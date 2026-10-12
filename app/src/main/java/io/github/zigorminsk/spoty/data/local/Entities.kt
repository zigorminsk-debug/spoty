package io.github.zigorminsk.spoty.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ───────────── Каталог: всё, что приложение когда-либо нашло в источниках ─────────────

@Entity(tableName = "artists", indices = [Index("source"), Index("searchKey")])
data class ArtistEntity(
    @PrimaryKey val id: String,
    val source: String,
    val name: String,
    val searchKey: String,
    val imageUrl: String?,
    val coverUrl: String?,
    val bio: String?,
    val genre: String?,
    val followers: Long,
    val trackCount: Int,
    val albumCount: Int,
    val verified: Boolean,
    val location: String?,
    val webUrl: String?,
    val discoveredAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "albums", indices = [Index("artistId"), Index("source"), Index("searchKey")])
data class AlbumEntity(
    @PrimaryKey val id: String,
    val source: String,
    val title: String,
    val searchKey: String,
    val artistId: String?,
    val artistName: String,
    val artworkUrl: String?,
    val year: Int?,
    val genre: String?,
    val trackCount: Int,
    val durationMs: Long,
    val kind: String,
    val description: String?,
    val playCount: Long,
    val license: String?,
    val webUrl: String?,
    val discoveredAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "tracks", indices = [Index("albumId"), Index("artistId"), Index("source"), Index("searchKey")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val source: String,
    val title: String,
    val searchKey: String,
    val artistId: String?,
    val artistName: String,
    val albumId: String?,
    val albumTitle: String?,
    val trackNumber: Int,
    val durationMs: Long,
    val artworkUrl: String?,
    val streamUrl: String,
    val genre: String?,
    val license: String?,
    val playCount: Long,
    val webUrl: String?,
    val discoveredAt: Long,
    val updatedAt: Long,
)

// ───────────── Данные пользователя ─────────────

@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(@PrimaryKey val trackId: String, val likedAt: Long)

@Entity(tableName = "disliked_tracks")
data class DislikedTrackEntity(@PrimaryKey val trackId: String, val dislikedAt: Long)

@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(@PrimaryKey val artistId: String, val followedAt: Long)

@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(@PrimaryKey val albumId: String, val savedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "playlist_items", indices = [Index("playlistId"), Index("trackId")])
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "play_history", indices = [Index("trackId"), Index("playedAt")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAt: Long,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val filePath: String?,
    val sizeBytes: Long,
    /** См. [DownloadStatus]. */
    val status: Int,
    val progress: Int,
    val createdAt: Long,
)

object DownloadStatus {
    const val QUEUED = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(@PrimaryKey val query: String, val searchedAt: Long)

@Entity(tableName = "queue_items")
data class QueueItemEntity(@PrimaryKey val position: Int, val trackId: String)

// ───────────── Проекции запросов ─────────────

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val trackCount: Int,
)

data class PlaylistTrackRow(
    @Embedded val track: TrackEntity,
    val itemId: Long,
    val position: Int,
)

data class GenreCount(val genre: String, val cnt: Int)

data class IdCount(val id: String, val cnt: Int)

data class IdTime(val id: String, val discoveredAt: Long)
