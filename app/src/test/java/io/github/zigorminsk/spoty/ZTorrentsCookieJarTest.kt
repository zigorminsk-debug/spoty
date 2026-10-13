package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.data.remote.ztorrents.InMemoryCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZTorrentsCookieJarTest {

    private val url = "https://z-torrents.ru/index.php?newsid=1".toHttpUrl()

    private fun cookie(name: String, value: String, host: String = "z-torrents.ru", expiresAt: Long) =
        Cookie.Builder().name(name).value(value).domain(host).path("/").expiresAt(expiresAt).build()

    @Test
    fun `saved cookie is sent back to the same host`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(url, listOf(cookie("PHPSESSID", "abc", expiresAt = System.currentTimeMillis() + 60_000)))
        val sent = jar.loadForRequest(url)
        assertEquals(listOf("PHPSESSID"), sent.map { it.name })
        assertEquals("abc", sent.single().value)
    }

    @Test
    fun `cookie is not sent to another host`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(url, listOf(cookie("PHPSESSID", "abc", expiresAt = System.currentTimeMillis() + 60_000)))
        assertTrue(jar.loadForRequest("https://example.com/".toHttpUrl()).isEmpty())
    }

    @Test
    fun `expired cookie is dropped and deleting cookie removes the stored one`() {
        val jar = InMemoryCookieJar()
        val now = System.currentTimeMillis()
        jar.saveFromResponse(url, listOf(cookie("PHPSESSID", "abc", expiresAt = now + 60_000)))
        // Сайт сбрасывает cookie, присылая её же с истёкшим сроком.
        jar.saveFromResponse(url, listOf(cookie("PHPSESSID", "", expiresAt = now - 1_000)))
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `newer cookie replaces the older one`() {
        val jar = InMemoryCookieJar()
        val now = System.currentTimeMillis()
        jar.saveFromResponse(url, listOf(cookie("dle_user_id", "1", expiresAt = now + 60_000)))
        jar.saveFromResponse(url, listOf(cookie("dle_user_id", "2", expiresAt = now + 120_000)))
        assertEquals("2", jar.loadForRequest(url).single().value)
    }
}
