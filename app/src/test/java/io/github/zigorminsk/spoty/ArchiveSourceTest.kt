package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.data.remote.ArchiveFile
import io.github.zigorminsk.spoty.data.remote.archiveFileUrl
import io.github.zigorminsk.spoty.data.remote.archiveFormatRank
import io.github.zigorminsk.spoty.data.remote.parseArchiveLength
import io.github.zigorminsk.spoty.data.remote.prettifyArchiveName
import io.github.zigorminsk.spoty.data.remote.selectArchiveAudio
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveSourceTest {
    @Test
    fun `file url encodes spaces but keeps slashes`() {
        val url = archiveFileUrl("item1", "disc 1/track 01.mp3")
        assertEquals("https://archive.org/download/item1/disc%201/track%2001.mp3", url)
    }

    @Test
    fun `vbr mp3 preferred over ogg`() {
        assertTrue(archiveFormatRank("VBR MP3") < archiveFormatRank("Ogg Vorbis"))
        assertEquals(-1, archiveFormatRank("PNG"))
        assertEquals(-1, archiveFormatRank(null))
    }

    @Test
    fun `selectArchiveAudio picks best format per original`() {
        val files = listOf(
            ArchiveFile(name = "t1.mp3", source = "original", format = "VBR MP3"),
            ArchiveFile(name = "t1.ogg", source = "derivative", format = "Ogg Vorbis", original = "t1.mp3"),
            ArchiveFile(name = "t1.png", source = "derivative", format = "PNG", original = "t1.mp3"),
            ArchiveFile(name = "t2.ogg", source = "derivative", format = "Ogg Vorbis", original = "t2.flac"),
        )
        val selected = selectArchiveAudio(files)
        assertEquals(listOf("t1.mp3", "t2.ogg"), selected.map { it.name })
    }

    @Test
    fun `parses length in seconds and mm ss`() {
        assertEquals(240_900, parseArchiveLength(JsonPrimitive("240.9")))
        assertEquals(83_000, parseArchiveLength(JsonPrimitive("1:23")))
        assertEquals(0, parseArchiveLength(null))
    }

    @Test
    fun `prettify removes track numbers and underscores`() {
        assertEquals("Great Song", prettifyArchiveName("01 - Great_Song.mp3"))
        assertEquals("NS050-Multi-Panel Christmas", prettifyArchiveName("x/01-NS050-Multi-Panel_Christmas.mp3"))
    }
}
