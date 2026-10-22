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
    /** Cached stream URI for remote tracks; null until resolved. */
    val resolvedUri: String? = null,
)
