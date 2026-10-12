package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.util.formatDuration
import io.github.zigorminsk.spoty.util.normalizeKey
import io.github.zigorminsk.spoty.util.parseYear
import io.github.zigorminsk.spoty.util.sanitizeQuery
import io.github.zigorminsk.spoty.util.trackDedupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun `formatDuration formats minutes and hours`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5_000))
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun `normalizeKey strips punctuation and case`() {
        assertEquals("hello world", normalizeKey("  Hello,   WORLD!!  "))
        assertEquals("привет мир", normalizeKey("Привет — мир"))
    }

    @Test
    fun `trackDedupKey ignores bracketed suffixes`() {
        val a = trackDedupKey("Artist", "Song (Official Video)")
        val b = trackDedupKey("ARTIST", "Song [HQ]")
        assertEquals(a, b)
    }

    @Test
    fun `sanitizeQuery removes lucene specials`() {
        assertEquals("foo bar", sanitizeQuery("foo:(bar)*"))
    }

    @Test
    fun `parseYear finds year in dates`() {
        assertEquals(2012, parseYear("2012-01-01T00:00:00Z"))
        assertEquals(1999, parseYear("1999"))
        assertNull(parseYear("not a date"))
        assertNull(parseYear(null))
    }
}
