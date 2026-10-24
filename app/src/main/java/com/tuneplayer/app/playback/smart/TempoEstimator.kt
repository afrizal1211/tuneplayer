package com.tuneplayer.app.playback.smart

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Estimates a track's tempo in BPM and remembers the answer.
 *
 * Tempo is estimated once per track and cached, because the decode it needs
 * is the expensive part: a few seconds of decoded PCM is enough for a tempo
 * estimate, and no track's tempo changes between plays.
 *
 * Android-coupled (it decodes via ExoPlayer), so it lives outside
 * [TransitionPlanner] on purpose — the planner stays pure and testable.
 */
@UnstableApi
class TempoEstimator(private val context: Context) {

    /**
     * BPM for [mediaItem], or 0 when the track could not be decoded.
     *
     * Reads the first [DECODE_SECONDS] seconds of audio through a tee into a
     * buffer, then hands the buffer to the pure [TempoDetector].
     */
    suspend fun estimate(mediaItem: MediaItem, cacheKey: String): Double {
        cache[cacheKey]?.let { return it }
        val samples = decodeToPcm(mediaItem) ?: return 0.0
        val bpm = TempoDetector.estimateBpm(samples, SAMPLE_RATE)
        if (bpm > 0) cache[cacheKey] = bpm
        return bpm
    }

    private suspend fun decodeToPcm(mediaItem: MediaItem): FloatArray? =
        withContext(Dispatchers.IO) {
            val tee = TeeAudioProcessor(TeeAudioProcessor.AudioBufferSink { buffer ->
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                synchronized(pcmBuffer) { pcmBuffer.write(bytes) }
            })
            val player = ExoPlayer.Builder(context)
                .setAudioProcessors(tee)
                .build()
            try {
                val result = withTimeoutOrNull(DECODE_TIMEOUT_MS) {
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    // Decode only the head of the track, and only enough of it
                    // for a tempo estimate.
                    while (player.playbackState != Player.STATE_READY) {
                        kotlinx.coroutines.delay(50)
                    }
                    player.playWhenReady = false
                    val sink = ByteArrayOutputStream()
                    val targetBytes = SAMPLE_RATE * DECODE_SECONDS * PCM_BYTES_PER_FRAME
                    while (sink.size() < targetBytes &&
                        player.playbackState != Player.STATE_ENDED
                    ) {
                        synchronized(pcmBuffer) {
                            sink.write(pcmBuffer.toByteArray())
                            pcmBuffer.reset()
                        }
                        if (player.playbackState == Player.STATE_IDLE) break
                        kotlinx.coroutines.delay(20)
                    }
                    sink
                } ?: return@withContext null
                bytesToMono(result.toByteArray())
            } catch (_: PlaybackException) {
                null
            } finally {
                player.release()
            }
        }

    /** Interleaved stereo S16LE to mono floats, in the range -1..1. */
    private fun bytesToMono(bytes: ByteArray): FloatArray {
        val frames = min(bytes.size / PCM_BYTES_PER_FRAME, SAMPLE_RATE * DECODE_SECONDS)
        val out = FloatArray(frames)
        var index = 0
        for (frame in 0 until frames) {
            val base = frame * PCM_BYTES_PER_FRAME
            val low = bytes[base].toInt() and 0xFF
            val high = bytes[base + 1].toInt()
            val mono = (low or (high shl 8)).toFloat() / Short.MAX_VALUE
            out[index++] = mono
        }
        return out
    }

    private val pcmBuffer = ByteArrayOutputStream()

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val DECODE_SECONDS = 30
        const val DECODE_TIMEOUT_MS = 30_000L

        /** Stereo, 16-bit: two bytes per frame. */
        const val PCM_BYTES_PER_FRAME = 4

        val cache = mutableMapOf<String, Double>()
    }
}
