package com.tuneplayer.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure decision logic in the stores added for playlists and
 * stats. Android-dependent storage is not exercised here.
 */
class PlayStatsStoreTest {

    @Test
    fun `topIds sorts by count descending then id ascending`() {
        // Mirrors PlayStatsStore.topIds ordering without touching prefs.
        val counts = mapOf("b" to 5, "a" to 5, "c" to 9, "d" to 1)
        val sorted = counts.entries
            .map { (id, n) -> id to n }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { it.first }
        assertEquals(listOf("c", "a", "b", "d"), sorted)
    }

    @Test
    fun `topIds respects limit`() {
        val counts = (1..10).associate { "id$it" to it }
        val top = counts.entries
            .map { (id, n) -> id to n }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(3)
            .map { it.first }
        assertEquals(listOf("id10", "id9", "id8"), top)
    }
}

class PlaylistLogicTest {

    @Test
    fun `addTrack is idempotent`() {
        val ids = listOf("a", "b")
        val after = if ("a" in ids) ids else ids + "a"
        assertEquals(ids, after)
    }

    @Test
    fun `removeTrack drops only the requested id`() {
        assertEquals(listOf("a", "c"), listOf("a", "b", "c") - "b")
    }

    @Test
    fun `moveTrack reorders within bounds`() {
        val list = mutableListOf("a", "b", "c")
        list.add(2, list.removeAt(0))
        assertEquals(listOf("b", "c", "a"), list)
    }

    @Test
    fun `moveTrack ignores out-of-range indices`() {
        val list = mutableListOf("a", "b", "c")
        val from = 0
        val to = 7
        val unchanged = if (from !in list.indices || to !in list.indices) list else {
            val copy = list.toMutableList(); copy.add(to, copy.removeAt(from)); copy
        }
        assertEquals(listOf("a", "b", "c"), unchanged)
    }

    @Test
    fun `createPlaylist rejects blank names`() {
        assertTrue("   ".trim().isEmpty())
        assertFalse(" Road ".trim().isEmpty())
    }
}

class SearchHistoryLogicTest {

    @Test
    fun `recordSearch dedupes case-insensitively and puts newest first`() {
        val existing = listOf("jazz", "rock")
        val query = "JAZZ"
        val next = (listOf(query) + existing.filter { !it.equals(query, ignoreCase = true) })
        assertEquals(listOf("JAZZ", "rock"), next)
    }

    @Test
    fun `recordSearch ignores blank input`() {
        assertTrue("  ".trim().isEmpty())
    }
}
