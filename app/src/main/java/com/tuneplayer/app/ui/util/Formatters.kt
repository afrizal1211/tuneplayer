package com.tuneplayer.app.ui.util

import androidx.compose.runtime.Composable
import com.tuneplayer.app.ui.util.formatDuration
import com.tuneplayer.app.ui.util.formatSpeed

/** 1x, 1.25x, 1.5x: no trailing zeros, no rounding surprises. */
internal fun formatSpeed(speed: Float): String {
    val number = if (speed == speed.toInt().toFloat()) {
        "%.0f".format(speed)
    } else {
        "%.2f".format(speed).trimEnd('0').trimEnd('.')
    }
    return "${number}x"
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
