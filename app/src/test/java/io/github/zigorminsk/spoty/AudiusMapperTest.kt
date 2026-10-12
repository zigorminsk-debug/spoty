package io.github.zigorminsk.spoty

import io.github.zigorminsk.spoty.data.remote.AudiusAccess
import io.github.zigorminsk.spoty.data.remote.AudiusImage
import io.github.zigorminsk.spoty.data.remote.AudiusTrack
import io.github.zigorminsk.spoty.data.remote.AudiusUser
import io.github.zigorminsk.spoty.data.remote.toArtist
import io.github.zigorminsk.spoty.data.remote.toTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AudiusMapperTest {
    private val user = AudiusUser(id = "u1", name = "Artist", handle = "artist", followerCount = 10)

    @Test
    fun `maps playable track`() {
        val track = AudiusTrack(
            id = "t1",
            title = "Song",
            duration = 120,
            user = user,
            artwork = AudiusImage(s480 = "https://img/480.jpg"),
        ).toTrack()
        assertNotNull(track)
        track!!
        assertEquals("audius:t1", track.id)
        assertEquals(120_000L, track.durationMs)
        assertEquals("audius:u1", track.artistId)
        assertEquals("https://img/480.jpg", track.artworkUrl)
        assertEquals("https://api.audius.co/v1/tracks/t1/stream?app_name=Spoty", track.streamUrl)
    }

    @Test
    fun `skips deleted unlisted or gated tracks`() {
        assertNull(AudiusTrack(id = "t", user = user, isDelete = true).toTrack())
        assertNull(AudiusTrack(id = "t", user = user, isUnlisted = true).toTrack())
        assertNull(AudiusTrack(id = "t", user = user, isStreamable = false).toTrack())
        assertNull(AudiusTrack(id = "t", user = user, access = AudiusAccess(stream = false)).toTrack())
    }

    @Test
    fun `skips deactivated artists`() {
        assertNull(user.copy(isDeactivated = true).toArtist())
        assertEquals("Artist", user.toArtist()?.name)
    }
}
