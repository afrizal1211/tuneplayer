package com.tuneplayer.app.data.lyrics

/** Where the currently displayed lyrics came from. */
enum class LyricsSource {
    YouTube,
    Lrclib,
}

data class LyricsLookup(
    val text: String,
    val synced: Boolean,
    val source: LyricsSource,
)
