package io.github.zigorminsk.spoty.data.remote

import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.AlbumDetails
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
import kotlinx.serialization.json.JsonArray

@Serializable
data class JamendoTrack(
    val id: String,
    val name: String? = null,
    val duration: Int = 0,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("album_image") val albumImage: String? = null,
    val image: String? = null,
    val audio: String? = null,
    @SerialName("license_ccurl") val licenseUrl: String? = null,
    val position: Int = 0,
    val releasedate: String? = null,
    val shareurl: String? = null,
    val musicinfo: JsonElement? = null,
)

@Serializable
data class JamendoAlbum(
    val id: String,
    val name: String? = null,
    val releasedate: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    val image: String? = null,
    val shareurl: String? = null,
)

@Serializable
data class JamendoArtist(
    val id: String,
    val name: String? = null,
    val image: String? = null,
    val website: String? = null,
    val shareurl: String? = null,
)

/** Первый жанр из musicinfo.tags.genres. */
private fun JsonElement?.firstGenre(): String? {
    val tags = (this as? JsonObject)?.get("tags") as? JsonObject ?: return null
    val genres = tags["genres"] as? JsonArray ?: return null
    return genres.firstString()
}

fun JamendoTrack.toTrack(): Track? {
    val url = audio?.takeIf { it.isNotBlank() } ?: return null
    return Track(
        id = "jamendo:$id",
        source = SourceId.JAMENDO.key,
        title = name?.trim().takeUnless { it.isNullOrEmpty() } ?: "—",
        artistId = artistId?.let { "jamendo:$it" },
        artistName = artistName ?: "—",
        albumId = albumId?.takeIf { it.isNotBlank() }?.let { "jamendo:$it" },
        albumTitle = albumName?.takeIf { it.isNotBlank() },
        trackNumber = position,
        durationMs = duration * 1000L,
        artworkUrl = (albumImage?.takeIf { it.isNotBlank() } ?: image)?.takeIf { it.isNotBlank() },
        streamUrl = url,
        genre = musicinfo.firstGenre(),
        license = licenseUrl?.takeIf { it.isNotBlank() },
        webUrl = shareurl,
    )
}

fun JamendoAlbum.toAlbum(): Album = Album(
    id = "jamendo:$id",
    source = SourceId.JAMENDO.key,
    title = name?.trim().takeUnless { it.isNullOrEmpty() } ?: "—",
    artistId = artistId?.let { "jamendo:$it" },
    artistName = artistName ?: "—",
    artworkUrl = image?.takeIf { it.isNotBlank() },
    year = parseYear(releasedate),
    webUrl = shareurl,
)

fun JamendoArtist.toArtist(): Artist = Artist(
    id = "jamendo:$id",
    source = SourceId.JAMENDO.key,
    name = name?.trim().takeUnless { it.isNullOrEmpty() } ?: "—",
    imageUrl = image?.takeIf { it.isNotBlank() },
    webUrl = shareurl ?: website,
)

/**
 * Jamendo (треки под лицензиями Creative Commons). Общий демо-ключ приостановлен,
 * поэтому нужен собственный client_id (бесплатно: devportal.jamendo.com) — вводится в настройках.
 */
class JamendoSource(
    private val http: Http,
    private val clientId: () -> String,
) : MusicSource {
    override val id: SourceId = SourceId.JAMENDO
    override val isAvailable: Boolean get() = clientId().isNotBlank()

    private suspend fun results(path: String): JsonElement? {
        val sep = if (path.contains('?')) '&' else '?'
        val url = "https://api.jamendo.com/v3.0$path${sep}client_id=${urlEncode(clientId())}&format=json"
        val root = http.getElement(url) as? JsonObject ?: return null
        val headers = root["headers"] as? JsonObject
        val code = headers?.get("code").asLong() ?: 0L
        if (code != 0L) throw IOException(headers?.get("error_message").firstString() ?: "Jamendo error $code")
        return root["results"]
    }

    private suspend fun tracks(query: String): List<Track> =
        results("/tracks/?$query&imagesize=400&audioformat=mp32&type=single+albumtrack")
            .decodeList<JamendoTrack>(http.json).mapNotNull { it.toTrack() }

    override suspend fun searchTracks(query: String, limit: Int): List<Track> =
        tracks("search=${urlEncode(query)}&limit=$limit&boost=popularity_month")

    override suspend fun searchAlbums(query: String, limit: Int): List<Album> =
        results("/albums/?namesearch=${urlEncode(query)}&limit=$limit&imagesize=400&order=popularity_total")
            .decodeList<JamendoAlbum>(http.json).map { it.toAlbum() }

    override suspend fun searchArtists(query: String, limit: Int): List<Artist> =
        results("/artists/?namesearch=${urlEncode(query)}&limit=$limit&imagesize=400&order=popularity_total")
            .decodeList<JamendoArtist>(http.json).map { it.toArtist() }

    override suspend fun trendingTracks(genre: Genre?, limit: Int): List<Track> {
        val tag = genre?.jamendo?.let { "&fuzzytags=${urlEncode(it)}" }.orEmpty()
        return tracks("order=popularity_week&limit=$limit&groupby=artist_id$tag")
    }

    override suspend fun popularAlbums(genre: Genre?, limit: Int): List<Album> {
        // У альбомов нет фильтра по жанру: берём альбомы жанровых треков.
        return if (genre?.jamendo != null) {
            val ts = tracks("order=popularity_month&limit=${limit * 2}&groupby=album_id&fuzzytags=${urlEncode(genre.jamendo)}")
            ts.filter { it.albumId != null }.distinctBy { it.albumId }.map {
                Album(
                    id = it.albumId!!, source = SourceId.JAMENDO.key, title = it.albumTitle ?: it.title,
                    artistId = it.artistId, artistName = it.artistName, artworkUrl = it.artworkUrl,
                    year = null, genre = it.genre, webUrl = it.webUrl,
                )
            }.take(limit)
        } else {
            results("/albums/?order=popularity_week&limit=$limit&imagesize=400")
                .decodeList<JamendoAlbum>(http.json).map { it.toAlbum() }
        }
    }

    override suspend fun popularArtists(limit: Int): List<Artist> =
        results("/artists/?order=popularity_week&limit=$limit&imagesize=400")
            .decodeList<JamendoArtist>(http.json).map { it.toArtist() }

    override suspend fun album(rawId: String): AlbumDetails = coroutineScope {
        val metaReq = async { results("/albums/?id=${urlEncode(rawId)}&imagesize=600").decodeList<JamendoAlbum>(http.json).firstOrNull() }
        val tracksReq = async { tracks("album_id=${urlEncode(rawId)}&limit=200&order=releasedate") }
        val meta = metaReq.await() ?: throw IOException("Album not found")
        val ts = tracksReq.await().sortedBy { it.trackNumber }
        val album = meta.toAlbum().copy(trackCount = ts.size, durationMs = ts.sumOf { it.durationMs })
        AlbumDetails(album, ts.map { it.copy(albumId = album.id, albumTitle = album.title, artworkUrl = album.artworkUrl ?: it.artworkUrl) })
    }

    override suspend fun artist(rawId: String): ArtistDetails = coroutineScope {
        val metaReq = async { results("/artists/?id=${urlEncode(rawId)}&imagesize=600").decodeList<JamendoArtist>(http.json).firstOrNull() }
        val tracksReq = async { safely(emptyList()) { tracks("artist_id=${urlEncode(rawId)}&limit=30&order=popularity_total") } }
        val albumsReq = async {
            safely(emptyList()) {
                results("/albums/?artist_id=${urlEncode(rawId)}&limit=50&imagesize=400&order=releasedate_desc")
                    .decodeList<JamendoAlbum>(http.json).map { it.toAlbum() }
            }
        }
        val meta = metaReq.await() ?: throw IOException("Artist not found")
        val albums = albumsReq.await()
        ArtistDetails(
            artist = meta.toArtist().copy(albumCount = albums.size),
            topTracks = tracksReq.await(),
            albums = albums,
            related = emptyList(),
        )
    }
}
