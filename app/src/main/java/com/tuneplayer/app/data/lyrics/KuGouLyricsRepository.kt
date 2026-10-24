package com.tuneplayer.app.data.lyrics

import com.tuneplayer.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Key-less lyrics lookup against KuGou's public music APIs.
 *
 * Two steps, because the search response and the lyrics response are separate:
 * find candidate songs, then fetch each candidate's word-synced lyric. KuGou is
 * one of the few key-less sources that returns per-word timing, which is why
 * it is consulted even when a line-timed source has already answered.
 */
class KuGouLyricsRepository {

    suspend fun find(track: Track): LyricsLookup? = withContext(Dispatchers.IO) {
        val title = track.title.forLyricsSearch()
        val artist = track.artist.artistForLyricsSearch()
        if (title.isBlank()) return@withContext null

        val candidates = runCatching { search(title, artist) }.getOrNull().orEmpty()
        if (candidates.isEmpty()) return@withContext null

        val ranked = candidates.sortedBy { candidate ->
            var score = 0
            if (candidate.title.isNotBlank() &&
                !candidate.title.contains(title, ignoreCase = true) &&
                !title.contains(candidate.title, ignoreCase = true)
            ) {
                score += 2
            }
            if (artist.isNotBlank() && !candidate.artist.contains(artist, ignoreCase = true)) {
                score += 1
            }
            score
        }

        ranked.firstNotNullOfOrNull { candidate ->
            runCatching { lyrics(candidate) }.getOrNull()
        }
    }

    private fun search(title: String, artist: String): List<Candidate> {
        val url = "$SEARCH_URL?keyword=" +
            encode(if (artist.isBlank()) title else "$artist - $title")
        val body = get(url) ?: return emptyList()
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val list = root.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val hash = item.optStringOrNull("FileHash") ?: return@mapNotNull null
            Candidate(
                hash = hash,
                albumId = item.optStringOrNull("AlbumID"),
                title = item.optString("SongName"),
                artist = item.optString("SingerName"),
            )
        }
    }

    private fun lyrics(candidate: Candidate): LyricsLookup? {
        val url = "$LYRICS_URL?ver=1&man=yes&client=pc&hash=" + encode(candidate.hash)
        val body = get(url) ?: return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val text = root.optStringOrNull("content") ?: return null
        return LyricsLookup(
            text = text,
            synced = true,
            source = LyricsSource.KuGou,
            wordSynced = true,
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

    private data class Candidate(
        val hash: String,
        val albumId: String?,
        val title: String,
        val artist: String,
    )

    private companion object {
        const val SEARCH_URL = "https://complexsearch.kugou.com/api/v3/search/song"
        const val LYRICS_URL = "https://lyrics.kugou.com/download"
        const val USER_AGENT = "TunePlayer/0.1 (https://github.com/afrizal1211/tuneplayer)"
        const val MAX_RESPONSE_BYTES = 4L * 1024L * 1024L
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
