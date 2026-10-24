package com.tuneplayer.app.playback.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TransitionPlannerTest {

    private fun info(id: String, durationMs: Long, artist: String = "A", album: String = "") =
        TransitionTrackInfo(id, durationMs, artist = artist, album = album)

    private val current = info("1", 200_000L)
    private val next = info("2", 200_000L)

    @Test
    fun `blocks when either track is missing`() {
        assertTrue(plan(null, next).blocked)
        assertTrue(plan(current, null).blocked)
    }

    @Test
    fun `blocks when durations unknown`() {
        assertTrue(plan(current.copy(durationMs = 0), next).blocked)
        assertTrue(plan(current, next.copy(durationMs = 0)).blocked)
    }

    @Test
    fun `blocks tracks too short to mix`() {
        assertTrue(plan(info("1", 40_000L), info("2", 40_000L)).blocked)
    }

    @Test
    fun `gapless for album siblings played through`() {
        val a = info("1", 200_000L, artist = "X", album = "Same Album")
        val b = info("2", 200_000L, artist = "X", album = "Same Album")
        val out = plan(a, b, albumSequential = true)
        assertTrue(out.shouldStart)
        assertEquals(TransitionStyle.GAPLESS, out.transitionStyle)
    }

    @Test
    fun `not gapless for same album outside a sequential run`() {
        val a = info("1", 200_000L, artist = "X", album = "Same Album")
        val b = info("2", 200_000L, artist = "X", album = "Same Album")
        assertFalse(plan(a, b, albumSequential = false).transitionStyle == TransitionStyle.GAPLESS)
    }

    @Test
    fun `equal power when tempo unknown`() {
        val out = plan(current, next, currentBpm = 0.0, nextBpm = 0.0, fadeSeconds = 5.0)
        assertEquals(TransitionStyle.EQUAL_POWER, out.transitionStyle)
        assertEquals(5.0, out.fadeSeconds, 1e-9)
        assertEquals(0, out.transitionBeats)
        assertEquals(0.0, out.bassSwapFraction, 1e-9)
    }

    @Test
    fun `beat matched when tempi within eight percent`() {
        val out = plan(current, next, currentBpm = 120.0, nextBpm = 124.0)
        assertEquals(TransitionStyle.DJ_BLEND, out.transitionStyle)
        assertTrue(out.transitionBeats > 0)
        assertEquals(0.7, out.bassSwapFraction, 1e-9)
        assertEquals(1.0, out.incomingPlaybackRate, 1e-9)
    }

    @Test
    fun `filter handoff when tempi far apart`() {
        val out = plan(current, next, currentBpm = 120.0, nextBpm = 150.0)
        assertEquals(TransitionStyle.DJ_FILTER, out.transitionStyle)
        assertEquals(0.0, out.bassSwapFraction, 1e-9)
    }

    @Test
    fun `slower incoming is stretched up to outgoing tempo`() {
        val out = plan(current, next, currentBpm = 120.0, nextBpm = 100.0)
        assertEquals(1.2, out.incomingPlaybackRate, 1e-9)
    }

    @Test
    fun `faster incoming is never slowed down`() {
        val out = plan(current, next, currentBpm = 120.0, nextBpm = 140.0)
        assertEquals(1.0, out.incomingPlaybackRate, 1e-9)
    }

    @Test
    fun `overlap stays inside the end of the outgoing track`() {
        val out = plan(current, next, currentBpm = 200.0, nextBpm = 200.0)
        val duration = 200.0
        assertTrue(out.transitionStart <= duration)
        assertTrue(out.transitionStart >= duration - out.fadeSeconds)
    }

    @Test
    fun `overlap never longer than twelve seconds`() {
        val out = plan(current, next, currentBpm = 60.0, nextBpm = 60.0)
        assertTrue(out.fadeSeconds <= 12.0 + 1e-9)
        assertTrue(out.fadeSeconds >= 4.0 - 1e-9)
    }

    @Test
    fun `cue never deeper than a quarter of the incoming track`() {
        val short = info("2", 60_000L)
        val out = plan(current, short, currentBpm = 120.0, nextBpm = 120.0)
        assertTrue(out.incomingCueTime <= 15.0 + 1e-9)
    }

    @Test
    fun `tempo ratio folds octaves`() {
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(120.0, 240.0), 1e-9)
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(240.0, 120.0), 1e-9)
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(0.0, 120.0), 1e-9)
        assertEquals(1.5, TransitionPlanner.normalizedTempoRatio(100.0, 150.0), 1e-9)
    }

    @Test
    fun `latest cue is a quarter of length`() {
        assertEquals(30.0, TransitionPlanner.latestIncomingCue(120.0), 1e-9)
        assertTrue(TransitionPlanner.latestIncomingCue(0.0).isInfinite())
    }

    private fun plan(
        current: TransitionTrackInfo?,
        next: TransitionTrackInfo?,
        currentBpm: Double = 0.0,
        nextBpm: Double = 0.0,
        currentTime: Double = 100.0,
        duration: Double = 200.0,
        fadeSeconds: Double = 5.0,
        albumSequential: Boolean = false,
    ) = TransitionPlanner.planTransition(
        current = current,
        next = next,
        currentBpm = currentBpm,
        nextBpm = nextBpm,
        currentTime = currentTime,
        duration = duration,
        fadeSeconds = fadeSeconds,
        albumSequential = albumSequential,
    )
}

class TempoDetectorTest {

    @Test
    fun `returns zero for silence`() {
        assertEquals(0.0, TempoDetector.estimateBpm(FloatArray(1 shl 16), 44100), 1e-9)
    }

    @Test
    fun `returns zero for too short input`() {
        assertEquals(0.0, TempoDetector.estimateBpm(FloatArray(100), 44100), 1e-9)
    }

    @Test
    fun `detects a pulse train near its true tempo`() {
        val sampleRate = 44100
        val bpm = 120.0
        val samples = FloatArray(1 shl 18)
        val period = (60.0 / bpm * sampleRate).toInt()
        for (index in samples.indices) {
            // Short decaying click every beat.
            val phase = index % period
            samples[index] = if (phase < 200) 0.9f else 0f
        }
        val estimated = TempoDetector.estimateBpm(samples, sampleRate)
        assertTrue("expected near $bpm, got $estimated", abs(estimated - bpm) <= 4.0)
    }

    @Test
    fun `detects a slower pulse train`() {
        val sampleRate = 44100
        val bpm = 80.0
        val samples = FloatArray(1 shl 19)
        val period = (60.0 / bpm * sampleRate).toInt()
        for (index in samples.indices) {
            val phase = index % period
            samples[index] = if (phase < 200) 0.8f else 0f
        }
        val estimated = TempoDetector.estimateBpm(samples, sampleRate)
        assertTrue("expected near $bpm, got $estimated", abs(estimated - bpm) <= 6.0)
    }
}
