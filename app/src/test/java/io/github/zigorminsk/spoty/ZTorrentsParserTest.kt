package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.data.remote.ztorrents.ZTorrentsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZTorrentsParserTest {

    private val searchHtml = """
        <html><body>
        <div class="search_result">
          <a href="https://z-torrents.ru/club/10952-russian-dj-love-songs-5-2026-mp3.html">Russian DJ - Love Songs 5 (2026) MP3</a>
          <a href="https://z-torrents.ru/uploads/posts/2026-09/x.jpg"><img src="https://z-torrents.ru/uploads/posts/2026-09/x.jpg"></a>
          <div>Информация об альбоме / Album info: Название | Title: Russian DJ - Love Songs5
            Исполнитель | Executor: VA Год | Year: 2026 Жанр | Genre: Dance, Club-House, Pop
            Композиций | Tracks: 112 Размер | File size: 901 MB</div>
          <a href="https://z-torrents.ru/club/10952-russian-dj-love-songs-5-2026-mp3.html">Читать дальше »</a>
          <a href="https://z-torrents.ru/user/admin/">admin</a>
          <a href="https://z-torrents.ru/club/">Клубная музыка</a>
        </div>
        <div class="search_result">
          <a href="https://z-torrents.ru/haus/10953-collection-of-house-styles-36-2026-mp3.html">Collection Of House Styles 36 (2026) MP3</a>
          <div>Информация об альбоме / Album info: Год | Year: 2026 Размер | File size: 3.83 GB</div>
          <a href="https://z-torrents.ru/haus/">Хаус музыка</a>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses search results`() {
        val results = ZTorrentsParser.parseSearch(searchHtml)
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals(10952L, first.id)
        assertEquals("Russian DJ - Love Songs 5 (2026) MP3", first.title)
        assertEquals("Клубная музыка", first.category)
        assertEquals("2026", first.year)
        assertEquals("901 MB", first.sizeText)

        assertEquals(10953L, results[1].id)
        assertEquals("3.83 GB", results[1].sizeText)
        assertEquals("Хаус музыка", results[1].category)
    }

    @Test
    fun `empty page gives empty list`() {
        assertTrue(ZTorrentsParser.parseSearch("<html><body>ничего</body></html>").isEmpty())
    }

    @Test
    fun `parses release page`() {
        val html = """
            <html><head><title>Russian DJ - Love Songs 5 (2026) MP3 скачать через торрент</title></head><body>
            <h1>Russian DJ - Love Songs 5 (2026) MP3 скачать через торрент</h1>
            <img src="https://z-torrents.ru/uploads/posts/2026-09/cover.jpg">
            <div>Информация об альбоме / Album info: Год | Year: 2026 Размер | File size: 901 MB</div>
            <div>Скачать торрент: <a href="https://z-torrents.ru/uploads/files/2026-09/1.torrent">love_songs.torrent</a></div>
            </body></html>
        """.trimIndent()
        val details = ZTorrentsParser.parseRelease(html, 10952L)
        assertEquals(10952L, details.id)
        assertEquals("Russian DJ - Love Songs 5 (2026) MP3", details.title)
        assertEquals("https://z-torrents.ru/uploads/files/2026-09/1.torrent", details.torrentUrl)
        assertEquals("https://z-torrents.ru/uploads/posts/2026-09/cover.jpg", details.coverUrl)
        assertTrue(details.infoText.contains("Year: 2026"))
    }

    @Test
    fun `release without torrent has null url`() {
        val details = ZTorrentsParser.parseRelease("<html><body>нет файла</body></html>", 1L)
        assertNull(details.torrentUrl)
    }

    @Test
    fun `reads release id from urls`() {
        assertEquals(10952L, ZTorrentsParser.releaseId("https://z-torrents.ru/club/10952-russian-dj-love-songs-5-2026-mp3.html"))
        assertNull(ZTorrentsParser.releaseId("https://z-torrents.ru/club/"))
        assertNull(ZTorrentsParser.releaseId("https://z-torrents.ru/index.php?newsid=10952"))
    }
}
