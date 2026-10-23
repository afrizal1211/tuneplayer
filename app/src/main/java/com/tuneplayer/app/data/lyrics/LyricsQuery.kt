package com.tuneplayer.app.data.lyrics

/**
 * Turns a YouTube title into something a lyrics database has indexed.
 *
 * Only credits and upload packaging are removed. Markers that name a different
 * recording, such as "(Live)" or "(Remix)", stay: showing the wrong lyrics is
 * worse than showing none.
 */
fun String.forLyricsSearch(): String {
    var name = this
    CREDITS.forEach { pattern -> name = pattern.replace(name, " ") }
    return name.replace(WHITESPACE, " ")
        .trim()
        .trimEnd(',', '-', '–', '—')
        .trim()
        .ifBlank { trim() }
}

/** Drops the " - Topic" suffix from YouTube's auto-generated artist channels. */
fun String.artistForLyricsSearch(): String =
    removeSuffix(" - Topic").trim().ifBlank { trim() }

private val WHITESPACE = Regex("""\s+""")

private val CREDITS = listOf(
    // Bracketed credits: (feat. X), [ft. X], (with X).
    Regex("""\s*[(\[]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
    // The same, unbracketed and running to the end of the title.
    Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", RegexOption.IGNORE_CASE),
    // How the upload was labelled, not what was recorded.
    Regex(
        """\s*[(\[]\s*(official\s*)?(music\s*)?""" +
            """(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k|full\s*song)""" +
            """\s*[)\]]""",
        RegexOption.IGNORE_CASE,
    ),
    Regex("""\s*[(\[]\s*official\s*[)\]]""", RegexOption.IGNORE_CASE),
)
