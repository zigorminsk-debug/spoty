package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.data.remote.parseLrc
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcTest {
    @Test
    fun `parses standard lrc lines`() {
        val lines = parseLrc("[00:01.50]hello\n[01:02.03]world")
        assertEquals(2, lines.size)
        assertEquals(1_500, lines[0].timeMs)
        assertEquals("hello", lines[0].text)
        assertEquals(62_030, lines[1].timeMs)
    }

    @Test
    fun `supports multiple timestamps per line and sorting`() {
        val lines = parseLrc("[00:10.00][00:02.00]chorus")
        assertEquals(2, lines.size)
        assertEquals(2_000, lines[0].timeMs)
        assertEquals(10_000, lines[1].timeMs)
        assertEquals("chorus", lines[1].text)
    }

    @Test
    fun `ignores metadata lines`() {
        val lines = parseLrc("[ar:Artist]\n[00:05]line")
        assertEquals(1, lines.size)
        assertEquals(5_000, lines[0].timeMs)
    }
}
