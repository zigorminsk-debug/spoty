package io.github.zigorminsk.spoty.data.model

import kotlinx.serialization.Serializable

/** Источник музыки. [key] входит в идентификаторы: «audius:abc». */
enum class SourceId(val key: String, val title: String) {
    AUDIUS("audius", "Audius"),
    ARCHIVE("archive", "Internet Archive"),
    JAMENDO("jamendo", "Jamendo");

    companion object {
        fun fromKey(key: String?): SourceId? = entries.firstOrNull { it.key == key }
        fun title(key: String?): String = fromKey(key)?.title ?: when (key) {
            "rutracker" -> "RuTracker"
            else -> key ?: ""
        }
    }
}

@Serializable
enum class AlbumKind { ALBUM, PLAYLIST }

@Serializable
data class Track(
    val id: String,
    val source: String,
    val title: String,
    val artistId: String?,
    val artistName: String,
    val albumId: String?,
    val albumTitle: String?,
    val trackNumber: Int = 0,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val streamUrl: String,
    val genre: String? = null,
    val license: String? = null,
    val playCount: Long = 0L,
    val webUrl: String? = null,
    /** Аватар исполнителя (если источник отдал его вместе с треком); в БД треков не хранится. */
    val artistImageUrl: String? = null,
)

@Serializable
data class Album(
    val id: String,
    val source: String,
    val title: String,
    val artistId: String?,
    val artistName: String,
    val artworkUrl: String?,
    val year: Int? = null,
    val genre: String? = null,
    val trackCount: Int = 0,
    val durationMs: Long = 0L,
    val kind: AlbumKind = AlbumKind.ALBUM,
    val description: String? = null,
    val playCount: Long = 0L,
    val license: String? = null,
    val webUrl: String? = null,
)

@Serializable
data class Artist(
    val id: String,
    val source: String,
    val name: String,
    val imageUrl: String?,
    val coverUrl: String? = null,
    val bio: String? = null,
    val followers: Long = 0L,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val verified: Boolean = false,
    val location: String? = null,
    val genre: String? = null,
    val webUrl: String? = null,
)

data class AlbumDetails(val album: Album, val tracks: List<Track>)

data class ArtistDetails(
    val artist: Artist,
    val topTracks: List<Track>,
    val albums: List<Album>,
    val related: List<Artist>,
)

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    /** Источники, которые не ответили (для информирования пользователя). */
    val failedSources: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/** Лёгкая «карточка» для рядов на главной: трек, альбом или исполнитель. */
sealed interface Shelf {
    data class Tracks(val items: List<Track>) : Shelf
    data class Albums(val items: List<Album>) : Shelf
    data class Artists(val items: List<Artist>) : Shelf
}
