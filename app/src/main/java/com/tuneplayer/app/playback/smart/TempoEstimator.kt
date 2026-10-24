package com.tuneplayer.app.playback.smart

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Estimates a track's tempo in BPM and remembers the answer.
 *
 * Tempo is estimated once per track and cached, because the decode it needs
 * is the expensive part. A few seconds of decoded PCM is enough for a tempo
 * estimate, and no track's tempo changes between plays.
 *
 * Android-coupled (it decodes), so it lives outside [TransitionPlanner] on
 * purpose — the planner stays pure and testable.
 */
@UnstableApi
class TempoEstimator(private val context: Context) {

    /**
     * BPM for [mediaItem], or 0 when the track could not be decoded.
     *
     * Reads the head of the audio through [MediaCodec] and hands the buffer to
     * the pure [TempoDetector]. A decode that fails, times out, or produces
     * silence answers 0, which the planner already reads as "no evidence" and
     * answers with the plain crossfade.
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
            val uri = mediaItem.localConfiguration?.uri ?: return@withContext null
            withTimeoutOrNull(DECODE_TIMEOUT_MS) {
                runCatching {
                    val extractor = MediaExtractor()
                    var codec: MediaCodec? = null
                    try {
                        extractor.setDataSource(context, uri, null)
                        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                            extractor.getTrackFormat(index)
                                .getString(MediaFormat.KEY_MIME)
                                ?.startsWith("audio/") == true
                        } ?: return@runCatching null
                        extractor.selectTrack(trackIndex)
                        val format = extractor.getTrackFormat(trackIndex)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: return@runCatching null
                        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        codec = MediaCodec.createDecoderByType(mime).also { decoder ->
                            decoder.configure(format, null, null, 0)
                            decoder.start()
                        }

                        val sink = ByteArrayOutputStream()
                        val targetBytes = SAMPLE_RATE * DECODE_SECONDS * channels * 2
                        val info = MediaCodec.BufferInfo()
                        var sawInputEos = false
                        var sawOutputEos = false

                        while (sink.size() < targetBytes && !sawOutputEos) {
                            if (!sawInputEos) {
                                val inputIndex = codec!!.dequeueInputBuffer(TIMEOUT_US)
                                if (inputIndex >= 0) {
                                    val buffer = codec!!.getInputBuffer(inputIndex)!!
                                    val size = extractor.readSampleData(buffer, 0)
                                    if (size < 0) {
                                        codec!!.queueInputBuffer(
                                            inputIndex, 0, 0, 0,
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                        )
                                        sawInputEos = true
                                    } else {
                                        codec!!.queueInputBuffer(
                                            inputIndex, 0, size, extractor.sampleTime, 0,
                                        )
                                        extractor.advance()
                                    }
                                }
                            }
                            val outputIndex = codec!!.dequeueOutputBuffer(info, TIMEOUT_US)
                            if (outputIndex >= 0) {
                                val buffer = codec!!.getOutputBuffer(outputIndex)
                                if (buffer != null && info.size > 0) {
                                    val chunk = ByteArray(info.size)
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    buffer.get(chunk)
                                    sink.write(chunk)
                                }
                                codec!!.releaseOutputBuffer(outputIndex, false)
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    sawOutputEos = true
                                }
                            }
                        }
                        toMonoFloats(sink.toByteArray(), channels, sampleRate)
                    } finally {
                        runCatching { codec?.stop() }
                        runCatching { codec?.release() }
                        runCatching { extractor.release() }
                    }
                }.getOrNull()
            }
        }

    /** Interleaved PCM16 to mono floats, trimmed to the decode budget. */
    private fun toMonoFloats(bytes: ByteArray, channels: Int, sampleRate: Int): FloatArray {
        val bytesPerFrame = channels * 2
        val frames = min(bytes.size / bytesPerFrame, SAMPLE_RATE * DECODE_SECONDS)
        val out = FloatArray(frames)
        val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer()
        for (frame in 0 until frames) {
            var sum = 0.0
            for (channel in 0 until channels) {
                sum += shortBuffer.get(frame * channels + channel).toDouble()
            }
            out[frame] = (sum / channels / Short.MAX_VALUE).toFloat()
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val DECODE_SECONDS = 30
        const val DECODE_TIMEOUT_MS = 20_000L
        const val TIMEOUT_US = 10_000L

        val cache = mutableMapOf<String, Double>()
    }
}
