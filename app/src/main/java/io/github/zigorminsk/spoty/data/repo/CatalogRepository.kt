package io.github.zigorminsk.spoty.data.repo

import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.AlbumDetails
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.ArtistDetails
import io.github.zigorminsk.spoty.data.model.Genre
import io.github.zigorminsk.spoty.data.model.SearchResults
import io.github.zigorminsk.spoty.data.model.SourceId
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.remote.MusicSource
import io.github.zigorminsk.spoty.data.remote.splitId
import io.github.zigorminsk.spoty.data.settings.SettingsStore
import io.github.zigorminsk.spoty.util.interleave
import io.github.zigorminsk.spoty.util.rankByQuery
import io.github.zigorminsk.spoty.util.trackDedupKey
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Небольшой кэш в памяти с временем жизни — чтобы переключение вкладок не дёргало сеть. */
class TtlCache(private val ttlMs: Long = 10 * 60_000L) {
    private val map = ConcurrentHashMap<String, Pair<Long, Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() - entry.first > ttlMs) {
            map.remove(key)
            return null
        }
        return entry.second as T
    }

    fun put(key: String, value: Any) {
        map[key] = System.currentTimeMillis() to value
    }

    fun clear() = map.clear()
}

private val OPEN_LICENSE = Regex("(?i)\\bcc\\b|creative ?commons|attribution|public ?domain|cc0|share ?alike")

/** Трек из «открытого» источника или с CC/PD-лицензией. */
fun Track.isOpenLicensed(): Boolean =
    source == SourceId.ARCHIVE.key || source == SourceId.JAMENDO.key ||
        (license != null && OPEN_LICENSE.containsMatchIn(license))

class CatalogRepository(
    private val sources: List<MusicSource>,
    private val settings: SettingsStore,
    val store: CatalogStore,
    private val appScope: CoroutineScope,
) {
    private val cache = TtlCache()

    fun activeSources(): List<MusicSource> {
        val enabled = settings.value.enabledSources
        return sources.filter { it.isAvailable && it.id.key in enabled }
    }

    private fun sourceFor(fullId: String): MusicSource? {
        val key = splitId(fullId).first
        return sources.firstOrNull { it.id.key == key && it.isAvailable }
    }

    fun clearCache() = cache.clear()

    private fun filterTracks(items: List<Track>): List<Track> =
        if (settings.value.openLicenseOnly) items.filter { it.isOpenLicensed() } else items

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun persist(tracks: List<Track> = emptyList(), albums: List<Album> = emptyList(), artists: List<Artist> = emptyList()) {
        appScope.launch {
            try {
                store.saveArtists(artists)
                store.saveAlbums(albums)
                store.saveTracks(tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Каталог — вспомогательный: ошибка записи не должна ломать поиск.
            }
        }
    }

    suspend fun search(query: String, limitPerSource: Int = 20): SearchResults = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope SearchResults()
        cache.get<SearchResults>("search:$q")?.let { return@coroutineScope it }
        val active = activeSources()
        val tracksJobs = active.map { s -> async { attempt { s.searchTracks(q, limitPerSource) } } }
        val albumsJobs = active.map { s -> async { attempt { s.searchAlbums(q, limitPerSource) } } }
        val artistsJobs = active.map { s -> async { attempt { s.searchArtists(q, limitPerSource) } } }
        val tracksRes = tracksJobs.map { it.await() }
        val albumsRes = albumsJobs.map { it.await() }
        val artistsRes = artistsJobs.map { it.await() }

        val failed = active.indices.filter { i ->
            tracksRes[i].isFailure && albumsRes[i].isFailure && artistsRes[i].isFailure
        }.map { active[it].id.title }

        val tracks = filterTracks(
            interleave(tracksRes.map { it.getOrDefault(emptyList()) })
                .distinctBy { trackDedupKey(it.artistName, it.title) },
        ).rankByQuery(q) { it.title }
        val albums = interleave(albumsRes.map { it.getOrDefault(emptyList()) })
            .distinctBy { it.id }.rankByQuery(q) { it.title }
        val artists = interleave(artistsRes.map { it.getOrDefault(emptyList()) })
            .distinctBy { it.id }.rankByQuery(q) { it.name }

        persist(tracks, albums, artists)
        val result = SearchResults(tracks, albums, artists, failed)
        if (failed.isEmpty()) cache.put("search:$q", result)
        result
    }

    suspend fun trendingTracks(genre: Genre? = null, limit: Int = 30): List<Track> {
        val key = "trending:${genre?.id}:$limit"
        cache.get<List<Track>>(key)?.let { return it }
        val result = coroutineScope {
            activeSources().map { s -> async { attempt { s.trendingTracks(genre, limit) } } }.map { it.await() }
        }
        val merged = filterTracks(interleave(result.map { it.getOrDefault(emptyList()) }).distinctBy { it.id })
        if (merged.isEmpty() && result.isNotEmpty() && result.all { it.isFailure }) {
            throw result.first().exceptionOrNull() ?: IOException("No sources")
        }
        persist(tracks = merged)
        if (merged.isNotEmpty()) cache.put(key, merged)
        return merged
    }

    suspend fun popularAlbums(genre: Genre? = null, limit: Int = 20): List<Album> {
        val key = "albums:${genre?.id}:$limit"
        cache.get<List<Album>>(key)?.let { return it }
        val result = coroutineScope {
            activeSources().map { s -> async { attempt { s.popularAlbums(genre, limit) } } }.map { it.await() }
        }
        val merged = interleave(result.map { it.getOrDefault(emptyList()) }).distinctBy { it.id }
        if (merged.isEmpty() && result.isNotEmpty() && result.all { it.isFailure }) {
            throw result.first().exceptionOrNull() ?: IOException("No sources")
        }
        persist(albums = merged)
        if (merged.isNotEmpty()) cache.put(key, merged)
        return merged
    }

    suspend fun popularArtists(limit: Int = 20): List<Artist> {
        val key = "artists:$limit"
        cache.get<List<Artist>>(key)?.let { return it }
        val result = coroutineScope {
            activeSources().map { s -> async { attempt { s.popularArtists(limit) } } }.map { it.await() }
        }
        val merged = interleave(result.map { it.getOrDefault(emptyList()) }).distinctBy { it.id }
        persist(artists = merged)
        if (merged.isNotEmpty()) cache.put(key, merged)
        return merged
    }

    suspend fun albumDetails(fullId: String): AlbumDetails {
        cache.get<AlbumDetails>("album:$fullId")?.let { return it }
        val src = sourceFor(fullId)
        val remote = if (src != null) attempt { src.album(splitId(fullId).second) } else null
        val details = remote?.getOrNull()
        if (details != null) {
            cache.put("album:$fullId", details)
            appScope.launch { runCatching { store.saveAlbumDetails(details) } }
            return details
        }
        store.localAlbum(fullId)?.let { return it }
        throw remote?.exceptionOrNull() ?: IOException("Album not found")
    }

    suspend fun artistDetails(fullId: String): ArtistDetails {
        cache.get<ArtistDetails>("artist:$fullId")?.let { return it }
        val src = sourceFor(fullId)
        val remote = if (src != null) attempt { src.artist(splitId(fullId).second) } else null
        val details = remote?.getOrNull()
        if (details != null) {
            cache.put("artist:$fullId", details)
            persist(tracks = details.topTracks, albums = details.albums, artists = listOf(details.artist) + details.related)
            return details
        }
        // Офлайн-режим: собираем страницу из локального каталога.
        val artist = store.artist(fullId)
        if (artist != null) {
            return ArtistDetails(
                artist = artist,
                topTracks = store.localArtistTracks(fullId, 30),
                albums = store.localArtistAlbums(fullId),
                related = emptyList(),
            )
        }
        throw remote?.exceptionOrNull() ?: IOException("Artist not found")
    }

    /** Треки для кнопки «Слушать» на странице исполнителя (если популярных нет — берём первый альбом). */
    suspend fun playableTracks(details: ArtistDetails): List<Track> {
        if (details.topTracks.isNotEmpty()) return details.topTracks
        val firstAlbum = details.albums.firstOrNull() ?: return emptyList()
        return albumDetails(firstAlbum.id).tracks
    }
}
