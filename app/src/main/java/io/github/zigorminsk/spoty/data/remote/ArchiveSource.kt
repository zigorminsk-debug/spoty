package io.github.zigorminsk.spoty.data.remote

import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.AlbumDetails
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.ArtistDetails
import io.github.zigorminsk.spoty.data.model.Genre
import io.github.zigorminsk.spoty.data.model.SourceId
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.util.normalizeKey
import io.github.zigorminsk.spoty.util.parseYear
import io.github.zigorminsk.spoty.util.sanitizeQuery
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ───────────── DTO ─────────────

@Serializable
data class ArchiveSearchResponse(val response: ArchiveSearchBody? = null)

@Serializable
data class ArchiveSearchBody(val numFound: Int = 0, val docs: List<ArchiveDoc> = emptyList())

@Serializable
data class ArchiveDoc(
    val identifier: String,
    val title: JsonElement? = null,
    val creator: JsonElement? = null,
    val date: JsonElement? = null,
    val year: JsonElement? = null,
    val downloads: JsonElement? = null,
    val subject: JsonElement? = null,
)

@Serializable
data class ArchiveMetadataResponse(
    val metadata: ArchiveMeta? = null,
    val files: List<ArchiveFile> = emptyList(),
)

@Serializable
data class ArchiveMeta(
    val identifier: String? = null,
    val title: JsonElement? = null,
    val creator: JsonElement? = null,
    val date: JsonElement? = null,
    val year: JsonElement? = null,
    val description: JsonElement? = null,
    val licenseurl: String? = null,
    val subject: JsonElement? = null,
)

@Serializable
data class ArchiveFile(
    val name: String,
    val source: String? = null,
    val format: String? = null,
    val original: String? = null,
    val title: JsonElement? = null,
    val creator: JsonElement? = null,
    val artist: JsonElement? = null,
    val album: JsonElement? = null,
    val track: JsonElement? = null,
    val length: JsonElement? = null,
    val genre: JsonElement? = null,
)

// ───────────── Чистые функции (покрыты тестами) ─────────────

const val ARCHIVE_ARTIST_PREFIX = "archive:artist:"

fun archiveCover(identifier: String): String = "https://archive.org/services/img/$identifier"

fun archiveArtistId(creator: String): String = ARCHIVE_ARTIST_PREFIX + creator.trim()

/** Кодирует каждый сегмент пути отдельно: пробелы → %20, «/» сохраняется. */
fun archiveFileUrl(identifier: String, fileName: String): String {
    val encoded = fileName.split('/').joinToString("/") { urlEncode(it).replace("+", "%20") }
    return "https://archive.org/download/$identifier/$encoded"
}

/** Чем меньше — тем лучше качество/совместимость. -1 — не аудио. */
fun archiveFormatRank(format: String?): Int {
    val f = format?.lowercase()?.trim() ?: return -1
    return when {
        f == "vbr mp3" -> 0
        f == "mp3" -> 1
        f.contains("256kbps mp3") -> 2
        f.contains("192kbps mp3") || f.contains("160kbps mp3") -> 3
        f.contains("128kbps mp3") -> 4
        f.contains("mp3") && !f.contains("64kbps") && !f.contains("96kbps") -> 5
        f.contains("96kbps mp3") -> 6
        f.contains("64kbps mp3") -> 7
        f == "ogg vorbis" -> 20
        else -> -1
    }
}

/** Из списка файлов выбирает по одному лучшему аудиофайлу на каждую композицию. */
fun selectArchiveAudio(files: List<ArchiveFile>): List<ArchiveFile> {
    val audio = files.filter { archiveFormatRank(it.format) >= 0 }
    if (audio.isEmpty()) return emptyList()
    val groups = LinkedHashMap<String, MutableList<ArchiveFile>>()
    for (f in audio) groups.getOrPut(f.original ?: f.name) { mutableListOf() }.add(f)
    return groups.values.map { group -> group.minBy { archiveFormatRank(it.format) } }
}

fun parseArchiveLength(element: JsonElement?): Long {
    val s = element.firstString() ?: return 0L
    val seconds = if (s.contains(':')) {
        s.split(':').mapNotNull { it.trim().toDoubleOrNull() }.fold(0.0) { acc, v -> acc * 60 + v }
    } else {
        s.trim().toDoubleOrNull() ?: 0.0
    }
    return (seconds * 1000).toLong()
}

private val LEADING_NUMBER = Regex("^\\s*(\\d{1,3})[\\s._)\\-]+")

fun prettifyArchiveName(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringBeforeLast('.')
    return base.replace('_', ' ').replace(LEADING_NUMBER, "").trim().ifEmpty { base }
}

private fun archiveTrackNumber(file: ArchiveFile, fallback: Int): Int {
    file.track.firstString()?.let { raw ->
        raw.substringBefore('/').trim().toIntOrNull()?.let { return it }
    }
    LEADING_NUMBER.find(file.name.substringAfterLast('/'))?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    return fallback
}

private val HTML_TAG = Regex("<[^>]*>")

fun stripHtml(raw: String?): String? =
    raw?.replace(HTML_TAG, " ")?.replace("&nbsp;", " ")?.replace("&amp;", "&")?.replace(Regex("\\s+"), " ")?.trim()
        ?.takeIf { it.isNotEmpty() }

fun ArchiveDoc.toAlbum(): Album? {
    val albumTitle = title.firstString() ?: return null
    val by = creator.firstString()
    return Album(
        id = "archive:$identifier",
        source = SourceId.ARCHIVE.key,
        title = albumTitle,
        artistId = by?.let { archiveArtistId(it) },
        artistName = by ?: "—",
        artworkUrl = archiveCover(identifier),
        year = parseYear(date.firstString() ?: year.firstString()),
        genre = subject.firstString(),
        playCount = downloads.asLong() ?: 0L,
        webUrl = "https://archive.org/details/$identifier",
    )
}

fun archiveTracks(identifier: String, meta: ArchiveMeta?, files: List<ArchiveFile>): List<Track> {
    val albumTitle = meta?.title.firstString()
    val albumCreator = meta?.creator.firstString()
    val cover = archiveCover(identifier)
    val selected = selectArchiveAudio(files)
    return selected.mapIndexed { index, f ->
        Track(
            id = "archive:$identifier/${f.name}",
            source = SourceId.ARCHIVE.key,
            title = f.title.firstString() ?: prettifyArchiveName(f.name),
            artistId = albumCreator?.let { archiveArtistId(it) },
            artistName = f.artist.firstString() ?: f.creator.firstString() ?: albumCreator ?: "—",
            albumId = "archive:$identifier",
            albumTitle = f.album.firstString() ?: albumTitle,
            trackNumber = archiveTrackNumber(f, index + 1),
            durationMs = parseArchiveLength(f.length),
            artworkUrl = cover,
            streamUrl = archiveFileUrl(identifier, f.name),
            genre = f.genre.firstString() ?: meta?.subject.firstString(),
            license = meta?.licenseurl,
            webUrl = "https://archive.org/details/$identifier",
        )
    }.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
}

// ───────────── Источник ─────────────

class ArchiveSource(private val http: Http) : MusicSource {
    override val id: SourceId = SourceId.ARCHIVE

    private val musicFilter = "mediatype:audio AND collection:(netlabels OR freemusicarchive OR audio_music)"

    private fun searchUrl(q: String, rows: Int, sort: String?): String {
        val sb = StringBuilder("https://archive.org/advancedsearch.php?q=${urlEncode(q)}&rows=$rows&page=1&output=json")
        listOf("identifier", "title", "creator", "date", "year", "downloads", "subject").forEach {
            sb.append("&fl[]=").append(it)
        }
        if (sort != null) sb.append("&sort[]=").append(urlEncode(sort))
        return sb.toString()
    }

    private suspend fun search(q: String, rows: Int, sort: String?): List<ArchiveDoc> =
        http.getJson<ArchiveSearchResponse>(searchUrl(q, rows, sort)).response?.docs.orEmpty()

    private fun terms(query: String): String =
        sanitizeQuery(query).split(' ').filter { it.isNotBlank() }.joinToString(" AND ")

    override suspend fun searchTracks(query: String, limit: Int): List<Track> = emptyList()

    override suspend fun searchAlbums(query: String, limit: Int): List<Album> {
        val t = terms(query)
        if (t.isBlank()) return emptyList()
        return search("(title:($t) OR creator:($t)) AND $musicFilter", limit, null).mapNotNull { it.toAlbum() }
    }

    override suspend fun searchArtists(query: String, limit: Int): List<Artist> {
        val t = terms(query)
        if (t.isBlank()) return emptyList()
        val tokens = sanitizeQuery(query).lowercase().split(' ').filter { it.isNotBlank() }
        val docs = search("creator:($t) AND $musicFilter", 60, "downloads desc")
        return docs.mapNotNull { d -> d.creator.firstString()?.let { it to d } }
            .groupBy { normalizeKey(it.first) }
            .values
            .filter { group -> val n = normalizeKey(group.first().first); tokens.all { n.contains(it) } }
            .map { group ->
                val name = group.first().first
                Artist(
                    id = archiveArtistId(name),
                    source = SourceId.ARCHIVE.key,
                    name = name,
                    imageUrl = archiveCover(group.first().second.identifier),
                    albumCount = group.size,
                    followers = group.sumOf { it.second.downloads.asLong() ?: 0L },
                    webUrl = "https://archive.org/search?query=creator%3A%22${urlEncode(name)}%22",
                )
            }
            .take(limit)
    }

    override suspend fun trendingTracks(genre: Genre?, limit: Int): List<Track> = emptyList()

    override suspend fun popularAlbums(genre: Genre?, limit: Int): List<Album> {
        val subject = genre?.archive?.let { " AND subject:(${sanitizeQuery(it)})" }.orEmpty()
        return search("$musicFilter$subject", limit, "week desc").mapNotNull { it.toAlbum() }
    }

    override suspend fun album(rawId: String): AlbumDetails {
        val resp = http.getJson<ArchiveMetadataResponse>("https://archive.org/metadata/${urlEncode(rawId)}")
        val meta = resp.metadata ?: throw IOException("Item not found")
        val tracks = archiveTracks(rawId, meta, resp.files)
        if (tracks.isEmpty()) throw IOException("No playable audio files")
        val creator = meta.creator.firstString()
        val album = Album(
            id = "archive:$rawId",
            source = SourceId.ARCHIVE.key,
            title = meta.title.firstString() ?: rawId,
            artistId = creator?.let { archiveArtistId(it) },
            artistName = creator ?: "—",
            artworkUrl = archiveCover(rawId),
            year = parseYear(meta.date.firstString() ?: meta.year.firstString()),
            genre = meta.subject.firstString(),
            trackCount = tracks.size,
            durationMs = tracks.sumOf { it.durationMs },
            description = stripHtml(meta.description.firstString())?.take(600),
            license = meta.licenseurl,
            webUrl = "https://archive.org/details/$rawId",
        )
        return AlbumDetails(album, tracks)
    }

    override suspend fun artist(rawId: String): ArtistDetails {
        val name = rawId.removePrefix("artist:")
        val clean = sanitizeQuery(name)
        val albums = search("creator:(\"$clean\") AND mediatype:audio", 60, "downloads desc").mapNotNull { it.toAlbum() }
        if (albums.isEmpty()) throw IOException("Artist not found")
        val artist = Artist(
            id = archiveArtistId(name),
            source = SourceId.ARCHIVE.key,
            name = name,
            imageUrl = albums.first().artworkUrl,
            albumCount = albums.size,
            followers = albums.sumOf { it.playCount },
            webUrl = "https://archive.org/search?query=creator%3A%22${urlEncode(name)}%22",
        )
        return ArtistDetails(artist, emptyList(), albums, emptyList())
    }
}
