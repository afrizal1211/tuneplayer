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
 * Key-less lyrics lookup against LRCLIB.
 *
 * Exact matches are tried first because they carry the cleanest timing. A
 * fuzzy search then covers YouTube's noisy titles and alternate edits, choosing
 * the result closest to the duration actually being played.
 */
class LrclibLyricsRepository {

    suspend fun find(track: Track): LyricsLookup? = withContext(Dispatchers.IO) {
        val title = track.title.forLyricsSearch()
        val artist = track.artist.artistForLyricsSearch()
        if (title.isBlank() || artist.isBlank()) return@withContext null

        val seconds = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
        val exact = if (seconds != null) {
            runCatching { exactMatch(title, artist, seconds) }.getOrNull()
        } else {
            null
        }
        if (exact != null && !exact.synced.isNullOrBlank()) return@withContext exact.toLookup()

        val fuzzy = runCatching { bestSearchHit(title, artist, seconds) }.getOrNull()
        val chosen = when {
            !fuzzy?.synced.isNullOrBlank() -> fuzzy
            !fuzzy?.plain.isNullOrBlank() -> fuzzy
            !exact?.plain.isNullOrBlank() -> exact
            else -> null
        }
        chosen?.toLookup()
    }

    private fun exactMatch(title: String, artist: String, seconds: Int): Hit? {
        val url = apiUrl(
            "get",
            listOf(
                "track_name" to title,
                "artist_name" to artist,
                "duration" to seconds.toString(),
            ),
        )
        return parseHit(get(url) ?: return null)
    }

    private fun bestSearchHit(title: String, artist: String, seconds: Int?): Hit? {
        val structured = runCatching {
            parseHits(
                get(
                    apiUrl(
                        "search",
                        listOf(
                            "track_name" to title,
                            "artist_name" to artist,
                        ),
                    ),
                ),
            )
        }.getOrDefault(emptyList())

        val hits = if (structured.isNotEmpty()) {
            structured
        } else {
            parseHits(get(apiUrl("search", listOf("q" to "$title $artist"))))
        }
        if (hits.isEmpty()) return null

        val target = seconds?.toDouble() ?: 0.0
        val synced = hits.filter { !it.synced.isNullOrBlank() }
        val pool = synced.ifEmpty { hits.filter { !it.plain.isNullOrBlank() } }
        return pool.minByOrNull {
            if (target > 0.0) abs(it.durationSeconds - target) else 0.0
        }
    }

    private fun parseHits(body: String?): List<Hit> {
        val array = runCatching { JSONArray(body.orEmpty()) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::parseHit)?.let { add(it) }
            }
        }
    }

    private fun parseHit(obj: JSONObject): Hit? {
        if (obj.optBoolean("instrumental", false)) return null
        val synced = obj.stringOrNull("syncedLyrics")
        val plain = obj.stringOrNull("plainLyrics")
        if (synced == null && plain == null) return null
        return Hit(
            synced = synced,
            plain = plain,
            durationSeconds = obj.optDouble("duration", 0.0),
        )
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

    private fun apiUrl(path: String, params: List<Pair<String, String>>): String =
        "https://lrclib.net/api/$path?" + params.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private data class Hit(
        val synced: String?,
        val plain: String?,
        val durationSeconds: Double,
    ) {
        fun toLookup(): LyricsLookup = LyricsLookup(
            text = synced ?: plain.orEmpty(),
            synced = !synced.isNullOrBlank(),
            source = LyricsSource.Lrclib,
        )
    }

    private companion object {
        const val USER_AGENT =
            "TunePlayer/0.1 (https://github.com/afrizal1211/tuneplayer)"
        const val MAX_RESPONSE_BYTES = 4L * 1024L * 1024L
    }
}

private fun JSONObject.stringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
