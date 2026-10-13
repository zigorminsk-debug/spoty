package io.github.zigorminsk.spoty.data.remote.rutracker

import io.github.zigorminsk.spoty.data.settings.SettingsStore
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup

/** Ключ источника в идентификаторах треков: «rutracker:<topic>:<file>». */
const val RUTRACKER_SOURCE = "rutracker"

/** Схема URI для проигрывания файлов из раздач: torrent://<источник>/<topicId>/<fileIndex>. */
const val TORRENT_URI_SCHEME = "torrent"

fun torrentStreamUrl(source: String, topicId: Long, fileIndex: Int): String =
    "$TORRENT_URI_SCHEME://$source/$topicId/$fileIndex"

/**
 * Адрес зеркала к каноническому виду «схема://хост»: «rutracker.org» → «https://rutracker.org»,
 * «http://rutracker.net/forum/» → «http://rutracker.net». Пустая строка остаётся пустой.
 * Без схемы OkHttp бросил бы исключение на первом же запросе.
 */
fun normalizeMirrorUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val scheme = if ("://" in trimmed) trimmed.substringBefore("://").ifBlank { "https" } else "https"
    val rest = trimmed.substringAfter("://", trimmed)
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#').trim()
    return if (authority.isEmpty()) trimmed else "$scheme://$authority"
}

/** Браузероподобный User-Agent: часть защит трекера отсекает запросы «не из браузера». */
internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

internal const val HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
internal const val RU_LANG = "ru-RU,ru;q=0.9,en-US;q=0.7,en;q=0.5"

/** Раздача из результатов поиска трекера. */
data class RuTrackerTopic(
    val id: Long,
    val title: String,
    val forum: String,
    val sizeBytes: Long,
    val seeders: Int,
    val leechers: Int,
)

sealed class RuTrackerError(message: String) : IOException(message) {
    /** Нет сессии и нет сохранённых учётных данных (или они больше не подходят). */
    class NotAuthorized : RuTrackerError("rutracker: not authorized")

    /** Трекер требует капчу — вход возможен только через браузер, позже можно повторить. */
    class Captcha : RuTrackerError("rutracker: captcha required")

    /** Логин или пароль не подошли. */
    class BadCredentials : RuTrackerError("rutracker: bad credentials")

    /** Зеркало не открылось: неверный адрес, DNS, TLS или таймаут. */
    class Unreachable(detail: String = "") : RuTrackerError(("rutracker: mirror unreachable $detail").trim())

    /** Трекер или его защита отклонили запрос (HTTP 403/429/503). */
    class Blocked(val code: Int) : RuTrackerError("rutracker: request blocked, http $code")
}

/** Разбор HTML-страниц RuTracker. Вынесен отдельно, чтобы покрыть тестами без Android. */
object RuTrackerParser {

    private val topicIdRegex = Regex("""[?&]t=(\d+)""")

    /** Таблица результатов tracker.php. */
    fun parseSearch(html: String): List<RuTrackerTopic> {
        val doc = Jsoup.parse(html)
        return doc.select("table#tor-tbl tr.hl-tr, table#tor-tbl tr.tCenter").distinct().mapNotNull { row ->
            val link = row.selectFirst("a.tLink") ?: return@mapNotNull null
            val id = link.attr("data-topic_id").toLongOrNull()
                ?: topicIdRegex.find(link.attr("href"))?.groupValues?.get(1)?.toLongOrNull()
                ?: return@mapNotNull null
            val sizeCell = row.selectFirst("td.tor-size")
            val size = sizeCell?.attr("data-ts_text")?.trim()?.toLongOrNull() ?: 0L
            val seeders = row.selectFirst("b.seedmed")?.text()?.trim()?.toIntOrNull()
                ?: row.selectFirst("td.seedmed")?.text()?.trim()?.toIntOrNull()
                ?: 0
            val leechers = row.selectFirst("td.leechmed")?.text()?.trim()?.toIntOrNull() ?: 0
            RuTrackerTopic(
                id = id,
                title = link.text().trim(),
                forum = row.selectFirst(".f-name a")?.text()?.trim().orEmpty(),
                sizeBytes = size,
                seeders = seeders.coerceAtLeast(0),
                leechers = leechers.coerceAtLeast(0),
            )
        }
    }

    /**
     * Эвристика «(Жанр) Исполнитель - Альбом - Год, формат» → исполнитель и альбом.
     * Возвращает пару (исполнитель?, альбом?).
     */
    fun splitTitle(raw: String): Pair<String?, String?> {
        var s = raw.trim()
        // Убираем ведущие группы в скобках: «(Rock)», «[Hi-Res]» и т.п.
        while (true) {
            val cleaned = s.replace(Regex("""^[(\[][^)\]]*[)\]]\s*"""), "")
            if (cleaned == s) break
            s = cleaned.trim()
        }
        val parts = s.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null to s.ifBlank { null }
        val artist = parts[0].takeIf { it.isNotBlank() }
        val album = parts[1].substringBefore(" [").substringBefore(" (").trim().trimEnd(',').ifBlank { null }
        return artist to album
    }

    /** Имя аудиофайла → название трека: без расширения, подчёркиваний и ведущего номера. */
    fun cleanFileTitle(name: String): String {
        val cleaned = name
            .replace('_', ' ')
            .replace(Regex("""^\s*\d{1,3}(\s*[-.)]\s*|\s+)"""), "")
            .trim()
        return cleaned.ifEmpty { name.trim() }
    }
}

/**
 * Клиент RuTracker: вход по логину и паролю, поиск раздач и скачивание .torrent-файлов.
 * Все страницы трекера — в кодировке windows-1251; формы шлём в той же кодировке.
 */
class RuTrackerClient(
    baseClient: OkHttpClient,
    private val settings: SettingsStore,
) {
    /** Своим User-Agent приложение светить не должно: подменяем на браузерный после общего интерцептора. */
    private val browserized: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", BROWSER_USER_AGENT).build())
        })
        .build()

    /** Клиент для обычных страниц: следует редиректам (по ним же понимаем, что сессия истекла). */
    private val web: OkHttpClient = browserized.newBuilder().followRedirects(true).build()

    /** Клиент для входа: редиректам не следует, чтобы прочитать Set-Cookie из ответа 302. */
    private val noRedirect: OkHttpClient = browserized.newBuilder().followRedirects(false).build()

    /** Свежая cookie-сессия: DataStore обновляется асинхронно, поэтому держим и локальную копию. */
    @Volatile
    private var cookieCache: String? = null

    private fun base(): String {
        val normalized = normalizeMirrorUrl(settings.value.rutrackerMirror)
        // Пустой адрес или мусор без хоста — иначе OkHttp упадёт на разборе URL.
        if (normalized.substringAfter("://", "").isBlank()) {
            throw RuTrackerError.Unreachable(settings.value.rutrackerMirror)
        }
        return normalized
    }

    private fun forumUrl(pathAndQuery: String): String = "${base()}/forum/$pathAndQuery"

    private fun currentCookie(): String = cookieCache ?: settings.value.rutrackerCookie

    private fun encode(value: String): String = URLEncoder.encode(value, "windows-1251")

    private fun formBody(vararg fields: Pair<String, String>): RequestBody {
        val encoded = fields.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return encoded.toByteArray(Charsets.ISO_8859_1)
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    private suspend fun execute(client: OkHttpClient, request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
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

    /** То же [execute], но сетевой сбой (DNS, TLS, таймаут) превращает в понятную ошибку зеркала. */
    private suspend fun executeSafe(client: OkHttpClient, request: Request): Response =
        try {
            execute(client, request)
        } catch (e: IOException) {
            throw RuTrackerError.Unreachable(e.message.orEmpty())
        }

    /**
     * Вход по логину и паролю. При успехе сохраняет учётные данные и cookie-сессию в настройках.
     * @throws RuTrackerError.Captcha если трекер показал капчу;
     * @throws RuTrackerError.BadCredentials если логин/пароль не подошли.
     */
    /** Cookie из ответа без атрибутов: «bb_session=abc; path=/» → «bb_session=abc». */
    private fun Response.readSetCookies(): List<String> =
        headers("Set-Cookie")
            .map { it.substringBefore(';').trim() }
            .filter { c -> '=' in c && !c.endsWith("=") && !c.endsWith("=deleted") }

    suspend fun login(login: String, password: String) {
        val url = forumUrl("login.php")
        // Прогрев: сперва открываем страницу входа как браузер, чтобы собрать cookie
        // защиты трекера. POST «с пустого места» без них нередко получает 403,
        // хотя в браузере тот же вход работает.
        val warmCookies = executeSafe(
            web,
            Request.Builder().url(url)
                .header("Accept", HTML_ACCEPT)
                .header("Accept-Language", RU_LANG)
                .build(),
        ).use { it.readSetCookies() }
        val request = Request.Builder()
            .url(url)
            .header("Referer", url)
            .header("Origin", base())
            .header("Accept", HTML_ACCEPT)
            .header("Accept-Language", RU_LANG)
            .header("Cookie", warmCookies.joinToString("; "))
            .post(formBody("login_username" to login, "login_password" to password, "login" to "вход"))
            .build()
        executeSafe(noRedirect, request).use { r ->
            val cookies = r.readSetCookies()
            val hasSession = cookies.any { it.startsWith("bb_session=") }
            if (r.code in 300..399 && hasSession) {
                // Cookie защиты + сессия: пригодятся и поиску, и торрент-движку.
                val cookie = (warmCookies + cookies).distinct().joinToString("; ")
                cookieCache = cookie
                settings.setRutrackerAuth(login, password)
                settings.setRutrackerCookie(cookie)
                return
            }
            val body = runCatching { r.body?.string().orEmpty() }.getOrDefault("")
            if ("cap_sid" in body) throw RuTrackerError.Captcha()
            // Защита от ботов или недоступный ответ сервера — это не «неверный пароль».
            if (r.code == 403 || r.code == 429 || r.code == 503) throw RuTrackerError.Blocked(r.code)
            if (!r.isSuccessful) throw RuTrackerError.Unreachable("http ${r.code}")
            throw RuTrackerError.BadCredentials()
        }
    }

    /** Разлогиниться локально (трекер сам «забудет» сессию по сроку). */
    suspend fun logout() {
        cookieCache = null
        settings.clearRutracker()
    }

    private suspend fun ensureCookie(): String {
        currentCookie().takeIf { it.isNotBlank() }?.let { return it }
        return relogin()
    }

    private suspend fun relogin(): String {
        val s = settings.value
        if (s.rutrackerLogin.isBlank() || s.rutrackerPassword.isBlank()) throw RuTrackerError.NotAuthorized()
        login(s.rutrackerLogin, s.rutrackerPassword)
        return currentCookie()
    }

    private fun isLoginRedirect(response: Response): Boolean =
        response.request.url.toString().contains("login.php")

    /** Поиск раздач, отсортированных по числу сидов. */
    suspend fun search(query: String): List<RuTrackerTopic> {
        var cookie = ensureCookie()
        repeat(2) { attempt ->
            val url = forumUrl("tracker.php?nm=${encode(query)}&o=10&s=2")
            val request = Request.Builder().url(url).header("Cookie", cookie).build()
            val html = executeSafe(web, request).use { r ->
                // Защита от ботов на выдаче: честно говорим «заблокировано», а не «ничего не найдено».
                if (r.code == 403 || r.code == 429 || r.code == 503) throw RuTrackerError.Blocked(r.code)
                if (isLoginRedirect(r)) null else r.body?.string().orEmpty()
            }
            if (html != null) return RuTrackerParser.parseSearch(html)
            if (attempt == 0) cookie = relogin()
        }
        throw RuTrackerError.NotAuthorized()
    }

    /** Скачивает .torrent-файл раздачи. */
    suspend fun torrentBytes(topicId: Long): ByteArray {
        var cookie = ensureCookie()
        repeat(2) { attempt ->
            val request = Request.Builder()
                .url(forumUrl("dl.php?t=$topicId"))
                .header("Cookie", cookie)
                .header("Referer", forumUrl("viewtopic.php?t=$topicId"))
                .build()
            val bytes = executeSafe(web, request).use { r ->
                if (r.code == 403 || r.code == 429 || r.code == 503) throw RuTrackerError.Blocked(r.code)
                val body = r.body?.bytes() ?: ByteArray(0)
                // .torrent — это bencode-словарь, он всегда начинается с байта «d».
                val isTorrent = r.isSuccessful && body.isNotEmpty() && body[0] == 'd'.code.toByte()
                if (!isLoginRedirect(r) && isTorrent) body else null
            }
            if (bytes != null) return bytes
            if (attempt == 0) cookie = relogin()
        }
        throw RuTrackerError.NotAuthorized()
    }
}
