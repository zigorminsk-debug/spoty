package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.data.remote.rutracker.RuTrackerParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuTrackerParserTest {

    private val searchHtml = """
        <html><body>
        <table id="tor-tbl"><tbody>
        <tr class="tCenter hl-tr">
          <td class="row1 t-ico"><img></td>
          <td class="row1 f-name-col"><div class="f-name"><a href="tracker.php?f=737">Rock</a></div></td>
          <td class="row4 med tLeft t-title-col"><div class="t-title">
            <a data-topic_id="6543210" class="med tLink hl-tags" href="viewtopic.php?t=6543210">(Trip-Hop) Massive Attack - Mezzanine - 1998, FLAC (tracks+.cue), lossless</a>
          </div></td>
          <td class="row1 u-name-col"><div class="u-name"><a href="#">uploader</a></div></td>
          <td class="row4 small nowrap tor-size" data-ts_text="356515840"><a class="small tr-dl dl-stub" href="dl.php?t=6543210">340 MB ↓</a></td>
          <td class="row4 nowrap" data-ts_text="25"><b class="seedmed">25</b></td>
          <td class="row4 leechmed bold" title="Leechers">4</td>
          <td class="row4 small number-format">1234</td>
          <td class="row4 small nowrap" data-ts_text="1600000000"><p>2020</p></td>
        </tr>
        <tr class="tCenter hl-tr">
          <td class="row1 t-ico"><img></td>
          <td class="row1 f-name-col"><div class="f-name"><a href="tracker.php?f=1737">Jazz</a></div></td>
          <td class="row4 med tLeft t-title-col"><div class="t-title">
            <a class="med tLink" href="viewtopic.php?t=777001">Miles Davis - Kind of Blue - 1959, MP3, 320 kbps</a>
          </div></td>
          <td class="row1 u-name-col"><div class="u-name"><a href="#">someone</a></div></td>
          <td class="row4 small nowrap tor-size" data-ts_text="123456789"><a class="small tr-dl dl-stub" href="dl.php?t=777001">117 MB ↓</a></td>
          <td class="row4 nowrap" data-ts_text="7"><b class="seedmed">7</b></td>
          <td class="row4 leechmed bold" title="Leechers">1</td>
          <td class="row4 small number-format">555</td>
          <td class="row4 small nowrap" data-ts_text="1500000000"><p>2017</p></td>
        </tr>
        </tbody></table>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses search results`() {
        val topics = RuTrackerParser.parseSearch(searchHtml)
        assertEquals(2, topics.size)

        val first = topics[0]
        assertEquals(6543210L, first.id)
        assertTrue(first.title.startsWith("(Trip-Hop) Massive Attack"))
        assertEquals("Rock", first.forum)
        assertEquals(356515840L, first.sizeBytes)
        assertEquals(25, first.seeders)
        assertEquals(4, first.leechers)

        // Второй результат: id берётся из href, если нет data-topic_id.
        assertEquals(777001L, topics[1].id)
        assertEquals(7, topics[1].seeders)
    }

    @Test
    fun `empty page gives empty list`() {
        assertTrue(RuTrackerParser.parseSearch("<html><body>nothing</body></html>").isEmpty())
    }

    @Test
    fun `splits release title into artist and album`() {
        val (artist, album) = RuTrackerParser.splitTitle("(Trip-Hop) Massive Attack - Mezzanine - 1998, FLAC (tracks+.cue), lossless")
        assertEquals("Massive Attack", artist)
        assertEquals("Mezzanine", album)

        val (artist2, album2) = RuTrackerParser.splitTitle("[Hi-Res] Miles Davis - Kind of Blue - 1959")
        assertEquals("Miles Davis", artist2)
        assertEquals("Kind of Blue", album2)

        val (artist3, album3) = RuTrackerParser.splitTitle("Просто название раздачи")
        assertEquals(null, artist3)
        assertEquals("Просто название раздачи", album3)
    }

    @Test
    fun `cleans file names`() {
        assertEquals("Teardrop", RuTrackerParser.cleanFileTitle("03. Teardrop"))
        assertEquals("Teardrop", RuTrackerParser.cleanFileTitle("03 - Teardrop"))
        assertEquals("Blue in Green", RuTrackerParser.cleanFileTitle("05_Blue_in_Green"))
        assertEquals("Angel", RuTrackerParser.cleanFileTitle("Angel"))
    }
}
