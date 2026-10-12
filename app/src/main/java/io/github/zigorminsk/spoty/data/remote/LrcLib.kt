package io.github.zigorminsk.spoty.data.remote

import io.github.zigorminsk.spoty.data.model.Track
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class LrcLine(val timeMs: Long, val text: String)

/** Текст песни: [lines] — с таймкодами (если есть), [plain] — просто текст. */
data class Lyrics(val lines: List<LrcLine>, val plain: String?, val instrumental: Boolean) {
    val isSynced: Boolean get() = lines.isNotEmpty()
    val isEmpty: Boolean get() = !instrumental && lines.isEmpty() && plain.isNullOrBlank()
}

@Serializable
data class LrcLibItem(
    val id: Long = 0,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

private val LRC_TS = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

/** Разбор формата LRC: «[01:23.45] строка» (допускается несколько меток в строке). */
fun parseLrc(text: String): List<LrcLine> {
    val out = ArrayList<LrcLine>()
    for (raw in text.lineSequence()) {
        val matches = LRC_TS.findAll(raw).toList()
        if (matches.isEmpty()) continue
        val content = raw.substring(matches.last().range.last + 1).trim()
        for (m in matches) {
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val millis = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            out += LrcLine((minutes * 60 + seconds) * 1000 + millis, content)
        }
    }
    return out.sortedBy { it.timeMs }
}

fun LrcLibItem.toLyrics(): Lyrics = Lyrics(
    lines = syncedLyrics?.let { parseLrc(it) }.orEmpty(),
    plain = plainLyrics?.takeIf { it.isNotBlank() },
    instrumental = instrumental,
)

/** Клиент LRCLIB (https://lrclib.net) — открытая база текстов, в том числе синхронизированных. */
class LrcLibClient(private val http: Http) {
    private val headers = mapOf("User-Agent" to "Spoty/1.0 (https://github.com/zigorminsk-debug/spoty)")

    suspend fun lyrics(track: Track): Lyrics? {
        val exact = safely<LrcLibItem?>(null) {
            val sb = StringBuilder("https://lrclib.net/api/get?artist_name=${urlEncode(track.artistName)}&track_name=${urlEncode(track.title)}")
            track.albumTitle?.takeIf { it.isNotBlank() }?.let { sb.append("&album_name=").append(urlEncode(it)) }
            if (track.durationMs > 0) sb.append("&duration=").append(track.durationMs / 1000)
            http.getJson<LrcLibItem>(sb.toString(), headers)
        }
        exact?.toLyrics()?.takeUnless { it.isEmpty }?.let { return it }

        val results = safely(emptyList<LrcLibItem>()) {
            val url = "https://lrclib.net/api/search?track_name=${urlEncode(track.title)}&artist_name=${urlEncode(track.artistName)}"
            http.getElement(url, headers).let { el ->
                when (el) {
                    is JsonArray -> el.decodeList<LrcLibItem>(http.json)
                    is JsonObject -> el.decodeList<LrcLibItem>(http.json)
                    else -> emptyList()
                }
            }
        }
        val wanted = track.durationMs / 1000.0
        val best = results
            .filter { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() || it.instrumental }
            .minWithOrNull(
                compareBy<LrcLibItem> { if (it.syncedLyrics.isNullOrBlank()) 1 else 0 }
                    .thenBy { if (wanted > 0 && it.duration != null) abs(it.duration - wanted) else 0.0 },
            )
        return best?.toLyrics()?.takeUnless { it.isEmpty }
    }
}
