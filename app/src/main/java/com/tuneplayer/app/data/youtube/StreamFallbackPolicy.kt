package com.tuneplayer.app.data.youtube

import com.metrolist.innertubex.models.YouTubeClient

/**
 * Why a stream URL was refused.
 *
 * Kept as its own type so the layers above can tell a refusal that will never
 * soften (an age gate, a takedown) from one worth retrying (a rate limit, a
 * minted-but-dead URL). A load error carries only an exception, so the
 * distinction has to travel in the type.
 */
class PermanentlyUnplayableException(reason: String) : Exception(reason)

/** Verdicts a probe can return for a URL handed to it. */
enum class StreamProbe {
    /** Bytes came back; the URL is usable. */
    OK,

    /** The server answered, but not with audio (403, 404, a redirect loop). */
    REFUSED,

    /** The request never completed; the URL may still be good. */
    UNREACHABLE,
}

/**
 * Which client identities to ask, and in what order.
 *
 * A client whose URL fails the probe is skipped and the next one tried, so a
 * single minted-but-dead URL does not fail the track. The order is the one
 * that resolves most tracks: the web remix client is the most complete, the
 * mobile clients answer when it does not.
 */
val STREAM_CLIENT_FALLBACK_ORDER = listOf(
    YouTubeClient.WEB_REMIX,
    YouTubeClient.ANDROID_MUSIC,
    YouTubeClient.IOS_MUSIC,
)

/** How many distinct clients a single resolve may try before giving up. */
const val STREAM_RESOLVE_ATTEMPTS = 3
