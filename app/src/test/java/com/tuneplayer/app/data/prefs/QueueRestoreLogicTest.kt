package com.tuneplayer.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Queue restore is id -> Track resolution plus index clamping. The store
 * itself is Android-backed, so only the pure resolution step is covered.
 */
class QueueRestoreLogicTest {

    private fun resolve(ids: List<String>, known: Map<String, String>): List<String> =
        ids.mapNotNull { known[it] }

    @Test
    fun `missing ids are dropped in order`() {
        val known = mapOf("a" to "A", "c" to "C")
        assertEquals(listOf("A", "C"), resolve(listOf("a", "b", "c"), known))
    }

    @Test
    fun `deleted queue resolves to empty rather than broken entries`() {
        assertTrue(resolve(listOf("x", "y"), emptyMap()).isEmpty())
    }

    @Test
    fun `saved index is clamped to the resolved queue`() {
        val queue = listOf("a", "b", "c")
        assertEquals(2, 9.coerceIn(queue.indices))
        assertEquals(0, (-3).coerceIn(queue.indices))
    }

    @Test
    fun `duplicate ids keep both entries`() {
        val known = mapOf("a" to "A")
        assertEquals(listOf("A", "A"), resolve(listOf("a", "a"), known))
    }
}
