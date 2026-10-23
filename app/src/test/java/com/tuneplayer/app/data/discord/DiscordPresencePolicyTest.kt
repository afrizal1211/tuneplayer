package com.tuneplayer.app.data.discord

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural checks for the presence policy, mirroring the cases ArchiveTune
 * guards: hidden when disabled/tokenless/idle, held through buffering, and
 * resolved to hidden once a stall outlives the hold timeout.
 */
class DiscordPresencePolicyTest {

    private fun inputs(
        enabled: Boolean = true,
        hasToken: Boolean = true,
        trackId: String? = "t1",
        isPlaying: Boolean = true,
        showWhenPaused: Boolean = false,
        playWhenReady: Boolean = isPlaying,
        playbackState: Int = if (isPlaying) Player.STATE_READY else Player.STATE_IDLE,
    ) = DiscordPresenceInputs(
        enabled = enabled,
        hasToken = hasToken,
        trackId = trackId,
        isPlaying = isPlaying,
        showWhenPaused = showWhenPaused,
        playWhenReady = playWhenReady,
        playbackState = playbackState,
    )

    @Test
    fun `hidden when disabled`() {
        val decision = deriveDiscordPresenceDecision(inputs(enabled = false))
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.Disabled),
            decision,
        )
    }

    @Test
    fun `hidden when no token`() {
        val decision = deriveDiscordPresenceDecision(inputs(hasToken = false))
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.NoToken),
            decision,
        )
    }

    @Test
    fun `hidden when no song`() {
        val decision = deriveDiscordPresenceDecision(inputs(trackId = null))
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.NoSong),
            decision,
        )
    }

    @Test
    fun `playing is visible`() {
        val decision = deriveDiscordPresenceDecision(inputs())
        assertEquals(
            DiscordPresenceDecision.Visible("t1", PresenceMode.Playing),
            decision,
        )
    }

    @Test
    fun `paused hidden by preference`() {
        val decision =
            deriveDiscordPresenceDecision(
                inputs(
                    isPlaying = false,
                    showWhenPaused = false,
                    playWhenReady = false,
                    playbackState = Player.STATE_READY,
                ),
            )
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.PausedByPreference),
            decision,
        )
    }

    @Test
    fun `paused visible when preference set`() {
        val decision =
            deriveDiscordPresenceDecision(
                inputs(
                    isPlaying = false,
                    showWhenPaused = true,
                    playWhenReady = false,
                    playbackState = Player.STATE_READY,
                ),
            )
        assertEquals(
            DiscordPresenceDecision.Visible("t1", PresenceMode.Paused),
            decision,
        )
    }

    @Test
    fun `paused hidden when dismissed from notification`() {
        val decision =
            deriveDiscordPresenceDecision(
                inputs(
                    isPlaying = false,
                    showWhenPaused = true,
                    playWhenReady = false,
                    playbackState = Player.STATE_READY,
                ).copy(pausedPresenceGate = PausedPresenceGate.HiddenByNotificationDismiss),
            )
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.PausedByNotificationDismiss),
            decision,
        )
    }

    @Test
    fun `buffering holds while play requested`() {
        // A hold only survives a single-shot derive when a card is already up;
        // otherwise there is nothing to keep and it hides at once.
        val raw =
            deriveRawDiscordPresenceDecision(
                input =
                    inputs(
                        isPlaying = false,
                        playWhenReady = true,
                        playbackState = Player.STATE_BUFFERING,
                    ),
                semanticState = PlaybackSemanticState.BufferingWhilePlayRequested,
            )
        assertTrue(raw is DiscordPresenceDecision.Hold)
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.NoStablePlaybackYet),
            resolveDiscordPresenceDecision(
                rawDecision = raw,
                holdContext =
                    DiscordHoldContext(
                        nowMs = 0L,
                        activeHoldState = null,
                        lastAppliedVisiblePresence = null,
                        holdTimeoutMs = 8_000L,
                    ),
            ).decision,
        )
    }

    @Test
    fun `buffering keeps last visible card inside hold timeout`() {
        val hold =
            DiscordPresenceResolution(
                decision = DiscordPresenceDecision.Visible("t1", PresenceMode.Playing),
                nextHoldState = null,
            )
        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Hold(
                        HoldReason.BufferingWhilePlayRequested,
                        "t1",
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 1_000L,
                        activeHoldState = null,
                        lastAppliedVisiblePresence =
                            LastAppliedVisiblePresence("t1", PresenceMode.Playing),
                        holdTimeoutMs = 8_000L,
                    ),
            )
        assertTrue(resolution.decision is DiscordPresenceDecision.Hold)
        assertEquals(hold.decision, hold.decision)
    }

    @Test
    fun `buffering degrades to hidden once hold expires`() {
        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Hold(
                        HoldReason.BufferingWhilePlayRequested,
                        "t1",
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 10_000L,
                        activeHoldState =
                            ActiveHoldState(
                                HoldReason.BufferingWhilePlayRequested,
                                "t1",
                                1_000L,
                            ),
                        lastAppliedVisiblePresence =
                            LastAppliedVisiblePresence("t1", PresenceMode.Playing),
                        holdTimeoutMs = 8_000L,
                    ),
            )
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.PlaybackStalled),
            resolution.decision,
        )
    }

    @Test
    fun `buffering without prior card hides immediately`() {
        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Hold(
                        HoldReason.BufferingWhilePlayRequested,
                        "t1",
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 0L,
                        activeHoldState = null,
                        lastAppliedVisiblePresence = null,
                        holdTimeoutMs = 8_000L,
                    ),
            )
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.NoStablePlaybackYet),
            resolution.decision,
        )
    }

    @Test
    fun `idle with play requested hides`() {
        // playWhenReady true + IDLE/ENDED means the stream died, not a pause.
        val decision =
            deriveDiscordPresenceDecision(
                inputs(
                    isPlaying = false,
                    playWhenReady = true,
                    playbackState = Player.STATE_ENDED,
                ),
            )
        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.PlaybackStalled),
            decision,
        )
    }
}
