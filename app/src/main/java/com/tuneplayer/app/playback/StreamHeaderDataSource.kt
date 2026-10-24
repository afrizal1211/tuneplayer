package com.tuneplayer.app.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource

/**
 * The request headers each resolved stream URL was signed against, keyed by URL.
 *
 * A YouTube stream URL is only valid when fetched with the exact headers the
 * client identity that minted it used — a different User-Agent is answered with
 * 403. Those headers are captured when the URL is resolved and are needed again
 * every time the URL is fetched: by the player, by the crossfade peer, and by
 * the tempo decoder. Keeping them here means one source of truth instead of a
 * hardcoded guess that is right for some URLs and wrong for the rest.
 *
 * URLs are not persisted — they expire — and the map is bounded so a long
 * session cannot grow it without limit.
 */
@UnstableApi
object StreamHeaders {

    private const val MAX_ENTRIES = 64

    private val headers = object : LinkedHashMap<String, Map<String, String>>() {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Map<String, String>>,
        ): Boolean = size > MAX_ENTRIES
    }

    fun put(url: String, requestHeaders: Map<String, String>) {
        if (url.isBlank()) return
        synchronized(headers) {
            headers.remove(url)
            headers[url] = requestHeaders
        }
    }

    /** The headers [url] was signed against, or null when it is not a known stream. */
    fun forUri(url: String?): Map<String, String>? {
        if (url.isNullOrBlank()) return null
        return synchronized(headers) { headers[url] }
    }

    fun clear() = synchronized(headers) { headers.clear() }
}

/**
 * Rewrites each request to carry the headers its own URL was signed against.
 *
 * A plain [DefaultHttpDataSource] applies one set of default headers to every
 * URL, which is wrong for streaming URLs: each one decides which User-Agent it
 * will accept. Resolving the spec before it opens is the supported hook for
 * swapping them per URL.
 */
@UnstableApi
private class StreamHeaderResolver : ResolvingDataSource.Resolver {
    override fun resolve(dataSpec: DataSpec): DataSpec {
        val signed = StreamHeaders.forUri(dataSpec.uri.toString()) ?: return dataSpec
        return dataSpec.buildUpon()
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + signed)
            .build()
    }
}

/**
 * A [DataSource.Factory] whose sources send each URL its signed headers.
 *
 * Used for both the session player and the crossfade peer, so a track sounds
 * the same whether it is playing or being blended in.
 */
@UnstableApi
fun streamHeaderDataSourceFactory(
    upstream: DataSource.Factory,
): DataSource.Factory = object : DataSource.Factory {
    override fun createDataSource(): DataSource =
        ResolvingDataSource(upstream.createDataSource(), StreamHeaderResolver())
}
