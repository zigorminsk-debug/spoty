package io.github.zigorminsk.spoty.data.repo

import androidx.room.withTransaction
import io.github.zigorminsk.spoty.data.local.DislikedTrackEntity
import io.github.zigorminsk.spoty.data.local.DownloadEntity
import io.github.zigorminsk.spoty.data.local.DownloadStatus
import io.github.zigorminsk.spoty.data.local.FollowedArtistEntity
import io.github.zigorminsk.spoty.data.local.LikedTrackEntity
import io.github.zigorminsk.spoty.data.local.PlayHistoryEntity
import io.github.zigorminsk.spoty.data.local.PlaylistEntity
import io.github.zigorminsk.spoty.data.local.PlaylistItemEntity
import io.github.zigorminsk.spoty.data.local.QueueItemEntity
import io.github.zigorminsk.spoty.data.local.SavedAlbumEntity
import io.github.zigorminsk.spoty.data.local.SearchHistoryEntity
import io.github.zigorminsk.spoty.data.local.SpotyDatabase
import io.github.zigorminsk.spoty.data.local.toModel
import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class PlaylistSummary(val id: Long, val name: String, val trackCount: Int, val covers: List<String>)

data class PlaylistItem(val itemId: Long, val track: Track)

data class PlaylistDetails(val id: Long, val name: String, val description: String?, val items: List<PlaylistItem>)

/** Всё, что принадлежит пользователю: лайки, подписки, плейлисты, история, загрузки. */
class LibraryRepository(
    private val db: SpotyDatabase,
    private val store: CatalogStore,
    appScope: CoroutineScope,
) {
    private val dao = db.library()

    val likedIds: StateFlow<Set<String>> =
        dao.observeLikedIds().map { it.toSet() }.stateIn(appScope, SharingStarted.Eagerly, emptySet())
    val dislikedIds: StateFlow<Set<String>> =
        dao.observeDislikedIds().map { it.toSet() }.stateIn(appScope, SharingStarted.Eagerly, emptySet())
    val followedIds: StateFlow<Set<String>> =
        dao.observeFollowedIds().map { it.toSet() }.stateIn(appScope, SharingStarted.Eagerly, emptySet())
    val savedAlbumIds: StateFlow<Set<String>> =
        dao.observeSavedAlbumIds().map { it.toSet() }.stateIn(appScope, SharingStarted.Eagerly, emptySet())
    val downloads: StateFlow<Map<String, DownloadEntity>> =
        dao.observeDownloads().map { list -> list.associateBy { it.trackId } }
            .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    /** trackId → путь к скачанному файлу (только завершённые загрузки). */
    val downloadedPaths: StateFlow<Map<String, String>> =
        downloads.map { m ->
            m.values.filter { it.status == DownloadStatus.DONE && it.filePath != null }
                .associate { it.trackId to it.filePath!! }
        }.stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    // ───────────── Лайки ─────────────

    fun likedTracks(): Flow<List<Track>> = dao.observeLikedTracks().map { list -> list.map { it.toModel() } }

    suspend fun likedTracksOnce(): List<Track> = withContext(Dispatchers.IO) { dao.likedTracks().map { it.toModel() } }

    suspend fun setLiked(track: Track, liked: Boolean) = withContext(Dispatchers.IO) {
        if (liked) {
            store.saveTracks(listOf(track))
            dao.like(LikedTrackEntity(track.id, System.currentTimeMillis()))
            dao.undislike(track.id)
        } else {
            dao.unlike(track.id)
        }
    }

    suspend fun toggleLike(track: Track) = setLiked(track, track.id !in likedIds.value)

    suspend fun dislike(track: Track) = withContext(Dispatchers.IO) {
        store.saveTracks(listOf(track))
        dao.dislike(DislikedTrackEntity(track.id, System.currentTimeMillis()))
        dao.unlike(track.id)
    }

    suspend fun undislike(trackId: String) = withContext(Dispatchers.IO) { dao.undislike(trackId) }

    suspend fun dislikedIdsOnce(): Set<String> = withContext(Dispatchers.IO) { dao.dislikedIds().toSet() }

    // ───────────── Подписки и альбомы ─────────────

    fun followedArtists(): Flow<List<Artist>> = dao.observeFollowedArtists().map { list -> list.map { it.toModel() } }

    suspend fun setFollowed(artist: Artist, follow: Boolean) = withContext(Dispatchers.IO) {
        if (follow) {
            store.saveArtists(listOf(artist))
            dao.follow(FollowedArtistEntity(artist.id, System.currentTimeMillis()))
        } else {
            dao.unfollow(artist.id)
        }
    }

    suspend fun toggleFollow(artist: Artist) = setFollowed(artist, artist.id !in followedIds.value)

    suspend fun followedArtistsOnce(): List<Artist> = withContext(Dispatchers.IO) { dao.followedArtists().map { it.toModel() } }

    fun savedAlbums(): Flow<List<Album>> = dao.observeSavedAlbums().map { list -> list.map { it.toModel() } }

    suspend fun savedAlbumsOnce(): List<Album> = withContext(Dispatchers.IO) { dao.savedAlbums().map { it.toModel() } }

    suspend fun setAlbumSaved(album: Album, saved: Boolean) = withContext(Dispatchers.IO) {
        if (saved) {
            store.saveAlbums(listOf(album))
            dao.saveAlbum(SavedAlbumEntity(album.id, System.currentTimeMillis()))
        } else {
            dao.unsaveAlbum(album.id)
        }
    }

    suspend fun toggleAlbumSaved(album: Album) = setAlbumSaved(album, album.id !in savedAlbumIds.value)

    // ───────────── Плейлисты ─────────────

    fun playlists(): Flow<List<PlaylistSummary>> = dao.observePlaylists().map { list ->
        list.map { p -> PlaylistSummary(p.playlist.id, p.playlist.name, p.trackCount, dao.playlistCovers(p.playlist.id, 4)) }
    }

    suspend fun playlistsOnce(): List<PlaylistSummary> = withContext(Dispatchers.IO) {
        dao.playlists().map { p -> PlaylistSummary(p.playlist.id, p.playlist.name, p.trackCount, dao.playlistCovers(p.playlist.id, 4)) }
    }

    fun playlist(id: Long): Flow<PlaylistDetails?> =
        combine(dao.observePlaylist(id), dao.observePlaylistTracks(id)) { meta, rows ->
            if (meta == null) null else PlaylistDetails(
                id = meta.playlist.id,
                name = meta.playlist.name,
                description = meta.playlist.description,
                items = rows.map { PlaylistItem(it.itemId, it.track.toModel()) },
            )
        }

    suspend fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = name.trim().ifEmpty { "Playlist" }, description = null, createdAt = now, updatedAt = now))
        if (tracks.isNotEmpty()) addToPlaylist(id, tracks)
        id
    }

    /** Добавляет треки в конец плейлиста (уже присутствующие пропускаются). Возвращает число добавленных. */
    suspend fun addToPlaylist(playlistId: Long, tracks: List<Track>): Int = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext 0
        store.saveTracks(tracks)
        db.withTransaction {
            val existing = dao.playlistTracks(playlistId).map { it.track.id }.toSet()
            val fresh = tracks.distinctBy { it.id }.filter { it.id !in existing }
            var position = dao.maxPosition(playlistId)
            val now = System.currentTimeMillis()
            dao.insertPlaylistItems(fresh.map { PlaylistItemEntity(playlistId = playlistId, trackId = it.id, position = ++position, addedAt = now) })
            dao.touchPlaylist(playlistId, now)
            fresh.size
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, itemId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylistItem(itemId)
        dao.touchPlaylist(playlistId, System.currentTimeMillis())
    }

    suspend fun moveInPlaylist(playlistId: Long, from: Int, to: Int) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val rows = dao.playlistTracks(playlistId).toMutableList()
            if (from !in rows.indices || to !in rows.indices || from == to) return@withTransaction
            val moved = rows.removeAt(from)
            rows.add(to, moved)
            rows.forEachIndexed { index, row -> dao.setItemPosition(row.itemId, index) }
            dao.touchPlaylist(playlistId, System.currentTimeMillis())
        }
    }

    suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) {
        val current = dao.playlist(id) ?: return@withContext
        dao.updatePlaylist(current.copy(name = name.trim().ifEmpty { current.name }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.deletePlaylistItems(id)
            dao.deletePlaylist(id)
        }
    }

    suspend fun playlistTracksOnce(id: Long): List<Track> = withContext(Dispatchers.IO) {
        dao.playlistTracks(id).map { it.track.toModel() }
    }

    // ───────────── История ─────────────

    fun recentTracks(limit: Int = 50): Flow<List<Track>> = dao.observeRecentTracks(limit).map { list -> list.map { it.toModel() } }

    suspend fun recentTracksOnce(limit: Int = 50): List<Track> = withContext(Dispatchers.IO) {
        dao.recentTracks(limit).map { it.toModel() }
    }

    suspend fun recentPlayedIds(limit: Int): List<String> = withContext(Dispatchers.IO) { dao.recentPlayedIds(limit) }

    suspend fun recordPlay(track: Track) = withContext(Dispatchers.IO) {
        store.saveTracks(listOf(track))
        dao.insertPlay(PlayHistoryEntity(trackId = track.id, playedAt = System.currentTimeMillis()))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { dao.clearHistory() }

    suspend fun topGenres(limit: Int): List<String> = withContext(Dispatchers.IO) {
        (dao.topGenresFromLikes(limit) + dao.topGenresFromHistory(limit))
            .groupBy { it.genre }.mapValues { (_, v) -> v.sumOf { it.cnt } }
            .entries.sortedByDescending { it.value }.map { it.key }.take(limit)
    }

    suspend fun topArtistIds(limit: Int): List<String> = withContext(Dispatchers.IO) {
        dao.topArtistsFromHistory(limit).map { it.id }
    }

    // ───────────── Загрузки (состояние; сами файлы качает DownloadWorker) ─────────────

    fun downloadedTracks(): Flow<List<Track>> = dao.observeDownloadedTracks().map { list -> list.map { it.toModel() } }

    suspend fun downloadedTracksOnce(): List<Track> = withContext(Dispatchers.IO) { dao.downloadedTracks().map { it.toModel() } }

    // ───────────── История поиска ─────────────

    fun searchHistory(limit: Int = 12): Flow<List<String>> = dao.observeSearchHistory(limit)

    suspend fun saveSearch(query: String) = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length >= 2) dao.saveSearch(SearchHistoryEntity(q, System.currentTimeMillis()))
    }

    suspend fun deleteSearch(query: String) = withContext(Dispatchers.IO) { dao.deleteSearch(query) }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) { dao.clearSearchHistory() }

    // ───────────── Очередь воспроизведения ─────────────

    suspend fun saveQueue(tracks: List<Track>) = withContext(Dispatchers.IO) {
        store.saveTracks(tracks)
        db.withTransaction {
            dao.clearQueue()
            dao.insertQueue(tracks.mapIndexed { index, t -> QueueItemEntity(index, t.id) })
        }
    }

    suspend fun loadQueue(): List<Track> = withContext(Dispatchers.IO) { dao.loadQueue().map { it.toModel() } }
}
