package com.tuneplayer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqPresetsTest {

    @Test
    fun `five bands pass through unchanged`() {
        val shape = listOf(900, 600, 200, -100, -300)
        assertEquals(shape, EqPresets.resample(shape, 5))
    }

    @Test
    fun `endpoints are preserved for any band count`() {
        val shape = listOf(900, 600, 200, -100, -300)
        val three = EqPresets.resample(shape, 3)
        assertEquals(900, three.first())
        assertEquals(-300, three.last())
        assertEquals(3, three.size)
    }

    @Test
    fun `single band picks the middle of the shape`() {
        assertEquals(listOf(200), EqPresets.resample(listOf(900, 600, 200, -100, -300), 1))
    }

    @Test
    fun `flat shape stays flat at every band count`() {
        listOf(2, 5, 10).forEach { count ->
            assertTrue(EqPresets.resample(listOf(0, 0, 0, 0, 0), count).all { it == 0 })
        }
    }

    @Test
    fun `empty or non-positive counts yield no bands`() {
        assertTrue(EqPresets.resample(listOf(1, 2, 3), 0).isEmpty())
        assertTrue(EqPresets.resample(emptyList(), 5).isEmpty())
    }

    @Test
    fun `every preset has a shape`() {
        assertEquals(4, EqPresets.shapes.size)
        assertTrue(EqPresets.shapes.all { it.size == 5 })
    }
}
