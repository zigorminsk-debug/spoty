package io.github.zigorminsk.spoty.data.repo

import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.remote.SpotyJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class BackupPlaylist(val name: String, val tracks: List<Track>)

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val likes: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
)

/** Экспорт/импорт медиатеки в JSON — на случай переезда на другой телефон или смены ключа подписи. */
class BackupRepository(
    private val library: LibraryRepository,
    private val store: CatalogStore,
) {
    suspend fun export(): String {
        val playlists = library.playlists().first().map { p ->
            BackupPlaylist(p.name, library.playlistTracksOnce(p.id))
        }
        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            likes = library.likedTracksOnce(),
            artists = library.followedArtistsOnce(),
            albums = library.savedAlbumsOnce(),
            playlists = playlists,
        )
        return SpotyJson.encodeToString(data)
    }

    /** Возвращает число восстановленных объектов. */
    suspend fun import(json: String): Int {
        val data = SpotyJson.decodeFromString<BackupData>(json)
        var count = 0
        data.likes.reversed().forEach { library.setLiked(it, true); count++ }
        data.artists.reversed().forEach { library.setFollowed(it, true); count++ }
        data.albums.reversed().forEach { library.setAlbumSaved(it, true); count++ }
        val existing = library.playlistsOnce().associateBy { it.name }
        for (p in data.playlists) {
            store.saveTracks(p.tracks)
            val id = existing[p.name]?.id ?: library.createPlaylist(p.name)
            library.addToPlaylist(id, p.tracks)
            count++
        }
        return count
    }
}
