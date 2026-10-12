package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.util.interleave
import io.github.zigorminsk.spoty.util.rankByQuery
import io.github.zigorminsk.spoty.util.spreadByKey
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionsTest {
    @Test
    fun `interleave alternates sources`() {
        val result = interleave(listOf(listOf("a1", "a2", "a3"), listOf("b1"), listOf("c1", "c2")))
        assertEquals(listOf("a1", "b1", "c1", "a2", "c2", "a3"), result)
    }

    @Test
    fun `rankByQuery puts exact match first`() {
        val items = listOf("Radio Edit", "radiohead", "Radio")
        val ranked = items.rankByQuery("radio") { it }
        assertEquals("Radio", ranked.first())
    }

    @Test
    fun `spreadByKey avoids adjacent duplicates`() {
        val spread = listOf("a", "a", "b", "b").spreadByKey { it }
        assertEquals(listOf("a", "b", "a", "b"), spread)
    }
}
