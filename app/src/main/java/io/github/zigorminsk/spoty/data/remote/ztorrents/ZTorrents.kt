package io.github.zigorminsk.spoty.data.remote.ztorrents

import io.github.zigorminsk.spoty.data.remote.rutracker.RuTrackerParser
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

/** Ключ источника в идентификаторах треков: «ztorrents:<релиз>:<file>». */
const val ZTORRENTS_SOURCE = "ztorrents"

/**
 * Адрес сайта: музыкальный трекер на DLE. Торренты открыты для гостей —
 * ни вход, ни cookie, ни капча не нужны, поэтому настроек у источника нет.
 */
const val ZTORRENTS_BASE = "https://z-torrents.ru"

/** Релиз из результатов поиска. */
data class ZRelease(
    val id: Long,
    val title: String,
    val category: String,
    val year: String?,
    val sizeText: String?,
    val url: String,
)

/** Страница релиза: прямая ссылка на .torrent и сведения о выпуске. */
data class ZReleaseDetails(
    val id: Long,
    val title: String,
    val torrentUrl: String?,
    val coverUrl: String?,
    val infoText: String,
)

/** Разбор HTML z-torrents.ru. Вынесен отдельно, чтобы покрыть тестами без Android. */
object ZTorrentsParser {

    /** Ссылка на релиз: «/<жанр>/<id>-<slug>.html». */
    private val articleRegex = Regex("""/[a-z0-9_]+/(\d+)-[a-z0-9-]+\.html""")

    fun releaseId(url: String): Long? =
        articleRegex.find(url)?.groupValues?.get(1)?.toLongOrNull()

    /** Страница результатов «index.php?do=search&subaction=search&story=…». */
    fun parseSearch(html: String): List<ZRelease> {
        val doc = Jsoup.parse(html)
        val seen = mutableSetOf<Long>()
        val out = mutableListOf<ZRelease>()
        for (a in doc.select("a[href]")) {
            val href = a.attr("href")
            val id = releaseId(href) ?: continue
            val title = a.text().trim()
            // У результата две ссылки на релиз: заголовок и «Читать дальше» — берём заголовок.
            if (title.isEmpty() || title.startsWith("Читать дальше")) continue
            if (!seen.add(id)) continue
            val block = a.parents().firstOrNull { it.text().contains("Информация об альбоме") }
            val text = block?.text().orEmpty()
            out += ZRelease(
                id = id,
                title = title,
                category = categoryOf(block),
                year = Regex("""Year:\s*(\d{4})""").find(text)?.groupValues?.get(1),
                sizeText = Regex("""File size:\s*([0-9.,]+\s*[A-Z]B)""").find(text)?.groupValues?.get(1)?.trim(),
                url = href,
            )
        }
        return out
    }

    /** Страница релиза: .torrent лежит открытой ссылкой в блоке «Скачать торрент». */
    fun parseRelease(html: String, id: Long): ZReleaseDetails {
        val doc = Jsoup.parse(html)
        val body = doc.text()
        return ZReleaseDetails(
            id = id,
            title = (doc.selectFirst("h1")?.text()?.trim() ?: doc.title())
                .removeSuffix(" скачать через торрент")
                .trim(),
            torrentUrl = doc.selectFirst("a[href$=.torrent]")?.attr("href")?.takeIf { it.isNotBlank() },
            coverUrl = doc.selectFirst("img[src*=/uploads/posts/]")?.attr("src")?.takeIf { it.isNotBlank() },
            infoText = body
                .substringAfter("Информация об альбоме", "")
                .substringBefore("Скачать торрент")
                .trim(),
        )
    }

    /** Название файла → название трека; логика общая с RuTracker. */
    fun cleanFileTitle(name: String): String = RuTrackerParser.cleanFileTitle(name)

    /** «Исполнитель - Альбом (год) …» → пара (исполнитель, альбом); логика общая с RuTracker. */
    fun splitTitle(raw: String): Pair<String?, String?> = RuTrackerParser.splitTitle(raw)

    /** Ссылка жанра вида «https://z-torrents.ru/club/» внутри блока результата. */
    private fun categoryOf(block: org.jsoup.nodes.Element?): String {
        if (block == null) return ""
        val link = block.select("a[href]").firstOrNull { a ->
            val path = a.attr("href").removeSuffix("/").substringAfter("://", a.attr("href")).substringAfter("/")
            path.isNotEmpty() && !path.contains("/") && path != "tags" && path != "user"
        } ?: return ""
        return link.text().trim()
    }
}

/**
 * Клиент z-torrents.ru: поиск по сайту, страница релиза и скачивание .torrent.
 * Всё доступно гостю, поэтому клиент Stateless — без входа и cookie.
 */
class ZTorrentsClient(private val baseClient: OkHttpClient) {

    /** Поиск по сайту (гостевой, без входа). titleonly=3 — искать в заголовках, а не в треклистах. */
    suspend fun search(query: String): List<ZRelease> =
        ZTorrentsParser.parseSearch(
            body(
                "$ZTORRENTS_BASE/index.php?do=search&subaction=search&titleonly=3&story=" +
                    URLEncoder.encode(query, "UTF-8"),
            ),
        )

    /** Страница релиза по числовому идентификатору (DLE отдаёт её и без ЧПУ-адреса). */
    suspend fun details(releaseId: Long): ZReleaseDetails =
        ZTorrentsParser.parseRelease(body("$ZTORRENTS_BASE/index.php?newsid=$releaseId"), releaseId)

    /** .torrent релиза: сперва читаем страницу, затем сам файл прямой ссылкой. */
    suspend fun torrentBytes(releaseId: Long): ByteArray {
        val torrentUrl = details(releaseId).torrentUrl
            ?: throw IOException("z-torrents: release $releaseId has no torrent file")
        return bytes(torrentUrl)
    }

    private suspend fun body(url: String): String = bytes(url).toString(Charsets.UTF_8)

    private suspend fun bytes(url: String): ByteArray =
        execute(Request.Builder().url(url).build()).use { r ->
            if (!r.isSuccessful) throw IOException("z-torrents: http ${r.code} for $url")
            r.body?.bytes() ?: ByteArray(0)
        }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = baseClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            })
        }
}
