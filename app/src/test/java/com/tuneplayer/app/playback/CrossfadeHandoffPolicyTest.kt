package com.tuneplayer.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeHandoffPolicyTest {

    @Test
    fun `equal power holds at endpoints and midpoint`() {
        val start = equalPowerGains(0f)
        val mid = equalPowerGains(0.5f)
        val end = equalPowerGains(1f)

        assertEquals(1f, start.outgoing, 0.0001f)
        assertEquals(0f, start.incoming, 0.0001f)
        assertEquals(0f, end.outgoing, 0.0001f)
        assertEquals(1f, end.incoming, 0.0001f)

        val power = mid.outgoing * mid.outgoing + mid.incoming * mid.incoming
        assertEquals(1f, power, 0.0001f)
    }

    @Test
    fun `progress outside zero to one is clamped`() {
        assertEquals(equalPowerGains(0f), equalPowerGains(-3f))
        assertEquals(equalPowerGains(1f), equalPowerGains(9f))
    }

    @Test
    fun `outgoing gain never rises while incoming rises`() {
        var previousOutgoing = Float.MAX_VALUE
        var previousIncoming = -1f
        var step = 0
        while (step <= 20) {
            val gains = equalPowerGains(step / 20f)
            assertTrue(gains.outgoing <= previousOutgoing)
            assertTrue(gains.incoming >= previousIncoming)
            previousOutgoing = gains.outgoing
            previousIncoming = gains.incoming
            step++
        }
    }

    @Test
    fun `corrective seek skipped when drift is within tolerance`() {
        assertFalse(needsCorrectiveCrossfadeSeek(10_040L, 10_000L, maximumDriftMs = 75L))
        assertFalse(needsCorrectiveCrossfadeSeek(10_075L, 10_000L, maximumDriftMs = 75L))
    }

    @Test
    fun `corrective seek required when drift exceeds tolerance either direction`() {
        assertTrue(needsCorrectiveCrossfadeSeek(9_900L, 10_000L, maximumDriftMs = 75L))
        assertTrue(needsCorrectiveCrossfadeSeek(10_100L, 10_000L, maximumDriftMs = 75L))
    }

    @Test
    fun `position must move forward after seek to count as advanced`() {
        assertFalse(hasPlaybackPositionAdvanced(10_000L, 10_000L))
        assertFalse(hasPlaybackPositionAdvanced(10_000L, 9_999L))
        assertTrue(hasPlaybackPositionAdvanced(10_000L, 10_001L))
    }
}

class StreamChunkResolverTest {

    @Test
    fun `chunk is the shortest of chunk request and remaining`() {
        assertEquals(
            500L,
            resolveStreamChunkLength(
                requestedLength = 4_096L,
                position = 1_000L,
                knownContentLength = 1_500L,
                chunkLength = 500L,
            ),
        )
        assertEquals(
            100L,
            resolveStreamChunkLength(
                requestedLength = 100L,
                position = 1_000L,
                knownContentLength = 9_999L,
                chunkLength = 4_096L,
            ),
        )
    }

    @Test
    fun `read is refused once the position reaches the known end`() {
        assertNull(
            resolveStreamChunkLength(
                requestedLength = 4_096L,
                position = 1_500L,
                knownContentLength = 1_500L,
                chunkLength = 500L,
            ),
        )
    }

    @Test
    fun `open ended read is refused for mime types that require a length`() {
        assertNull(
            resolveStreamChunkLength(
                requestedLength = 0L,
                position = 0L,
                knownContentLength = null,
                chunkLength = 500L,
                mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
            ),
        )
    }

    @Test
    fun `open ended read is allowed for mime types that support it`() {
        assertEquals(
            500L,
            resolveStreamChunkLength(
                requestedLength = 0L,
                position = 0L,
                knownContentLength = null,
                chunkLength = 500L,
                mimeType = "audio/mpeg",
            ),
        )
    }

    @Test
    fun `degenerate chunk or negative position yields nothing`() {
        assertNull(
            resolveStreamChunkLength(
                requestedLength = 10L,
                position = 0L,
                knownContentLength = null,
                chunkLength = 0L,
            ),
        )
        assertNull(
            resolveStreamChunkLength(
                requestedLength = 10L,
                position = -1L,
                knownContentLength = null,
                chunkLength = 500L,
            ),
        )
    }
}
