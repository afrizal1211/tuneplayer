package com.tuneplayer.app.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricFocusTest {
    private fun line(timeMs: Long, text: String, endMs: Long) = LyricsLine(
        timeMs,
        text,
        words = listOf(WordTimestamp(text, timeMs, endMs)),
    )

    private val overlap = listOf(
        line(1_000, "upper", 4_000),
        line(2_000, "middle", 5_000),
        line(3_000, "lower", 6_000),
    )

    private val plain = listOf(
        LyricsLine(1_000, "upper"),
        LyricsLine(2_000, "middle"),
        LyricsLine(3_000, "lower"),
    )

    @Test
    fun upperLineKeepsAnchorAcrossThreeOverlappingVocals() {
        assertEquals(listOf(0, 1, 2), activeLyricRows(overlap, 3_500))
    }

    @Test
    fun anchorAdvancesExactlyWhenUpperVocalEnds() {
        assertEquals(listOf(1, 2), activeLyricRows(overlap, 4_000))
        assertEquals(listOf(2), activeLyricRows(overlap, 5_000))
    }

    @Test
    fun finishedMiddleRowDoesNotCutOffLongUpperVocal() {
        val lines = overlap.toMutableList()
        lines[1] = lines[1].copy(words = listOf(WordTimestamp("middle", 2_000, 2_900)))
        assertEquals(listOf(0, 2), activeLyricRows(lines, 3_500))
    }

    @Test
    fun plainLrcAdvancesWithoutInventingAnOverlap() {
        assertEquals(listOf(1), activeLyricRows(plain, 2_500))
    }

    @Test
    fun instrumentalGapIsNotKeptLit() {
        val lines = listOf(
            line(1_000, "first", 1_800),
            LyricsLine.INSTRUMENTAL.copy(timeMs = 2_000),
            line(3_000, "third", 4_000),
        )
        assertEquals(listOf(2), activeLyricRows(lines, 3_500))
    }

    @Test
    fun backwardSeekRecomputesTheAnchor() {
        assertEquals(listOf(2), activeLyricRows(overlap, 5_500))
        assertEquals(listOf(0), activeLyricRows(overlap, 1_500))
        assertEquals(emptyList<Int>(), activeLyricRows(overlap, 500))
    }
}
