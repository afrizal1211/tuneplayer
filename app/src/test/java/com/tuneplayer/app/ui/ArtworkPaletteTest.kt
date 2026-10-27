package com.tuneplayer.app.ui

import com.tuneplayer.app.ui.graphics.ColorUtils
import com.tuneplayer.app.ui.theme.adaptedArtworkSaturation
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkPaletteTest {
    @Test
    fun neutralSaturationKeptBelowChromaticThreshold() {
        assertEquals(0.05f, adaptedArtworkSaturation(0.05f, minimum = 0.20f, maximum = 0.62f))
    }

    @Test
    fun chromaticSaturationLiftedToTheFloor() {
        assertEquals(0.20f, adaptedArtworkSaturation(0.14f, minimum = 0.20f, maximum = 0.62f))
    }

    @Test
    fun chromaticSaturationCappedAtTheCeiling() {
        assertEquals(0.62f, adaptedArtworkSaturation(0.95f, minimum = 0.20f, maximum = 0.62f))
    }

    @Test
    fun hslRoundTripKeepsTheColourWithinQuantisation() {
        val origins = listOf(0xFF3A61D4.toInt(), 0xFFD43A3A, 0xFF3AD47F, 0xFF808080, 0xFF101010, -1)
        for (origin in origins) {
            val out = FloatArray(3).also { ColorUtils.colorToHSL(origin, it) }
            assertEquals(origin, ColorUtils.HSLToColor(out))
        }
    }

    @Test
    fun greyStaysGreyThroughHslWhateverFloorItIsGiven() {
        // Hue 0 on grey plus a saturation lift must not manufacture colour.
        val grey = 0xFFC0C0C0.toInt()
        val out = FloatArray(3).also { ColorUtils.colorToHSL(grey, it) }
        out[1] = adaptedArtworkSaturation(out[1], minimum = 0.20f, maximum = 0.62f)
        val rebuilt = ColorUtils.HSLToColor(out)
        assertEquals((rebuilt shr 16) and 0xFF, (rebuilt shr 8) and 0xFF)
        assertEquals((rebuilt shr 8) and 0xFF, rebuilt and 0xFF)
    }
}
