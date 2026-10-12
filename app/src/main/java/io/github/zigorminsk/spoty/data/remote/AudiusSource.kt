package io.github.zigorminsk.spoty.data.remote

import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.AlbumDetails
import io.github.zigorminsk.spoty.data.model.AlbumKind
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.ArtistDetails
import io.github.zigorminsk.spoty.data.model.Genre
import io.github.zigorminsk.spoty.data.model.SourceId
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.util.parseYear
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ───────────── DTO (только нужные поля; остальное игнорируется) ─────────────

@Serializable
data class AudiusImage(
    @SerialName("150x150") val s150: String? = null,
    @SerialName("480x480") val s480: String? = null,
    @SerialName("1000x1000") val s1000: String? = null,
)

@Serializable
data class AudiusCover(
    @SerialName("640x") val s640: String? = null,
    @SerialName("2000x") val s2000: String? = null,
)

@Serializable
data class AudiusUser(
    val id: String,
    val name: String? = null,
    val handle: String? = null,
    val bio: String? = null,
    val location: String? = null,
    @SerialName("follower_count") val followerCount: Long = 0,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("album_count") val albumCount: Int = 0,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_deactivated") val isDeactivated: Boolean = false,
    @SerialName("profile_picture") val profilePicture: AudiusImage? = null,
    @SerialName("cover_photo") val coverPhoto: AudiusCover? = null,
)

@Serializable
data class AudiusAccess(val stream: Boolean? = null, val download: Boolean? = null)

@Serializable
data class AudiusTrack(
    val id: String,
    val title: String? = null,
    val duration: Int = 0,
    val genre: String? = null,
    val artwork: AudiusImage? = null,
    val user: AudiusUser? = null,
    @SerialName("play_count") val playCount: Long = 0,
    val license: String? = null,
    val permalink: String? = null,
    @SerialName("is_streamable") val isStreamable: Boolean? = null,
    @SerialName("is_delete") val isDelete: Boolean = false,
    @SerialName("is_unlisted") val isUnlisted: Boolean = false,
    @SerialName("is_available") val isAvailable: Boolean? = null,
    val access: AudiusAccess? = null,
)

@Serializable
data class AudiusPlaylist(
    val id: String,
    @SerialName("playlist_name") val name: String? = null,
    @SerialName("is_album") val isAlbum: Boolean = false,
    val description: String? = null,
    val artwork: AudiusImage? = null,
    val user: AudiusUser? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("total_play_count") val totalPlayCount: Long = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("is_delete") val isDelete: Boolean = false,
    val permalink: String? = null,
)

// ───────────── Маппинг DTO → модель (чистые функции, покрыты тестами) ─────────────

const val AUDIUS_BASE = "https://api.audius.co/v1"
const val AUDIUS_APP = "app_name=Spoty"

private fun pickArt(image: AudiusImage?): String? = image?.s480 ?: image?.s1000 ?: image?.s150

fun audiusStreamUrl(rawId: String): String = "$AUDIUS_BASE/tracks/$rawId/stream?$AUDIUS_APP"

fun AudiusUser.toArtist(): Artist? {
    if (isDeactivated) return null
    val display = name?.trim().takeUnless { it.isNullOrEmpty() } ?: handle ?: return null
    return Artist(
        id = "audius:$id",
        source = SourceId.AUDIUS.key,
        name = display,
        imageUrl = pickArt(profilePicture),
        coverUrl = coverPhoto?.s2000 ?: coverPhoto?.s640,
        bio = bio?.trim()?.takeIf { it.isNotEmpty() },
        followers = followerCount,
        trackCount = trackCount,
        albumCount = albumCount,
        verified = isVerified,
        location = location?.trim()?.takeIf { it.isNotEmpty() },
        webUrl = handle?.let { "https://audius.co/$it" },
    )
}

fun AudiusTrack.toTrack(): Track? {
    if (isDelete || isUnlisted || isStreamable == false || isAvailable == false) return null
    if (access?.stream == false) return null
    if (user?.isDeactivated == true) return null
    val artist = user
    val artistName = artist?.name?.trim().takeUnless { it.isNullOrEmpty() } ?: artist?.handle ?: "—"
    return Track(
        id = "audius:$id",
        source = SourceId.AUDIUS.key,
        title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: "—",
        artistId = artist?.id?.let { "audius:$it" },
        artistName = artistName,
        albumId = null,
        albumTitle = null,
        durationMs = duration * 1000L,
        artworkUrl = pickArt(artwork) ?: pickArt(artist?.profilePicture),
        streamUrl = audiusStreamUrl(id),
        genre = genre?.takeIf { it.isNotBlank() },
        license = license?.takeIf { it.isNotBlank() },
        playCount = playCount,
        webUrl = permalink?.let { "https://audius.co$it" },
        artistImageUrl = pickArt(artist?.profilePicture),
    )
}

fun AudiusPlaylist.toAlbum(): Album? {
    if (isDelete || isPrivate) return null
    val owner = user
    val title = name?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    return Album(
        id = "audius:$id",
        source = SourceId.AUDIUS.key,
        title = title,
        artistId = owner?.id?.let { "audius:$it" },
        artistName = owner?.name?.trim().takeUnless { it.isNullOrEmpty() } ?: owner?.handle ?: "—",
        artworkUrl = pickArt(artwork) ?: pickArt(owner?.profilePicture),
        year = parseYear(releaseDate ?: createdAt),
        trackCount = trackCount,
        kind = if (isAlbum) AlbumKind.ALBUM else AlbumKind.PLAYLIST,
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        playCount = totalPlayCount,
        webUrl = permalink?.let { "https://audius.co$it" },
    )
}

// ───────────── Источник ─────────────

class AudiusSource(private val http: Http) : MusicSource {
    override val id: SourceId = SourceId.AUDIUS

    private suspend fun data(path: String): JsonElement? {
        val sep = if (path.contains('?')) '&' else '?'
        val root = http.getElement("$AUDIUS_BASE$path$sep$AUDIUS_APP")
        return (root as? JsonObject)?.get("data")
    }

    private suspend inline fun <reified T> list(path: String): List<T> = data(path).decodeList(http.json)

    override suspend fun searchTracks(query: String, limit: Int): List<Track> =
        list<AudiusTrack>("/tracks/search?query=${urlEncode(query)}&limit=$limit").mapNotNull { it.toTrack() }

    override suspend fun searchAlbums(query: String, limit: Int): List<Album> =
        list<AudiusPlaylist>("/playlists/search?query=${urlEncode(query)}&limit=$limit")
            .mapNotNull { it.toAlbum() }
            .filter { it.trackCount > 0 }
            .sortedByDescending { it.kind == AlbumKind.ALBUM }

    override suspend fun searchArtists(query: String, limit: Int): List<Artist> =
        list<AudiusUser>("/users/search?query=${urlEncode(query)}&limit=$limit")
            .mapNotNull { it.toArtist() }
            .filter { it.trackCount > 0 }

    override suspend fun trendingTracks(genre: Genre?, limit: Int): List<Track> {
        val g = genre?.audius?.let { "&genre=${urlEncode(it)}" }.orEmpty()
        return list<AudiusTrack>("/tracks/trending?time=week&limit=$limit$g").mapNotNull { it.toTrack() }
    }

    override suspend fun popularAlbums(genre: Genre?, limit: Int): List<Album> {
        // Эндпоинт top?type=album не фильтруется по жанру: при выбранном жанре ищем альбомы по названию жанра.
        return if (genre?.audius != null) {
            safely(emptyList()) { searchAlbums(genre.audius, limit * 2) }.filter { it.kind == AlbumKind.ALBUM }.take(limit)
        } else {
            list<AudiusPlaylist>("/playlists/top?type=album&limit=$limit")
                .mapNotNull { it.toAlbum() }
                .filter { it.trackCount > 0 }
        }
    }

    override suspend fun album(rawId: String): AlbumDetails = coroutineScope {
        val meta = async { list<AudiusPlaylist>("/playlists/$rawId") }
        val tracksReq = async { list<AudiusTrack>("/playlists/$rawId/tracks") }
        val album = meta.await().firstOrNull()?.toAlbum() ?: throw IOException("Album not found")
        val tracks = tracksReq.await().mapNotNull { it.toTrack() }.mapIndexed { index, t ->
            t.copy(
                albumId = album.id,
                albumTitle = album.title,
                trackNumber = index + 1,
                artworkUrl = album.artworkUrl ?: t.artworkUrl,
            )
        }
        AlbumDetails(
            album = album.copy(trackCount = tracks.size, durationMs = tracks.sumOf { it.durationMs }),
            tracks = tracks,
        )
    }

    override suspend fun artist(rawId: String): ArtistDetails = coroutineScope {
        val userReq = async { list<AudiusUser>("/users/$rawId").firstOrNull() }
        val tracksReq = async { safely(emptyList()) { list<AudiusTrack>("/users/$rawId/tracks?sort=plays&limit=30") } }
        val albumsReq = async { safely(emptyList()) { list<AudiusPlaylist>("/users/$rawId/albums?limit=40") } }
        val relatedReq = async { safely(emptyList()) { list<AudiusUser>("/users/$rawId/related?limit=12") } }
        val artist = userReq.await()?.toArtist() ?: throw IOException("Artist not found")
        ArtistDetails(
            artist = artist,
            topTracks = tracksReq.await().mapNotNull { it.toTrack() }.sortedByDescending { it.playCount },
            albums = albumsReq.await().mapNotNull { it.toAlbum() }.filter { it.trackCount > 0 },
            related = relatedReq.await().mapNotNull { it.toArtist() },
        )
    }

    override suspend fun popularArtists(limit: Int): List<Artist> =
        list<AudiusTrack>("/tracks/trending?time=month&limit=60")
            .mapNotNull { it.user?.toArtist() }
            .distinctBy { it.id }
            .sortedByDescending { it.followers }
            .take(limit)
}
