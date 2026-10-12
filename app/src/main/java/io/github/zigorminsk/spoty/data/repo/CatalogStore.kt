package io.github.zigorminsk.spoty.data.repo

import androidx.room.withTransaction
import io.github.zigorminsk.spoty.data.local.SpotyDatabase
import io.github.zigorminsk.spoty.data.local.toEntity
import io.github.zigorminsk.spoty.data.local.toModel
import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.AlbumDetails
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Локальный каталог: сюда попадает всё, что приложение нашло в источниках
 * (треки, альбомы, исполнители). Работает офлайн и сохраняет дату первого обнаружения.
 */
class CatalogStore(private val db: SpotyDatabase) {
    private val dao get() = db.catalog()

    suspend fun saveTracks(items: List<Track>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val unique = items.distinctBy { it.id }
        // Минимальные карточки исполнителей/альбомов — чтобы каталог наполнялся «сам».
        val artists = unique.filter { it.artistId != null }.distinctBy { it.artistId }.map { t ->
            Artist(id = t.artistId!!, source = t.source, name = t.artistName, imageUrl = t.artistImageUrl).toEntity(now)
        }
        val albums = unique.filter { it.albumId != null }.distinctBy { it.albumId }.map { t ->
            Album(
                id = t.albumId!!, source = t.source, title = t.albumTitle ?: t.title, artistId = t.artistId,
                artistName = t.artistName, artworkUrl = t.artworkUrl, genre = t.genre,
            ).toEntity(now)
        }
        db.withTransaction {
            artists.chunked(300).forEach { dao.insertArtistsIfAbsent(it) }
            albums.chunked(300).forEach { dao.insertAlbumsIfAbsent(it) }
            unique.chunked(300).forEach { chunk ->
                val known = dao.trackDiscovery(chunk.map { it.id }).associate { it.id to it.discoveredAt }
                dao.upsertTracks(chunk.map { t ->
                    val e = t.toEntity(now)
                    known[t.id]?.let { d -> e.copy(discoveredAt = d) } ?: e
                })
            }
        }
    }

    suspend fun saveAlbums(items: List<Album>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val unique = items.distinctBy { it.id }
        val artists = unique.filter { it.artistId != null }.distinctBy { it.artistId }.map { a ->
            Artist(id = a.artistId!!, source = a.source, name = a.artistName, imageUrl = null).toEntity(now)
        }
        db.withTransaction {
            artists.chunked(300).forEach { dao.insertArtistsIfAbsent(it) }
            unique.chunked(300).forEach { chunk ->
                val known = dao.albumDiscovery(chunk.map { it.id }).associate { it.id to it.discoveredAt }
                dao.upsertAlbums(chunk.map { a ->
                    val e = a.toEntity(now)
                    known[a.id]?.let { d -> e.copy(discoveredAt = d) } ?: e
                })
            }
        }
    }

    suspend fun saveArtists(items: List<Artist>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val unique = items.distinctBy { it.id }
        db.withTransaction {
            unique.chunked(300).forEach { chunk ->
                val known = dao.artistDiscovery(chunk.map { it.id }).associate { it.id to it.discoveredAt }
                dao.upsertArtists(chunk.map { a ->
                    val e = a.toEntity(now)
                    known[a.id]?.let { d -> e.copy(discoveredAt = d) } ?: e
                })
            }
        }
    }

    suspend fun saveAlbumDetails(details: AlbumDetails) {
        saveAlbums(listOf(details.album))
        saveTracks(details.tracks)
    }

    // ───────────── Чтение ─────────────

    suspend fun album(id: String): Album? = withContext(Dispatchers.IO) { dao.album(id)?.toModel() }

    suspend fun artist(id: String): Artist? = withContext(Dispatchers.IO) { dao.artist(id)?.toModel() }

    suspend fun track(id: String): Track? = withContext(Dispatchers.IO) { dao.track(id)?.toModel() }

    /** Треки по списку id в том же порядке. */
    suspend fun tracks(ids: List<String>): List<Track> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = ids.chunked(500).flatMap { dao.tracksByIds(it) }.associateBy { it.id }
        ids.mapNotNull { byId[it]?.toModel() }
    }

    suspend fun localAlbum(id: String): AlbumDetails? = withContext(Dispatchers.IO) {
        val album = dao.album(id)?.toModel() ?: return@withContext null
        val tracks = dao.tracksOfAlbum(id).map { it.toModel() }
        if (tracks.isEmpty()) null else AlbumDetails(album, tracks)
    }

    suspend fun localArtistAlbums(id: String): List<Album> = withContext(Dispatchers.IO) {
        dao.albumsOfArtist(id).map { it.toModel() }
    }

    suspend fun localArtistTracks(id: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        dao.tracksOfArtist(id, limit).map { it.toModel() }
    }

    suspend fun latestAlbums(limit: Int): List<Album> = withContext(Dispatchers.IO) {
        dao.latestAlbums(limit).map { it.toModel() }
    }

    suspend fun latestArtists(limit: Int): List<Artist> = withContext(Dispatchers.IO) {
        dao.latestArtists(limit).map { it.toModel() }
    }

    suspend fun randomTracks(limit: Int): List<Track> = withContext(Dispatchers.IO) {
        dao.randomTracks(limit).map { it.toModel() }
    }

    suspend fun tracksByGenre(genre: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        dao.tracksByGenre(genre, limit).map { it.toModel() }
    }
}
