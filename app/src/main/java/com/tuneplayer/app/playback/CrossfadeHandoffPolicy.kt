package com.tuneplayer.app.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The gain pair a crossfade applies at a given progress.
 *
 * Outgoing falls on `cos`, incoming rises on `sin`, so
 * `outgoing² + incoming² == 1` for the whole blend. Equal *power* rather than
 * equal amplitude: two loud masters summed linearly peak at 1.41 mid-blend and
 * clip, while equal-power holds constant loudness and never dips in the middle.
 */
data class EqualPowerGains(
    val outgoing: Float,
    val incoming: Float,
)

/** Progress in 0..1 to the equal-power gain pair at that point of the blend. */
fun equalPowerGains(progress: Float): EqualPowerGains {
    val clamped = progress.coerceIn(0f, 1f)
    val radians = clamped.toDouble() * (PI / 2.0)
    return EqualPowerGains(
        outgoing = cos(radians).toFloat(),
        incoming = sin(radians).toFloat(),
    )
}

/**
 * Whether the two players have drifted far enough apart that the incoming one
 * must be re-seeked onto the outgoing one's position.
 *
 * Two decoders started separately never hold sample-accurate lock; a small
 * drift is inaudible, a large one is the incoming track audibly ahead or
 * behind the one leaving.
 */
fun needsCorrectiveCrossfadeSeek(
    primaryPositionMs: Long,
    secondaryPositionMs: Long,
    maximumDriftMs: Long,
): Boolean {
    require(maximumDriftMs >= 0L) { "maximumDriftMs must be non-negative" }
    return abs(primaryPositionMs - secondaryPositionMs) > maximumDriftMs
}

/** Whether the player actually moved forward after a seek, rather than sitting at the seek point. */
fun hasPlaybackPositionAdvanced(
    positionAfterSeekMs: Long,
    currentPositionMs: Long,
): Boolean = currentPositionMs > positionAfterSeekMs
