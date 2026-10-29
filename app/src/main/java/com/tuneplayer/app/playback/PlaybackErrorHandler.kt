package com.tuneplayer.app.playback

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.tuneplayer.app.TunePlayerApp
import com.tuneplayer.app.data.model.YouTubeAudioQuality
import com.tuneplayer.app.log.LogCatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Recovers playback after a player error.
 *
 * YouTube stream URLs are signed and short-lived. The most common failure
 * mid-queue is an expired URL (HTTP 403), which the session cannot fix on
 * its own: the item stays failed and playback stalls. The handler resolves
 * a fresh URL for the current item and swaps it in place at the same
 * position; if re-resolution fails or the same item keeps failing, it
 * advances so the queue keeps moving. Local items are left to the UI —
 * re-resolution cannot fix a missing file.
 */
internal class PlaybackErrorHandler(
    private val service: Context,
    private val playerRef: () -> ExoPlayer?,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastFailedMediaId: String? = null
    private var attempts = 0
    private var job: Job? = null

    fun handle(error: PlaybackException) {
        val player = playerRef() ?: return
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        // Local files and downloads fail for reasons re-resolution cannot
        // fix (renamed file, revoked permission). Let the UI surface those.
        val videoId = mediaId.removePrefix(YT_ID_PREFIX)
        if (videoId == mediaId) return
        if (mediaId == lastFailedMediaId && attempts >= MAX_ATTEMPTS_PER_ITEM) {
            LogCatcher.w(TAG, "giving up on $mediaId after $attempts attempts; skipping")
            skipForward(player)
            return
        }
        if (mediaId != lastFailedMediaId) attempts = 0
        attempts += 1
        lastFailedMediaId = mediaId
        LogCatcher.w(TAG, "player error on $mediaId (${error.errorCodeName}); re-resolving")
        job?.cancel()
        job = scope.launch {
            val repository = (service.applicationContext as TunePlayerApp).youTubeMusicRepository
            val quality = (service.applicationContext as TunePlayerApp)
                .settingsStore.youtubeAudioQuality
            val resolved = runCatching {
                repository.resolveAudio(videoId = videoId, quality = quality)
            }.onFailure { LogCatcher.w(TAG, "re-resolve failed for $videoId", it) }
                .getOrNull()
            // The user may have moved on while the resolve ran.
            if (player.currentMediaItem?.mediaId != mediaId) return@launch
            if (resolved == null) {
                skipForward(player)
                return@launch
            }
            // Replace in place at the current index: setMediaItem() would
            // wipe the whole playlist, killing the queue.
            val position = player.currentPosition.coerceAtLeast(0L)
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                item.buildUpon()
                    .setUri(resolved.url)
                    .setMimeType(resolved.mimeType)
                    .build(),
            )
            player.seekTo(position)
            player.prepare()
            player.play()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        scope.cancel()
    }

    private fun skipForward(player: ExoPlayer) {
        player.seekToNextMediaItem()
        if (!player.playWhenReady) player.prepare()
    }

    companion object {
        private const val TAG = "PlaybackErrors"

        /** Stream URLs expire; one fresh mint usually fixes it. */
        private const val MAX_ATTEMPTS_PER_ITEM = 2

        /** Player media ids for YouTube tracks start with this prefix. */
        private const val YT_ID_PREFIX = "yt:"
    }
}
