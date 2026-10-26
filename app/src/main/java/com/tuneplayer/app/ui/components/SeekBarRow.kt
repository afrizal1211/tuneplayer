package com.tuneplayer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.ui.util.formatDuration

/**
 * The scrubber row: [ThinSlider] plus the current and remaining times.
 *
 * [loading] shows the sheen instead of the fill — pass it while a stream is
 * being resolved, when the position has nothing meaningful to draw.
 */
@Composable
internal fun SeekBarRow(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    loading: Boolean = false,
    /** Artwork-palette accent; null keeps the theme colour. */
    accent: Color? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val max = durationMs.coerceAtLeast(1L).toFloat()

    Column(modifier = Modifier.fillMaxWidth()) {
        ThinSlider(
            value = (if (dragging) dragValue else positionMs.coerceIn(0L, durationMs).toFloat()) / max,
            onValueChange = {
                dragging = true
                dragValue = it * max
            },
            onValueChangeFinished = {
                onSeek(dragValue.toLong())
                dragging = false
            },
            loading = loading,
            activeColor = accent ?: MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(if (dragging) dragValue.toLong() else positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
