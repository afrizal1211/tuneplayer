package com.tuneplayer.app.data.lyrics

/**
 * Parses LRC into [LyricsLine]s, carrying per-word timing when the source
 * provides it.
 *
 * Two word-level dialects are understood:
 *
 *  - **LRC word extension** — `<mm:ss.xx>` tags inline before each word:
 *    `[00:12.00]Hello <00:12.00>dark <00:12.40>ness`
 *  - **QRC** — a single line tag followed by `<offset,length>` pairs after
 *    each word: `[00:12.00]Hello(0,400)darkness(400,900)`
 *
 * Line-only LRC keeps working unchanged: [words] stays null.
 */
object LyricsParser {

    private val LINE_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val QRC_WORD = Regex("""(\S+?)\((\d+),(\d+)\)""")
    private val INSTRUMENTAL = Regex("""^\s*(?:\*{2,}|[♪\s]+|instrumental|纯音乐)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String?): List<LyricsLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricsLine>()
        lrc.lineSequence().forEach { raw ->
            if (raw.isBlank()) return@forEach
            val lineTags = LINE_TAG.findAll(raw).toList()
            if (lineTags.isEmpty()) return@forEach
            val body = raw.substring(lineTags.last().range.last + 1)
            if (body.isBlank()) return@forEach

            if (INSTRUMENTAL.matches(body)) {
                lineTags.forEach { tag ->
                    lines += LyricsLine(
                        timeMs = toMillis(tag),
                        text = "♪",
                        words = null,
                        isInstrumental = true,
                    )
                }
                return@forEach
            }

            val words = parseWords(body)
            lineTags.forEach { tag ->
                val timeMs = toMillis(tag)
                lines += LyricsLine(
                    timeMs = timeMs,
                    text = body.let(::stripWordMarkup).trim(),
                    // Word times are absolute, on the same clock as [LyricsLine.timeMs].
                    words = words?.let { shift(it, timeMs) },
                )
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** True when every parsed line carries word timing. */
    fun isWordSynced(lines: List<LyricsLine>): Boolean =
        lines.isNotEmpty() && lines.all { it.words != null && it.words.isNotEmpty() }

    private fun toMillis(tag: MatchResult): Long {
        val (min, sec, frac) = tag.destructured
        val ms = frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return (min.toLongOrNull() ?: 0L) * 60_000L +
            (sec.toLongOrNull() ?: 0L) * 1000L + ms
    }

    /**
     * Per-word timings relative to the start of the line, or null when the
     * body carries neither dialect.
     *
     * LRC word tags are absolute, so they are rebased onto the line start; QRC
     * offsets are already relative. The last word of an LRC line has no tag
     * after it, so its end is left open.
     */
    private fun parseWords(body: String): List<WordTimestamp>? {
        val wordTags = WORD_TAG.findAll(body).toList()
        if (wordTags.isNotEmpty()) {
            val first = toMillis(wordTags.first())
            val out = mutableListOf<WordTimestamp>()
            wordTags.forEachIndexed { index, tag ->
                val start = toMillis(tag)
                val end = if (index + 1 < wordTags.size) {
                    toMillis(wordTags[index + 1]) - first
                } else {
                    Long.MAX_VALUE
                }
                val text = body.substring(
                    tag.range.last + 1,
                    if (index + 1 < wordTags.size) {
                        wordTags[index + 1].range.first
                    } else {
                        body.length
                    },
                ).trim()
                if (text.isNotEmpty()) {
                    out += WordTimestamp(text, start - first, end)
                }
            }
            return out.takeIf { it.isNotEmpty() }
        }

        val qrc = QRC_WORD.findAll(body).toList()
        if (qrc.isEmpty()) return null
        val out = mutableListOf<WordTimestamp>()
        qrc.forEach { match ->
            val (text, offset, length) = match.destructured
            val start = offset.toLongOrNull() ?: return@forEach
            out += WordTimestamp(
                text = text,
                startTimeMs = start,
                endTimeMs = start + (length.toLongOrNull() ?: 0L),
            )
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** Rebases relative word times onto an absolute line time. */
    private fun shift(words: List<WordTimestamp>, baseMs: Long): List<WordTimestamp> =
        words.map { word ->
            word.copy(
                startTimeMs = baseMs + word.startTimeMs,
                endTimeMs = (baseMs + word.endTimeMs).coerceAtMost(Long.MAX_VALUE - 1L),
            )
        }

    /** Removes both word-level markups, leaving only the sung text. */
    private fun stripWordMarkup(body: String): String =
        body.replace(WORD_TAG, " ").replace(QRC_WORD, "$1")
}
