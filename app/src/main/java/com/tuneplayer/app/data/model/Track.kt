package com.tuneplayer.app.data.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
    val mediaUri: String? = null,
    val videoId: String? = null,
    val isLocal: Boolean = false,
    /** YouTube browseId for the artist page, when the renderer exposed it. */
    val artistBrowseId: String? = null,
    /** YouTube browseId for the album page, when the renderer exposed it. */
    val albumBrowseId: String? = null,
    /**
     * True for YouTube's music-video uploads, which duplicate an audio track
     * under a different videoId. Queue building prefers the audio cut.
     */
    val isVideo: Boolean = false,
    /** Cached stream URI for remote tracks; null until resolved. */
    val resolvedUri: String? = null,
)
