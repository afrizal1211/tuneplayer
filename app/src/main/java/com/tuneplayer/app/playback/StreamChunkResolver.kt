package com.tuneplayer.app.playback

import kotlin.math.min

/**
 * How long a chunked read of [position, position + requestedLength) should be.
 *
 * YouTube's audio responses are frequently unbounded (`Transfer-Encoding:
 * chunked`, no `Content-Length`), which some mime types cannot be read as an
 * open-ended stream — the reader needs a length. Returns the largest length
 * that is safe for both the server's declared size and the caller's cap.
 *
 * Null means "do not issue this read": the position is already past the end, or
 * an open-ended read was asked for on a mime type that requires one.
 */
internal fun resolveStreamChunkLength(
    requestedLength: Long,
    position: Long,
    knownContentLength: Long?,
    chunkLength: Long,
    mimeType: String? = null,
): Long? {
    if (chunkLength <= 0L || position < 0L) return null
    if (knownContentLength != null && position >= knownContentLength) return null
    if (requestedLength <= 0L && mimeType.requiresOpenEndedRead()) return null

    val remainingLength = knownContentLength?.minus(position)?.takeIf { it > 0L }
    val resolvedLength =
        listOfNotNull(
            chunkLength,
            requestedLength.takeIf { it > 0L },
            remainingLength,
        ).minOrNull()

    return resolvedLength?.takeIf { it > 0L }
}

private fun String?.requiresOpenEndedRead(): Boolean {
    val normalized =
        this
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase()
            .orEmpty()
    return normalized in OPEN_ENDED_MIME_TYPES
}

private val OPEN_ENDED_MIME_TYPES = setOf(
    "audio/mp4",
    "video/mp4",
    "application/mp4",
    "audio/x-m4a",
    "audio/webm",
    "video/webm",
    "application/webm",
    "audio/ogg",
    "application/ogg",
)
