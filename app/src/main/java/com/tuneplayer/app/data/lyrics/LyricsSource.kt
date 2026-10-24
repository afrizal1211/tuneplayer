package com.tuneplayer.app.data.lyrics

/** Where the currently displayed lyrics came from. */
enum class LyricsSource {
    YouTube,
    Lrclib,
    Ovh,
    KuGou,
}

data class LyricsLookup(
    val text: String,
    val synced: Boolean,
    val source: LyricsSource,
    /** True when the text carries per-word timing, not just per-line. */
    val wordSynced: Boolean = false,
)
