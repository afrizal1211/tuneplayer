package com.tuneplayer.app.data.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.model.YouTubeAudioQuality
import com.tuneplayer.app.data.youtube.YouTubeMusicRepository
import com.tuneplayer.app.log.LogCatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** A completed audio file plus the metadata needed to play it again offline. */
data class DownloadedTrack(
    val track: Track,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val downloadedAt: Long,
)

data class OfflineDownloadsState(
    val downloads: Map<String, DownloadedTrack> = emptyMap(),
    val progress: Map<String, Float> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
) {
    val downloadedIds: Set<String>
        get() = downloads.keys
}

/**
 * Downloads YouTube audio into app-specific external storage.
 *
 * The files stay private to TunePlayer and need no storage permission. A
 * completed download is addressed by the original track id, so search results,
 * favourites, and the queue keep working after the stream URL expires.
 */
class OfflineDownloadManager(
    context: Context,
    private val youTubeRepository: YouTubeMusicRepository,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = HttpClient(OkHttp)
    private val jobs = mutableMapOf<String, Job>()

    private val downloadsDir: File
        get() = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: appContext.filesDir,
            DIRECTORY,
        )

    private val _state = MutableStateFlow(
        OfflineDownloadsState(downloads = readDownloads()),
    )
    val state = _state.asStateFlow()

    init {
        val existing = _state.value.downloads
        val valid = existing.filterValues { fileFor(it).isFile }
        if (valid.size != existing.size) {
            _state.value = _state.value.copy(downloads = valid)
            persist(valid)
        }
    }

    fun download(track: Track, quality: YouTubeAudioQuality) {
        if (track.isLocal || track.videoId.isNullOrBlank()) return
        synchronized(jobs) {
            if (_state.value.downloads.containsKey(track.id) || jobs.containsKey(track.id)) return
            val job = scope.launch { performDownload(track, quality) }
            jobs[track.id] = job
            job.invokeOnCompletion {
                synchronized(jobs) {
                    if (jobs[track.id] === job) jobs.remove(track.id)
                }
            }
        }
    }

    fun remove(trackId: String) {
        val job = synchronized(jobs) { jobs.remove(trackId) }
        job?.cancel()
        synchronized(this) {
            val entry = _state.value.downloads[trackId]
            if (entry != null) {
                runCatching { fileFor(entry).delete() }
            }
            _state.value = _state.value.copy(
                downloads = _state.value.downloads - trackId,
                progress = _state.value.progress - trackId,
                errors = _state.value.errors - trackId,
            )
            persist(_state.value.downloads)
        }
    }

    fun clearAll() {
        val entries = _state.value.downloads.values.toList()
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        synchronized(this) {
            entries.forEach { runCatching { fileFor(it).delete() } }
            _state.value = OfflineDownloadsState()
            persist(emptyMap())
        }
    }

    /** Original metadata with a local file URI, ready for the player. */
    fun downloadedTracks(): List<Track> = _state.value.downloads.values
        .sortedByDescending { it.downloadedAt }
        .map { entry ->
            entry.track.copy(mediaUri = Uri.fromFile(fileFor(entry)).toString())
        }

    fun localUri(trackId: String): String? {
        val entry = _state.value.downloads[trackId] ?: return null
        val file = fileFor(entry)
        return if (file.isFile) Uri.fromFile(file).toString() else null
    }

    fun totalBytes(): Long = _state.value.downloads.values.sumOf { it.sizeBytes }

    private suspend fun performDownload(track: Track, quality: YouTubeAudioQuality) {
        val videoId = track.videoId ?: return
        setProgress(track.id, 0f)
        var tempFile: File? = null
        try {
            val audio = youTubeRepository.resolveAudio(videoId, quality)
            val extension = extensionFor(audio.mimeType)
            val fileName = "${safeFileName(videoId)}.$extension"
            val directory = downloadsDir.apply { mkdirs() }
            val partFile = File(directory, "$fileName.${System.nanoTime()}.part")
            tempFile = partFile

            httpClient.prepareGet(audio.url) {
                audio.headers.forEach { (key, value) ->
                    // A stale Range header would save only one segment.
                    if (key.equals("Range", ignoreCase = true) ||
                        key.equals("Content-Length", ignoreCase = true) ||
                        key.equals("Accept-Encoding", ignoreCase = true) ||
                        key.equals("Host", ignoreCase = true)
                    ) {
                        return@forEach
                    }
                    header(key, value)
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    error("HTTP ${response.status.value}")
                }
                val totalBytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                var downloaded = 0L
                var lastProgressAt = 0L
                FileOutputStream(partFile).use { output ->
                    while (true) {
                        val read = channel.readAvailable(buffer)
                        if (read == -1) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            val now = System.currentTimeMillis()
                            if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                                lastProgressAt = now
                                setProgress(
                                    track.id,
                                    (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f),
                                )
                            }
                        }
                    }
                }
                if (downloaded <= 0L) error("Downloaded file was empty")
            }

            val target = File(directory, fileName)
            if (target.exists() && !target.delete()) error("Could not replace ${target.name}")
            if (!partFile.renameTo(target)) {
                partFile.copyTo(target, overwrite = true)
                partFile.delete()
            }
            tempFile = null

            val entry = DownloadedTrack(
                track = track,
                fileName = fileName,
                mimeType = audio.mimeType,
                sizeBytes = target.length(),
                downloadedAt = System.currentTimeMillis(),
            )
            synchronized(this) {
                _state.value = _state.value.copy(
                    downloads = _state.value.downloads + (track.id to entry),
                    progress = _state.value.progress - track.id,
                    errors = _state.value.errors - track.id,
                )
                persist(_state.value.downloads)
            }
            LogCatcher.d(TAG, "downloaded ${track.title} (${entry.sizeBytes} bytes)")
        } catch (cancelled: CancellationException) {
            tempFile?.delete()
            setProgress(track.id, null)
            throw cancelled
        } catch (error: Exception) {
            tempFile?.delete()
            LogCatcher.e(TAG, "download failed for ${track.id}", error)
            synchronized(this) {
                _state.value = _state.value.copy(
                    progress = _state.value.progress - track.id,
                    errors = _state.value.errors + (track.id to (error.message ?: "download failed")),
                )
            }
        }
    }

    private fun setProgress(trackId: String, value: Float?) {
        synchronized(this) {
            _state.value = if (value == null) {
                _state.value.copy(progress = _state.value.progress - trackId)
            } else {
                _state.value.copy(progress = _state.value.progress + (trackId to value))
            }
        }
    }

    private fun fileFor(entry: DownloadedTrack): File = File(downloadsDir, entry.fileName)

    private fun safeFileName(videoId: String): String =
        videoId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)

    private fun extensionFor(mimeType: String?): String = when {
        mimeType == null -> "m4a"
        mimeType.contains("webm", ignoreCase = true) -> "webm"
        mimeType.contains("ogg", ignoreCase = true) -> "ogg"
        mimeType.contains("mpeg", ignoreCase = true) -> "mp3"
        else -> "m4a"
    }

    private fun readDownloads(): Map<String, DownloadedTrack> {
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyMap()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, DownloadedTrack>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optStringOrNull("id") ?: continue
            val fileName = item.optStringOrNull("fileName") ?: continue
            val track = Track(
                id = id,
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optStringOrNull("album"),
                durationMs = item.optLong("durationMs", 0L),
                artworkUri = item.optStringOrNull("artworkUri"),
                videoId = item.optStringOrNull("videoId"),
                isLocal = false,
            )
            result[id] = DownloadedTrack(
                track = track,
                fileName = fileName,
                mimeType = item.optStringOrNull("mimeType"),
                sizeBytes = item.optLong("sizeBytes", 0L),
                downloadedAt = item.optLong("downloadedAt", 0L),
            )
        }
        return result
    }

    private fun persist(downloads: Map<String, DownloadedTrack>) {
        val array = JSONArray()
        downloads.values.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.track.id)
                    put("title", entry.track.title)
                    put("artist", entry.track.artist)
                    put("album", entry.track.album)
                    put("durationMs", entry.track.durationMs)
                    put("artworkUri", entry.track.artworkUri)
                    put("videoId", entry.track.videoId)
                    put("fileName", entry.fileName)
                    put("mimeType", entry.mimeType)
                    put("sizeBytes", entry.sizeBytes)
                    put("downloadedAt", entry.downloadedAt)
                },
            )
        }
        prefs.edit().putString(KEY_DOWNLOADS, array.toString()).apply()
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null
        else optString(name).takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "OfflineDownloads"
        const val PREFS = "tuneplayer_downloads"
        const val KEY_DOWNLOADS = "downloads"
        const val DIRECTORY = "downloads"
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 150L
    }
}
