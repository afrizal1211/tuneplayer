package com.tuneplayer.app.data.lyrics

import com.tuneplayer.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Key-less fallback lyrics lookup against lyrics.ovh (Deezer-backed).
 *
 * Only returns plain text — no line timing — so it is tried last, after
 * sources that can supply LRC. The title/artist pair is matched by duration
 * when the track knows it, which avoids picking a live edit over the studio
 * version.
 */
class OvhLyricsRepository {

    suspend fun find(track: Track): LyricsLookup? = withContext(Dispatchers.IO) {
        val title = track.title.forLyricsSearch()
        val artist = track.artist.artistForLyricsSearch()
        if (title.isBlank() || artist.isBlank()) return@withContext null

        val hit = runCatching { bestHit(title, artist, track.durationMs) }.getOrNull()
            ?: return@withContext null
        val lyrics = runCatching { lyricsFor(hit.artist, hit.title) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        LyricsLookup(text = lyrics, synced = false, source = LyricsSource.Ovh)
    }

    /** Picks the suggestion closest in duration, or the top hit when unknown. */
    private fun bestHit(title: String, artist: String, durationMs: Long): Hit? {
        val url = "https://api.lyrics.ovh/suggest/${encode(artist)}/${encode(title)}"
        val array = runCatching { JSONArray(get(url)) }.getOrNull() ?: return null
        var chosen: Hit? = null
        var bestDiff = Long.MAX_VALUE
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val hitTitle = obj.stringOrNull("title") ?: continue
            val hitArtist = obj.optJSONObject("artist")?.stringOrNull("name") ?: continue
            val hit = Hit(hitTitle, hitArtist, obj.optLong("duration", 0L))
            if (chosen == null) chosen = hit
            if (durationMs > 0L && hit.durationSeconds > 0L) {
                val diff = abs(hit.durationSeconds * 1000L - durationMs)
                if (diff < bestDiff) {
                    bestDiff = diff
                    chosen = hit
                }
            }
        }
        return chosen
    }

    private fun lyricsFor(artist: String, title: String): String? {
        val url = "https://api.lyrics.ovh/v1/${encode(artist)}/${encode(title)}"
        val body = get(url) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()?.stringOrNull("lyrics")
    }

    private fun get(url: String): String? {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull()
            ?: return null
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private data class Hit(
        val title: String,
        val artist: String,
        val durationSeconds: Long,
    )

    private companion object {
        const val USER_AGENT =
            "TunePlayer/0.1 (https://github.com/afrizal1211/tuneplayer)"
        const val MAX_RESPONSE_BYTES = 2L * 1024L * 1024L
    }
}

private fun JSONObject.stringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
