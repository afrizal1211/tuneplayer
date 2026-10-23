package com.tuneplayer.app.ui

/**
 * Equalizer preset shapes, independent of device band count.
 *
 * Shapes are authored as five points in millibels (the unit the platform
 * effect uses) and resampled onto however many bands the hardware reports.
 * Keeping them here makes the resampling testable without an audio session.
 */
object EqPresets {

    /** Flat, bass, vocal, rock. Index order matches the UI chip order. */
    val shapes: List<List<Int>> = listOf(
        listOf(0, 0, 0, 0, 0),
        listOf(900, 600, 200, -100, -300),
        listOf(-300, -100, 400, 600, 300),
        listOf(500, 300, 0, 300, 500),
    )

    /**
     * Linear sample of [shape] onto [count] points.
     *
     * Endpoints are exact so a preset always keeps its tilt at the extremes.
     */
    fun resample(shape: List<Int>, count: Int): List<Int> {
        if (count <= 0 || shape.isEmpty()) return emptyList()
        if (count == 1) return listOf(shape[shape.size / 2])
        val last = shape.lastIndex
        return (0 until count).map { i ->
            val position = i.toFloat() * last / (count - 1)
            val low = position.toInt().coerceIn(0, last)
            val high = (low + 1).coerceAtMost(last)
            val fraction = position - low
            (shape[low] + (shape[high] - shape[low]) * fraction).toInt()
        }
    }
}
