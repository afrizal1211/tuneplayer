package com.tuneplayer.app.playback.smart

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The minimal facts about a queue item the planner needs, independent of Media3. */
data class TransitionTrackInfo(
    val id: String,
    val durationMs: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
)

/** How the renderer should execute a planned transition. */
enum class TransitionStyle {
    /** A constant-power fade, unfiltered. The only style the bottom tier permits. */
    EQUAL_POWER,

    /** Album siblings played through: a near-instant handoff, not a mix. */
    GAPLESS,

    /** Beat-aligned blend with a bass swap, for matching or near-matching tempi. */
    DJ_BLEND,

    /** Filtered handoff for tempi too far apart to blend flat. */
    DJ_FILTER,
}

/**
 * The planned transition for one pair of tracks, in outgoing-track timeline
 * seconds.
 *
 * [shouldStart] is what says the playhead has actually reached the plan. When
 * [blocked] is true nothing should happen at all and [reason] says why.
 */
data class TransitionPlan(
    val shouldStart: Boolean = false,
    val blocked: Boolean = false,
    val reason: String = "",
    val transitionStart: Double = 0.0,
    val fadeSeconds: Double = 0.0,
    val transitionStyle: TransitionStyle = TransitionStyle.EQUAL_POWER,
    /** Where in the incoming track playback should be cued to when the transition opens. */
    val incomingCueTime: Double = 0.0,
    /**
     * Tempo the incoming track is stretched by to align with the outgoing one.
     * Never below 1 — tempo only ever moves up, so at most one side of a pair
     * is stretched.
     */
    val incomingPlaybackRate: Double = 1.0,
    /** Overlap length in outgoing beats; 0 when the grid is not trusted. */
    val transitionBeats: Int = 0,
    /** Where the low end changes hands over the blend, 0..1. */
    val bassSwapFraction: Double = 0.7,
    val outgoingBpm: Double = 0.0,
    val incomingBpm: Double = 0.0,
) {
    /** Convenience for the engine, which schedules in milliseconds. */
    val fadeMs: Long get() = (fadeSeconds * 1000).roundToLong()
}

/**
 * Turns tempo estimates into a concrete transition plan for one pair of tracks.
 *
 * Nothing here touches PCM; the planner decides *where* a transition happens
 * and *how* ambitious it is. The crossfade controller is what executes a plan.
 *
 * Pure: no clock, no I/O, no Android. Both the renderer and the marker read
 * the same plan, which is why the gesture is deterministic per pair rather
 * than random per tick.
 */
object TransitionPlanner {

    /** Four bars. Overlaps are counted in beats because that is what the ear hears. */
    private const val AUTO_TRANSITION_MAX_BEATS = 16.0
    private const val AUTO_MIN_SECONDS = 4.0
    private const val AUTO_TRANSITION_MAX_SECONDS = 12.0
    private const val AUTO_FALLBACK_SECONDS = 8.0

    /** A mix lives entirely within the last 30 s of the outgoing track. */
    private const val MIX_WINDOW_SECONDS = 30.0

    /** Below this a track would spend too much of itself transitioning. */
    private const val MIN_SMART_DURATION_SECONDS = 45.0

    /** Leave enough incoming material after a calibrated handoff. */
    private const val MIN_INCOMING_CLEARANCE_SECONDS = 5.0

    /** The deepest an incoming song may be cued; unbounded when the length is unknown. */
    const val MAX_INCOMING_SKIP_FRACTION = 0.25

    /** Tempo ratio bounds: a fold beyond this is a different tempo, not a mix. */
    private const val TEMPO_RATIO_MAX = 1.5
    private const val TEMPO_RATIO_MIN = 0.67

    /** Below this tempo distance a beat-matched blend is possible. */
    private const val DJ_BLEND_RATIO = 1.08

    /**
     * Plans the transition from [current] to [next].
     *
     * [currentTime] and [duration] are the outgoing track's playhead and length
     * in seconds. [fallbackSeconds] is the plain crossfade length used whenever
     * the planner has no better evidence.
     */
    fun planTransition(
        current: TransitionTrackInfo?,
        next: TransitionTrackInfo?,
        currentBpm: Double,
        nextBpm: Double,
        currentTime: Double,
        duration: Double,
        fadeSeconds: Double,
        albumSequential: Boolean = false,
    ): TransitionPlan {
        if (current == null || next == null) {
            return blocked("missing track")
        }
        if (current.durationMs <= 0 || next.durationMs <= 0) {
            return blocked("unknown duration")
        }
        val nextLength = next.durationMs / 1000.0
        if (nextLength < MIN_SMART_DURATION_SECONDS || duration < MIN_SMART_DURATION_SECONDS) {
            return blocked("track too short to mix")
        }

        // Gapless is for an album being played through, not for any two songs
        // that happen to share an album. The caller says which it is.
        if (albumSequential && sameAlbum(current, next)) {
            return TransitionPlan(
                shouldStart = true,
                transitionStyle = TransitionStyle.GAPLESS,
                fadeSeconds = min(0.25, fadeSeconds),
                transitionStart = max(0.0, duration - 0.25),
                reason = "album sequential",
            )
        }

        // Without a tempo estimate there is no evidence to beat-match on, and
        // the plain crossfade the listener asked for is the honest answer.
        val hasTempo = currentBpm > 0 && nextBpm > 0
        val ratio = normalizedTempoRatio(currentBpm, nextBpm)
        val style = when {
            !hasTempo -> TransitionStyle.EQUAL_POWER
            ratio <= 0.0 -> TransitionStyle.EQUAL_POWER
            ratio in (1.0 / DJ_BLEND_RATIO)..DJ_BLEND_RATIO -> TransitionStyle.DJ_BLEND
            else -> TransitionStyle.DJ_FILTER
        }

        val beats = if (currentBpm > 0) currentBpm / 60.0 else 0.0
        val beatSeconds = if (currentBpm > 0) 60.0 / currentBpm else 0.0

        // Overlap in outgoing beats, railed to seconds for absurd tempi.
        val overlapBeats = when (style) {
            TransitionStyle.DJ_BLEND -> min(AUTO_TRANSITION_MAX_BEATS, 16.0)
            TransitionStyle.DJ_FILTER -> min(AUTO_TRANSITION_MAX_BEATS, 8.0)
            else -> 0.0
        }
        val overlapSeconds = (overlapBeats * beatSeconds)
            .coerceIn(AUTO_MIN_SECONDS, AUTO_TRANSITION_MAX_SECONDS)

        // A DJ blend lands its cue on the incoming track's own downbeat where
        // possible; without a beat grid the plain start is the honest answer.
        val latestCue = latestIncomingCue(nextLength)
        val incomingCue = if (style == TransitionStyle.EQUAL_POWER) {
            0.0
        } else {
            // Skip a little past the top so the blend does not open on a
            // lead-in silence, but never deeper than the cap allows.
            min(latestCue, if (beatSeconds > 0) beatSeconds * 4.0 else 0.0)
        }

        // The incoming track is sped up, never slowed, to reach the outgoing
        // tempo — so it is the outgoing tempo that sets the target. When the
        // incoming track is already the faster of the two it is left alone.
        val rate = if (currentBpm > 0 && nextBpm > 0 && nextBpm < currentBpm) {
            (currentBpm / nextBpm).coerceAtMost(TEMPO_RATIO_MAX)
        } else {
            1.0
        }

        val transitionStart = max(0.0, duration - overlapSeconds)
        val finalFade = when (style) {
            TransitionStyle.EQUAL_POWER -> fadeSeconds
            else -> overlapSeconds
        }

        val reasons = buildList {
            if (style == TransitionStyle.DJ_FILTER) add("tempi ${fmt(currentBpm)}/${fmt(nextBpm)}")
            if (style == TransitionStyle.DJ_BLEND) add("beat matched ${fmt(currentBpm)}/${fmt(nextBpm)}")
            if (incomingCue >= latestCue) add("cue capped")
        }

        return TransitionPlan(
            shouldStart = true,
            blocked = false,
            reason = reasons.joinToString(",").ifEmpty { "plain" },
            transitionStart = transitionStart,
            fadeSeconds = finalFade,
            transitionStyle = style,
            incomingCueTime = incomingCue,
            incomingPlaybackRate = rate,
            transitionBeats = if (style == TransitionStyle.EQUAL_POWER) 0 else overlapBeats.roundToInt(),
            bassSwapFraction = if (style == TransitionStyle.DJ_BLEND) 0.7 else 0.0,
            outgoingBpm = currentBpm,
            incomingBpm = nextBpm,
        )
    }

    /** Folds [nextBpm] into the same octave as [currentBpm]. */
    fun normalizedTempoRatio(currentBpm: Double, nextBpm: Double): Double {
        if (currentBpm <= 0 || nextBpm <= 0) return 1.0
        var ratio = nextBpm / currentBpm
        while (ratio > TEMPO_RATIO_MAX) ratio /= 2
        while (ratio < TEMPO_RATIO_MIN) ratio *= 2
        return ratio
    }

    /** The deepest an incoming song of [length] seconds may be cued. */
    fun latestIncomingCue(length: Double): Double =
        if (length.isFinite() && length > 0) length * MAX_INCOMING_SKIP_FRACTION
        else Double.POSITIVE_INFINITY

    private fun sameAlbum(left: TransitionTrackInfo, right: TransitionTrackInfo): Boolean {
        if (left.albumId.isNotBlank() && left.albumId == right.albumId) return true
        return left.album.isNotBlank() && left.album == right.album && left.artist == right.artist
    }

    private fun blocked(reason: String) = TransitionPlan(blocked = true, reason = reason)

    private fun fmt(value: Double): String =
        if (value > 0) "${value.roundToInt()}bpm" else "unknown"
}

/**
 * Tempo estimation from a PCM buffer, by autocorrelation.
 *
 * A model-based beat tracker is more accurate, but it needs inference weights
 * shipped in the APK and a native runtime to run them. This is the honest
 * fallback: pure Kotlin, no assets, no NDK, and it degrades to "no evidence" —
 * which the planner already answers with the same fixed-length crossfade the
 * app ran before Automix existed.
 *
 * ponytail: autocorrelation over the whole buffer. Add a model-based tracker
 * (Beat This! ONNX, MIT-licensed) when per-track CPU budget allows the decode.
 */
object TempoDetector {

    /** Tempi outside this range are not worth a musical transition. */
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 200.0

    /**
     * Estimates tempo in BPM from [samples] at [sampleRate], or 0 when the
     * signal is too short or too quiet to say anything.
     */
    fun estimateBpm(samples: FloatArray, sampleRate: Int): Double {
        if (samples.size < MIN_SAMPLES || sampleRate <= 0) return 0.0

        // Energy envelope first: onset detection over 10 ms hops. Beat tracking
        // runs on how loud the signal is, not on its waveform.
        val hop = max(1, sampleRate / 100)
        val envelope = envelope(samples, hop)
        if (envelope.size < 4) return 0.0

        // Normalise so the correlation is a shape match, not a level match.
        val mean = envelope.average()
        val centred = envelope.map { it - mean }
        val energy = centred.sumOf { it * it }
        if (energy <= 1e-9) return 0.0

        var bestLag = 0
        var bestScore = 0.0
        val minLag = lagForBpm(MAX_BPM, sampleRate, hop)
        val maxLag = lagForBpm(MIN_BPM, sampleRate, hop).coerceAtMost(centred.size - 1)

        for (lag in minLag..maxLag) {
            var score = 0.0
            for (index in 0 until centred.size - lag) {
                score += centred[index] * centred[index + lag]
            }
            val normalized = score / energy
            // Prefer the shortest lag that scores nearly as well as the best,
            // so a half-time reading does not win on a longer buffer.
            if (normalized > bestScore) {
                bestScore = normalized
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestScore < MIN_CORRELATION) return 0.0
        return bpmForLag(bestLag, sampleRate, hop)
    }

    private fun envelope(samples: FloatArray, hop: Int): DoubleArray {
        val frames = samples.size / hop
        val out = DoubleArray(frames)
        for (frame in 0 until frames) {
            var sum = 0.0
            val start = frame * hop
            val end = min(start + hop, samples.size)
            for (index in start until end) {
                val value = samples[index].toDouble()
                sum += value * value
            }
            out[frame] = sum
        }
        return out
    }

    private fun lagForBpm(bpm: Double, sampleRate: Int, hop: Int): Int =
        max(1, round(60.0 / bpm * sampleRate / hop).toInt())

    private fun bpmForLag(lag: Int, sampleRate: Int, hop: Int): Double =
        60.0 * sampleRate / (lag * hop)

    private const val MIN_SAMPLES = 32_768
    private const val MIN_CORRELATION = 0.15
}
