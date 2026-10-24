package com.tuneplayer.app.data.lyrics

/**
 * One word inside a line, with its own timing.
 *
 * Word-synced lyrics (LRC's `<mm:ss.xx>` word extension, and QRC's per-word
 * `<offset>` markers) highlight each word as it is sung rather than lighting
 * the whole line at once. Providers that only know line timing leave this null.
 */
data class WordTimestamp(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
)

/**
 * A lyric line, optionally carrying per-word timing.
 *
 * [words] is null for plain line-timed LRC. When present it must cover the
 * whole of [text] in order and be non-overlapping.
 */
data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val isInstrumental: Boolean = false,
) : Comparable<LyricsLine> {
    override fun compareTo(other: LyricsLine): Int = (timeMs - other.timeMs).toInt()

    companion object {
        val INSTRUMENTAL = LyricsLine(0L, "", isInstrumental = true)
    }
}

/** Index of the line showing at [positionMs], or -1. */
fun activeLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    var lo = 0
    var hi = lines.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (lines[mid].timeMs <= positionMs) lo = mid + 1 else hi = mid - 1
    }
    return (lo - 1).coerceAtLeast(-1)
}

/** Index of the word showing at [positionMs] inside [line], or -1. */
fun activeWordIndex(line: LyricsLine, positionMs: Long): Int {
    val words = line.words ?: return -1
    if (words.isEmpty()) return -1
    var lo = 0
    var hi = words.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (words[mid].startTimeMs <= positionMs) lo = mid + 1 else hi = mid - 1
    }
    return (lo - 1).coerceAtLeast(-1)
}
