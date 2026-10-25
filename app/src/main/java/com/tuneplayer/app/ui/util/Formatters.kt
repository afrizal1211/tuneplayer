package com.tuneplayer.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

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

internal val SLEEP_MINUTE_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90)

internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

/** Honors the haptics switch without threading a boolean through every row. */
internal val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Lets list rows highlight the track that is actually playing. */
internal val LocalNowPlayingId = staticCompositionLocalOf<String?> { null }
