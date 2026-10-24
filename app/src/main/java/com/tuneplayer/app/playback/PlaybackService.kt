package com.tuneplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tuneplayer.app.TunePlayerApp
import com.tuneplayer.app.data.prefs.SettingsStore
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.playback.smart.TempoEstimator
import com.tuneplayer.app.playback.smart.TransitionPlanner
import com.tuneplayer.app.playback.smart.TransitionTrackInfo
import com.tuneplayer.app.ui.AudioEffects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keys shared between the ViewModel that builds the queue and the playback
 * service that reads it.
 */
object PlaybackKeys {
    /**
     * MediaItem extras key carrying the track's duration in milliseconds.
     * Crossfade planning needs the incoming track's length before the service
     * has prepared it, and ExoPlayer only knows the length once it has.
     */
    const val EXTRA_TRACK_DURATION_MS = "com.tuneplayer.app.TRACK_DURATION_MS"
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var secondaryPlayer: ExoPlayer? = null
    private var crossfadeJob: Job? = null
    private var crossfadeTriggerJob: Job? = null
    private var isCrossfading = false
    private var crossfadeTargetIndex = C.INDEX_UNSET
    private var crossfadeTargetMediaId: String? = null
    private var crossfadeOutgoingMediaId: String? = null
    private var crossfadeBaseVolume = 1f
    private var crossfadePlaybackRequested = false

    private val settingsStore: SettingsStore
        get() = (application as TunePlayerApp).settingsStore

    /**
     * Effects live here, not in the UI: the ExoPlayer session belongs to this
     * service and keeps playing while the activity is gone. The UI only writes
     * preferences; this listener applies them to the live session.
     */
    private val audioSettingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyAudioSettings() }

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            applyAudioSettings()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            scheduleCrossfade()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) scheduleCrossfade()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) scheduleCrossfade()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // A previous service instance may have died without releasing its
        // effects. Start from a clean slate; sessions can be reused.
        AudioEffects.release()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        this.player = player
        player.addListener(playerListener)
        settingsStore.registerListener(audioSettingsListener)
        applyAudioSettings()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, com.tuneplayer.app.MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    /**
     * The media source both the session player and the crossfade peer read
     * from: HTTP with YouTube's headers, plus content://, file:// and
     * https:// for local files.
     */
    private fun createMediaSourceFactory(): DefaultMediaSourceFactory {
        val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            "Referer" to "https://music.youtube.com/",
            "Origin" to "https://music.youtube.com",
            "Accept-Language" to "en-US,en;q=0.9",
        )
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(defaultHeaders)
            .setAllowCrossProtocolRedirects(true)
        return DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))
    }

    /**
     * Reads the shared settings and pushes them into the live player.
     *
     * Called after the session is created, when the platform changes the
     * audio session, and whenever the UI writes a preference. Calls before
     * the audio session exists are harmless: [AudioEffects.apply] releases
     * and waits for the next session callback.
     */
    private fun applyAudioSettings() {
        val player = player ?: return
        val settings = settingsStore
        player.skipSilenceEnabled = settings.skipSilence
        AudioEffects.apply(
            sessionId = player.audioSessionId,
            enabled = settings.equalizerEnabled,
            bass = settings.bassBoostLevel,
            virtualizerLevel = settings.virtualizerLevel,
            bands = settings.eqBands,
        )
    }

    // ---- Crossfade ----------------------------------------------------------

    /**
     * Arms the next transition once the current track is inside the blend
     * window of its end.
     *
     * A second ExoPlayer is prepared with the incoming track so both are
     * audible at once; one player can only fade to silence, which is a dip,
     * not a crossfade. The handoff then seeks the session player onto the
     * incoming track at the secondary's own position and reverse-ramps, so
     * the queue, shuffle and repeat keep working on the player the session
     * and notification already point at.
     */
    private fun scheduleCrossfade() {
        val player = player ?: return
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        if (isCrossfading) return
        if (!settingsStore.crossfadeEnabled || settingsStore.crossfadeDurationSeconds <= 0f) return
        if (!player.playWhenReady || player.mediaItemCount == 0) return

        val durationMs = settingsStore.crossfadeDurationMs
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return

        val targetIndex = player.nextMediaItemIndex
        if (targetIndex == C.INDEX_UNSET || targetIndex !in 0 until player.mediaItemCount) return
        val target = player.getMediaItemAt(targetIndex)

        val outgoingMediaId = player.currentMediaItem?.mediaId ?: return
        val currentItem = player.currentMediaItem ?: return

        crossfadeTriggerJob = scope.launch {
            // Automix plans the blend from each track's tempo, which needs a
            // decode. It runs here, before the trigger point is fixed, and the
            // plain crossfade is used whenever it cannot answer.
            val automix = settingsStore.automixEnabled
            val blendMs = if (automix) {
                val tempoEstimator = TempoEstimator(this@PlaybackService)
                val outgoingBpm = tempoEstimator.estimate(currentItem, outgoingMediaId)
                val incomingBpm = tempoEstimator.estimate(target, target.mediaId)
                val plan = TransitionPlanner.planTransition(
                    current = TransitionTrackInfo(
                        id = outgoingMediaId,
                        durationMs = duration,
                    ),
                    next = TransitionTrackInfo(
                        id = target.mediaId,
                        // Duration travels in the item's extras because the
                        // service cannot read the real length until it has
                        // prepared the incoming track.
                        durationMs =
                            target.mediaMetadata.extras?.getLong(PlaybackKeys.EXTRA_TRACK_DURATION_MS) ?: 0L,
                    ),
                    currentBpm = outgoingBpm,
                    nextBpm = incomingBpm,
                    currentTime = player.currentPosition / 1000.0,
                    duration = duration / 1000.0,
                    fadeSeconds = durationMs / 1000.0,
                )
                if (plan.shouldStart && !plan.blocked) plan.fadeMs else durationMs
            } else {
                durationMs
            }

            val triggerAt = duration - blendMs - CROSSFADE_END_GUARD_MS
            var prepared = false
            while (isActive) {
                if (!settingsStore.crossfadeEnabled || isCrossfading) return@launch
                if (player.currentMediaItem?.mediaId != outgoingMediaId) return@launch
                if (player.playbackState == Player.STATE_IDLE ||
                    player.playbackState == Player.STATE_ENDED
                ) {
                    return@launch
                }

                val remaining = triggerAt - player.currentPosition
                if (!prepared && remaining <= CROSSFADE_PREPARE_AHEAD_MS) {
                    prepareSecondaryPlayer(target)
                    prepared = true
                }
                if (remaining <= 0L) {
                    val adjusted = (duration - player.currentPosition - CROSSFADE_END_GUARD_MS)
                        .coerceAtMost(blendMs)
                    if (adjusted >= MIN_CROSSFADE_MS) {
                        startCrossfade(targetIndex, target.mediaId, adjusted)
                    }
                    return@launch
                }

                val sleep = when {
                    remaining > 5_000L -> 1_000L
                    remaining > 1_000L -> 250L
                    else -> 50L
                }.coerceAtMost(remaining).coerceAtLeast(1L)
                delay(sleep)
            }
        }
    }

    /** Builds the idle peer and loads the incoming track onto it, silent. */
    private fun prepareSecondaryPlayer(target: MediaItem): ExoPlayer? {
        releaseSecondaryPlayer()
        val secondary = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        secondary.setMediaItem(target)
        secondary.playbackParameters = player?.playbackParameters ?: secondary.playbackParameters
        secondary.volume = 0f
        secondary.prepare()
        secondaryPlayer = secondary
        return secondary
    }

    private fun startCrossfade(targetIndex: Int, targetMediaId: String, durationMs: Long) {
        val player = player ?: return
        val secondary = secondaryPlayer ?: return
        if (isCrossfading) return

        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            isCrossfading = true
            crossfadeTargetIndex = targetIndex
            crossfadeTargetMediaId = targetMediaId
            crossfadeOutgoingMediaId = player.currentMediaItem?.mediaId
            crossfadeBaseVolume = player.volume.takeIf { it > 0f } ?: 1f
            crossfadePlaybackRequested = player.playWhenReady

            try {
                if (!awaitSecondaryReady(secondary)) {
                    cancelCrossfade(resetVolume = true)
                    return@launch
                }
                if (player.currentMediaItem?.mediaId != crossfadeOutgoingMediaId) {
                    cancelCrossfade(resetVolume = true)
                    return@launch
                }
                val remaining = (player.duration - player.currentPosition - CROSSFADE_END_GUARD_MS)
                    .coerceIn(MIN_CROSSFADE_MS, durationMs)

                secondary.playWhenReady = crossfadePlaybackRequested
                if (crossfadePlaybackRequested) secondary.play()

                var elapsed = 0L
                var lastTick = SystemClock.elapsedRealtime()
                while (isActive && elapsed < remaining) {
                    if (player.currentMediaItem?.mediaId != crossfadeOutgoingMediaId) {
                        cancelCrossfade(resetVolume = true)
                        return@launch
                    }
                    val now = SystemClock.elapsedRealtime()
                    elapsed = (elapsed + (now - lastTick)).coerceAtMost(remaining)
                    val progress = elapsed.toFloat() / remaining.toFloat()
                    applyCrossfadeVolumes(progress, player, secondary)
                    lastTick = now
                    delay(CROSSFADE_FRAME_MS)
                }
                finishCrossfade(secondary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogCatcher.w(TAG, "crossfade failed: ${error.message}")
                cancelCrossfade(resetVolume = true)
            }
        }
    }

    private suspend fun CoroutineScope.awaitSecondaryReady(secondary: ExoPlayer): Boolean {
        val deadline = SystemClock.elapsedRealtime() + CROSSFADE_READY_TIMEOUT_MS
        while (isActive && SystemClock.elapsedRealtime() < deadline) {
            when (secondary.playbackState) {
                Player.STATE_READY -> return true
                Player.STATE_IDLE -> secondary.prepare()
                Player.STATE_ENDED -> return false
                else -> Unit
            }
            delay(50L)
        }
        return secondary.playbackState == Player.STATE_READY
    }

    /**
     * Moves the session onto the incoming track and reverse-ramps the pair.
     *
     * The primary is silenced and seeked to the secondary's live position, so
     * the two are aligned; the pair then fades the other way over
     * [CROSSFADE_HANDOFF_MS] and the secondary is released.
     */
    private suspend fun CoroutineScope.finishCrossfade(secondary: ExoPlayer) {
        val player = player ?: return
        val targetIndex = crossfadeTargetIndex
        if (targetIndex == C.INDEX_UNSET) {
            cancelCrossfade(resetVolume = true)
            return
        }
        val incomingPosition = secondary.currentPosition.coerceAtLeast(0L)
        val shouldContinue = crossfadePlaybackRequested

        player.volume = 0f
        player.seekTo(targetIndex, incomingPosition)
        player.playWhenReady = shouldContinue

        if (!shouldContinue) {
            secondary.pause()
            finishCrossfadeCleanup()
            return
        }

        val readyDeadline = SystemClock.elapsedRealtime() + CROSSFADE_HANDOFF_TIMEOUT_MS
        while (isActive && SystemClock.elapsedRealtime() < readyDeadline) {
            if (player.playbackState == Player.STATE_READY &&
                player.currentMediaItemIndex == targetIndex
            ) {
                break
            }
            if (player.playbackState == Player.STATE_IDLE ||
                player.playbackState == Player.STATE_ENDED
            ) {
                break
            }
            delay(25L)
        }

        if (player.currentMediaItemIndex == targetIndex &&
            player.playbackState == Player.STATE_READY
        ) {
            if (needsCorrectiveCrossfadeSeek(
                    primaryPositionMs = player.currentPosition,
                    secondaryPositionMs = secondary.currentPosition,
                    maximumDriftMs = CROSSFADE_MAX_DRIFT_MS,
                )
            ) {
                player.seekTo(targetIndex, secondary.currentPosition.coerceAtLeast(0L))
            }

            var elapsed = 0L
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive && elapsed < CROSSFADE_HANDOFF_MS) {
                val now = SystemClock.elapsedRealtime()
                elapsed = (elapsed + (now - lastTick)).coerceAtMost(CROSSFADE_HANDOFF_MS)
                val progress = elapsed.toFloat() / CROSSFADE_HANDOFF_MS.toFloat()
                applyCrossfadeVolumes(progress, secondary, player)
                lastTick = now
                delay(CROSSFADE_FRAME_MS)
            }
        }

        finishCrossfadeCleanup()
        scheduleCrossfade()
    }

    /**
     * Applies the equal-power pair.
     *
     * Both arguments are in playback order — the outgoing player first — so
     * the same call drives both the forward blend and the reverse handoff.
     */
    private fun applyCrossfadeVolumes(progress: Float, outgoing: ExoPlayer, incoming: ExoPlayer) {
        val gains = equalPowerGains(progress)
        outgoing.volume = (crossfadeBaseVolume * gains.outgoing).coerceIn(0f, 1f)
        incoming.volume = (crossfadeBaseVolume * gains.incoming).coerceIn(0f, 1f)
    }

    private fun finishCrossfadeCleanup() {
        val player = player ?: return
        player.volume = crossfadeBaseVolume.coerceIn(0f, 1f)
        isCrossfading = false
        crossfadeTargetIndex = C.INDEX_UNSET
        crossfadeTargetMediaId = null
        crossfadeOutgoingMediaId = null
        crossfadePlaybackRequested = false
        crossfadeJob = null
        releaseSecondaryPlayer()
    }

    private fun cancelCrossfade(resetVolume: Boolean) {
        val player = player
        if (resetVolume && player != null) {
            player.volume = crossfadeBaseVolume.coerceIn(0f, 1f)
        }
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossfading = false
        crossfadeTargetIndex = C.INDEX_UNSET
        crossfadeTargetMediaId = null
        crossfadeOutgoingMediaId = null
        crossfadePlaybackRequested = false
        releaseSecondaryPlayer()
    }

    private fun releaseSecondaryPlayer() {
        secondaryPlayer?.release()
        secondaryPlayer = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        settingsStore.unregisterListener(audioSettingsListener)
        player?.removeListener(playerListener)
        cancelCrossfade(resetVolume = false)
        scope.cancel()
        AudioEffects.release()
        mediaSession?.run {
            player.release()
            release()
        }
        player = null
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Crossfade"

        /** Leave a sliver of the outgoing track audible after the blend. */
        const val CROSSFADE_END_GUARD_MS = 250L

        /** Build the secondary player this far before the blend opens. */
        const val CROSSFADE_PREPARE_AHEAD_MS = 2_000L

        /** Shortest blend worth performing; anything less is a hard cut. */
        const val MIN_CROSSFADE_MS = 1_000L

        const val CROSSFADE_FRAME_MS = 50L
        const val CROSSFADE_READY_TIMEOUT_MS = 5_000L

        /** Reverse ramp after the primary takes over the incoming track. */
        const val CROSSFADE_HANDOFF_MS = 500L
        const val CROSSFADE_HANDOFF_TIMEOUT_MS = 3_000L

        /** Drift the two decoders may carry before a corrective seek. */
        const val CROSSFADE_MAX_DRIFT_MS = 75L
    }
}
