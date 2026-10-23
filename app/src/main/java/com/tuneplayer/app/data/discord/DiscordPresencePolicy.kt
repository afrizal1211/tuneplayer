package com.tuneplayer.app.data.discord

import androidx.media3.common.Player

/** Why the paused-presence card is currently suppressed. */
enum class PausedPresenceGate {
    FollowPreference,
    HiddenByNotificationDismiss,
}

enum class PresenceMode {
    Playing,
    Paused,
}

enum class PlaybackSemanticState {
    PlayingReady,
    PausedByUser,
    BufferingWhilePlayRequested,
    Inactive,
}

enum class HiddenReason {
    Disabled,
    NoToken,
    NoSong,
    PausedByPreference,
    PausedByNotificationDismiss,
    ServiceStopping,
    NoStablePlaybackYet,
    PlaybackStalled,
}

enum class HoldReason {
    BufferingWhilePlayRequested,
}

sealed interface DiscordPresenceDecision {
    data class Visible(
        val trackId: String,
        val mode: PresenceMode,
    ) : DiscordPresenceDecision {
        val isPaused: Boolean
            get() = mode == PresenceMode.Paused
    }

    data class Hidden(
        val reason: HiddenReason,
    ) : DiscordPresenceDecision

    data class Hold(
        val reason: HoldReason,
        val trackId: String?,
    ) : DiscordPresenceDecision
}

data class ActiveHoldState(
    val reason: HoldReason,
    val trackId: String?,
    val startedAtMs: Long,
)

data class LastAppliedVisiblePresence(
    val trackId: String,
    val mode: PresenceMode,
)

data class DiscordHoldContext(
    val nowMs: Long,
    val activeHoldState: ActiveHoldState?,
    val lastAppliedVisiblePresence: LastAppliedVisiblePresence?,
    val holdTimeoutMs: Long,
)

data class DiscordPresenceResolution(
    val decision: DiscordPresenceDecision,
    val nextHoldState: ActiveHoldState?,
)

data class DiscordPresenceInputs(
    val enabled: Boolean,
    val hasToken: Boolean,
    val trackId: String?,
    val isPlaying: Boolean,
    val showWhenPaused: Boolean,
    val pausedPresenceGate: PausedPresenceGate = PausedPresenceGate.FollowPreference,
    val serviceStopping: Boolean = false,
    val playWhenReady: Boolean = isPlaying,
    val playbackState: Int = if (isPlaying) Player.STATE_READY else Player.STATE_IDLE,
)

/**
 * Decides what the Discord card should show for a given playback snapshot.
 *
 * Pure: no clock, no network, no Android. [DiscordHoldContext] carries the
 * state that depends on the clock so buffering can keep the last good card
 * without making the policy itself time-dependent.
 */
fun derivePlaybackSemanticState(input: DiscordPresenceInputs): PlaybackSemanticState {
    if (input.trackId == null) {
        return PlaybackSemanticState.Inactive
    }
    if (!input.playWhenReady) {
        return PlaybackSemanticState.PausedByUser
    }

    return when (input.playbackState) {
        Player.STATE_READY -> {
            if (input.isPlaying) {
                PlaybackSemanticState.PlayingReady
            } else {
                PlaybackSemanticState.BufferingWhilePlayRequested
            }
        }

        Player.STATE_BUFFERING -> PlaybackSemanticState.BufferingWhilePlayRequested

        Player.STATE_IDLE,
        Player.STATE_ENDED,
        -> PlaybackSemanticState.Inactive

        else -> PlaybackSemanticState.Inactive
    }
}

fun deriveRawDiscordPresenceDecision(
    input: DiscordPresenceInputs,
    semanticState: PlaybackSemanticState,
): DiscordPresenceDecision {
    if (input.serviceStopping) {
        return DiscordPresenceDecision.Hidden(HiddenReason.ServiceStopping)
    }
    if (!input.enabled) {
        return DiscordPresenceDecision.Hidden(HiddenReason.Disabled)
    }
    if (!input.hasToken) {
        return DiscordPresenceDecision.Hidden(HiddenReason.NoToken)
    }

    val trackId = input.trackId ?: return DiscordPresenceDecision.Hidden(HiddenReason.NoSong)

    return when (semanticState) {
        PlaybackSemanticState.PlayingReady -> {
            DiscordPresenceDecision.Visible(
                trackId = trackId,
                mode = PresenceMode.Playing,
            )
        }

        PlaybackSemanticState.PausedByUser -> {
            when {
                !input.showWhenPaused -> {
                    DiscordPresenceDecision.Hidden(HiddenReason.PausedByPreference)
                }

                input.pausedPresenceGate == PausedPresenceGate.HiddenByNotificationDismiss -> {
                    DiscordPresenceDecision.Hidden(HiddenReason.PausedByNotificationDismiss)
                }

                else -> {
                    DiscordPresenceDecision.Visible(
                        trackId = trackId,
                        mode = PresenceMode.Paused,
                    )
                }
            }
        }

        PlaybackSemanticState.BufferingWhilePlayRequested -> {
            DiscordPresenceDecision.Hold(
                reason = HoldReason.BufferingWhilePlayRequested,
                trackId = trackId,
            )
        }

        PlaybackSemanticState.Inactive -> {
            DiscordPresenceDecision.Hidden(HiddenReason.PlaybackStalled)
        }
    }
}

/**
 * Resolves a [DiscordPresenceDecision.Hold] against the last applied card.
 *
 * A hold keeps the last visible presence while the same track buffers, then
 * degrades to hidden once the stall outlives [DiscordHoldContext.holdTimeoutMs].
 * Without a previous visible card there is nothing to keep, so it hides at once.
 */
fun resolveDiscordPresenceDecision(
    rawDecision: DiscordPresenceDecision,
    holdContext: DiscordHoldContext,
): DiscordPresenceResolution =
    when (rawDecision) {
        is DiscordPresenceDecision.Visible -> {
            DiscordPresenceResolution(
                decision = rawDecision,
                nextHoldState = null,
            )
        }

        is DiscordPresenceDecision.Hidden -> {
            DiscordPresenceResolution(
                decision = rawDecision,
                nextHoldState = null,
            )
        }

        is DiscordPresenceDecision.Hold -> {
            val lastAppliedVisible = holdContext.lastAppliedVisiblePresence
                ?: return DiscordPresenceResolution(
                    decision = DiscordPresenceDecision.Hidden(HiddenReason.NoStablePlaybackYet),
                    nextHoldState = null,
                )

            val activeHold = holdContext.activeHoldState
            val isSameHold =
                activeHold != null &&
                    activeHold.reason == rawDecision.reason &&
                    activeHold.trackId == rawDecision.trackId

            if (isSameHold) {
                val elapsedMs = holdContext.nowMs - activeHold.startedAtMs
                if (elapsedMs >= holdContext.holdTimeoutMs) {
                    return DiscordPresenceResolution(
                        decision = DiscordPresenceDecision.Hidden(HiddenReason.PlaybackStalled),
                        nextHoldState = null,
                    )
                }

                return DiscordPresenceResolution(
                    decision = rawDecision,
                    nextHoldState = activeHold,
                )
            }

            DiscordPresenceResolution(
                decision = rawDecision,
                nextHoldState =
                    ActiveHoldState(
                        reason = rawDecision.reason,
                        trackId = rawDecision.trackId ?: lastAppliedVisible.trackId,
                        startedAtMs = holdContext.nowMs,
                    ),
            )
        }
    }

/** Single-shot convenience for callers that keep no hold state. */
fun deriveDiscordPresenceDecision(input: DiscordPresenceInputs): DiscordPresenceDecision =
    resolveDiscordPresenceDecision(
        rawDecision =
            deriveRawDiscordPresenceDecision(
                input = input,
                semanticState = derivePlaybackSemanticState(input),
            ),
        holdContext =
            DiscordHoldContext(
                nowMs = 0L,
                activeHoldState = null,
                lastAppliedVisiblePresence = null,
                holdTimeoutMs = 0L,
            ),
    ).decision
