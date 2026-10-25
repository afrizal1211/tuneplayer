package com.tuneplayer.app.ui.util

import com.tuneplayer.app.R
import com.tuneplayer.app.data.lyrics.LyricsSource

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

internal val SLEEP_MINUTE_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90)

internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

internal fun LyricsSource.labelRes(): Int = when (this) {
    LyricsSource.YouTube -> R.string.lyrics_source_youtube
    LyricsSource.Lrclib -> R.string.lyrics_source_lrclib
    LyricsSource.Ovh -> R.string.lyrics_source_ovh
    LyricsSource.KuGou -> R.string.lyrics_source_kugou
}
