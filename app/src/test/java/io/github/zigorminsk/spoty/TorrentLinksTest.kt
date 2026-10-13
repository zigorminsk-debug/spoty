package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.data.remote.rutracker.parseTorrentUriParts
import io.github.zigorminsk.spoty.data.remote.rutracker.torrentStreamUrl
import io.github.zigorminsk.spoty.data.remote.rutracker.torrentStreamUrlFromTrackId
import io.github.zigorminsk.spoty.data.remote.ztorrents.resolveZtorrentsUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentLinksTest {

    @Test
    fun `builds and parses torrent uri`() {
        val url = torrentStreamUrl("rutracker", 6543210L, 3)
        assertEquals("torrent://rutracker/6543210/3", url)

        val parts = parseTorrentUriParts("rutracker", listOf("6543210", "3"))
        assertEquals("rutracker", parts?.source)
        assertEquals(6543210L, parts?.topicId)
        assertEquals(3, parts?.fileIndex)
    }

    @Test
    fun `rejects malformed torrent uri`() {
        assertNull(parseTorrentUriParts(null, listOf("1", "2")))
        assertNull(parseTorrentUriParts("", listOf("1", "2")))
        assertNull(parseTorrentUriParts("rutracker", listOf("abc", "2")))
        assertNull(parseTorrentUriParts("rutracker", listOf("1")))
        assertNull(parseTorrentUriParts("rutracker", listOf("1", "-1")))
        assertNull(parseTorrentUriParts("rutracker", emptyList()))
    }

    @Test
    fun `restores torrent url from track id`() {
        // По одному mediaId медиасессия обязана уметь восстановить адрес потока:
        // URI и extras при передаче через сессию могут потеряться, mediaId — нет.
        assertEquals(
            "torrent://rutracker/6543210/3",
            torrentStreamUrlFromTrackId("rutracker:6543210:3"),
        )
        assertEquals(
            "torrent://ztorrents/10952/0",
            torrentStreamUrlFromTrackId("ztorrents:10952:0"),
        )
    }

    @Test
    fun `track id without torrent address gives null`() {
        assertNull(torrentStreamUrlFromTrackId("audius:xyz"))
        assertNull(torrentStreamUrlFromTrackId("audius:123:extra:parts"))
        assertNull(torrentStreamUrlFromTrackId("rutracker:abc:1"))
        assertNull(torrentStreamUrlFromTrackId("rutracker:123:-2"))
        assertNull(torrentStreamUrlFromTrackId(""))
    }

    @Test
    fun `resolves ztorrents urls`() {
        assertEquals(
            "https://z-torrents.ru/uploads/files/1.torrent",
            resolveZtorrentsUrl("https://z-torrents.ru/uploads/files/1.torrent"),
        )
        assertEquals(
            "https://z-torrents.ru/uploads/files/1.torrent",
            resolveZtorrentsUrl("/uploads/files/1.torrent"),
        )
        assertEquals(
            "https://z-torrents.ru/uploads/files/1.torrent",
            resolveZtorrentsUrl("//z-torrents.ru/uploads/files/1.torrent"),
        )
        assertEquals(
            "https://z-torrents.ru/uploads/files/1.torrent",
            resolveZtorrentsUrl("uploads/files/1.torrent"),
        )
    }
}
