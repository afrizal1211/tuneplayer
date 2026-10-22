package com.tuneplayer.app.data.lyrics

/**
 * Parses LRC timestamps into a sortable list of [LrcLine].
 *
 * Returns empty list when the text has no `[mm:ss.xx]` tags, so callers can
 * fall back to plain lyrics. Pure function: no I/O, no Android deps.
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    data class LrcLine(val timeMs: Long, val text: String)

    fun parse(lrc: String?): List<LrcLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        lrc.lineSequence().forEach { raw ->
            val tags = TIME_TAG.findAll(raw).toList()
            if (tags.isEmpty()) return@forEach
            val text = raw.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            tags.forEach { tag ->
                val (min, sec, frac) = tag.destructured
                val ms = frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val timeMs = (min.toLongOrNull() ?: 0L) * 60_000L +
                    (sec.toLongOrNull() ?: 0L) * 1000L + ms
                lines += LrcLine(timeMs, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** Index of the line that should be showing at [positionMs], or -1. */
    fun activeIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) lo = mid + 1 else hi = mid - 1
        }
        return (lo - 1).coerceAtLeast(-1)
    }
}
