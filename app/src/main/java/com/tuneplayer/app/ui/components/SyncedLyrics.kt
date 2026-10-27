package com.tuneplayer.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.data.lyrics.LyricsLine
import kotlin.math.abs
import androidx.compose.runtime.getValue

@Composable
internal fun SyncedLyrics(
    lines: List<LyricsLine>,
    activeIndex: Int,
    activeWordIndex: Int,
    activeRows: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    // The uppermost still-sung row owns the scroll anchor until it ends, even
    // as later rows begin their own highlights.
    val anchor = activeRows.firstOrNull() ?: activeIndex
    val listState = rememberLazyListState()
    LaunchedEffect(anchor) {
        if (anchor in lines.indices) {
            listState.animateScrollToItem(anchor)
        }
    }
    val litRows = remember(activeRows) { activeRows.toHashSet() }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 360.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val active = index == activeIndex
            val rowLit = index != activeIndex && index in litRows
            // Word timing only exists for the active line, so the word index
            // applies to it and nothing else.
            val words = line.words?.takeIf { active && it.isNotEmpty() }
            val distance = abs(index - activeIndex).coerceAtMost(3)
            val scale by animateFloatAsState(
                targetValue = when {
                    active -> 1.12f
                    rowLit -> 1.06f
                    distance == 1 -> 1.0f
                    distance == 2 -> 0.94f
                    else -> 0.88f
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "lyricScale",
            )
            val alpha by animateFloatAsState(
                targetValue = when {
                    active -> 1f
                    rowLit -> 0.85f
                    distance == 1 -> 0.75f
                    distance == 2 -> 0.5f
                    else -> 0.32f
                },
                animationSpec = tween(220),
                label = "lyricAlpha",
            )
            Text(
                text = line.annotated(active && words != null, activeWordIndex),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active || rowLit) FontWeight.Bold else FontWeight.Medium,
                color = if (active || rowLit) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        // Anchor the growth to the left edge so the line does
                        // not slide sideways as it scales.
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun LyricsLine.annotated(wordSynced: Boolean, activeWordIndex: Int): AnnotatedString {
    val highlight = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val words = this.words
    if (!wordSynced || words.isNullOrEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, cursor).takeIf { it >= 0 } ?: return@forEachIndexed
            if (start > cursor) append(text.substring(cursor, start))
            withStyle(SpanStyle(color = if (index <= activeWordIndex) highlight else base)) {
                append(word.text)
            }
            cursor = start + word.text.length
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
