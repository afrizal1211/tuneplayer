package com.tuneplayer.app.playback.smart

import android.content.Context
import androidx.media3.common.MediaItem
import com.tuneplayer.app.playback.StreamHeaders
import androidx.media3.common.util.UnstableApi
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Estimates a track's tempo in BPM and remembers the answer.
 *
 * Tempo is estimated once per track and cached, because the decode it needs is
 * the expensive part. A few seconds of decoded PCM is enough for a tempo
 * estimate, and no track's tempo changes between plays.
 *
 * The decode is a two-step fetch-then-decode rather than a straight
 * [MediaExtractor] read of the stream URL, because the URL is signed against a
 * set of request headers and MediaExtractor has no way to send them. Fetching
 * the head of the stream with those headers into a scratch file first means
 * [MediaExtractor] only ever sees a local file, and a refusal from the server
 * surfaces as a null rather than as a wrong tempo.
 *
 * Android-coupled (it decodes), so it lives outside [TransitionPlanner] on
 * purpose — the planner stays pure and testable.
 */
@UnstableApi
class TempoEstimator(private val context: Context) {

    /**
     * BPM for [mediaItem], or 0 when the track could not be decoded.
     *
     * A decode that fails, times out, or produces silence answers 0, which the
     * planner already reads as "no evidence" and answers with the plain
     * crossfade.
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
            // The URL was signed against the headers captured at resolve time;
            // fetching it with anything else is refused, which would read as
            // "no tempo" and silently turn Automix off.
            val headers = StreamHeaders.forUri(uri.toString()) ?: emptyMap()
            val scratch = File(context.cacheDir, "tempo-${mediaItem.mediaId}.pcm")
            try {
                withTimeoutOrNull(DECODE_TIMEOUT_MS) {
                    if (!fetchHead(uri.toString(), headers, scratch)) return@withTimeoutOrNull null
                    decodeFile(scratch)
                }
            } finally {
                runCatching { scratch.delete() }
            }
        }

    /**
     * Writes the first [HEAD_BYTES] of [url] into [target], using the headers
     * the URL was signed against.
     *
     * A range request is used rather than a full GET: a tempo estimate needs a
     * few seconds of audio, not the whole track, and the rest of the bytes
     * would be thrown away anyway.
     */
    private fun fetchHead(url: String, headers: Map<String, String>, target: File): Boolean =
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = true
                headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                connection.setRequestProperty("Range", "bytes=0-${HEAD_BYTES - 1}")
                if (connection.responseCode !in 200..299) return false
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                }
            } finally {
                connection.disconnect()
            }
            target.length() > 0L
        }.getOrDefault(false)

    /** Decodes [file] to mono PCM16, or null when it carries no audio track. */
    private fun decodeFile(file: File): FloatArray? = runCatching {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            codec = MediaCodec.createDecoderByType(mime).also { decoder ->
                decoder.configure(format, null, null, 0)
                decoder.start()
            }

            val sink = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
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
            toMonoFloats(sink.toByteArray(), channels, SAMPLE_RATE)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }.getOrNull()

    /** Interleaved PCM16 to mono floats, trimmed to the decode budget. */
    private fun toMonoFloats(bytes: ByteArray, channels: Int, sampleRate: Int): FloatArray {
        val bytesPerFrame = channels * 2
        val frames = min(bytes.size / bytesPerFrame, sampleRate * DECODE_SECONDS)
        val out = FloatArray(frames)
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer()
        for (frame in 0 until frames) {
            var sum = 0.0
            for (channel in 0 until channels) {
                sum += shorts.get(frame * channels + channel).toDouble()
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

        /**
         * Enough head of the stream to cover the decode budget. A 128 kbps
         * track fills 30 s in about 480 KB; 2 MB leaves room for a higher
         * bitrate without downloading a whole song.
         */
        const val HEAD_BYTES = 2L * 1024L * 1024L

        val cache = mutableMapOf<String, Double>()
    }
}
