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
import com.tuneplayer.app.ui.components.SyncedLyrics

internal fun SyncedLyrics(
    lines: List<LyricsLine>,
    activeIndex: Int,
    activeWordIndex: Int,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex in lines.indices) {
            listState.animateScrollToItem(activeIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 360.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val active = index == activeIndex
            // Word timing only exists for the active line, so the word index
            // applies to it and nothing else.
            val words = line.words?.takeIf { active && it.isNotEmpty() }
            val distance = abs(index - activeIndex).coerceAtMost(3)
            val scale by animateFloatAsState(
                targetValue = when {
                    active -> 1.12f
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
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) {
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

/**
 * Builds the text for one lyric row, highlighting each word as it is sung.
 *
 * Words already sung render in the highlight colour; the word in flight and the
 * rest stay in the base colour. Without word timing this is just the line's
 * own text, so plain LRC renders unchanged.
 */
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

/**
 * Recently played. Track ids come from [com.tuneplayer.app.data.prefs.FavoriteStore]
 * history and are resolved against whatever library metadata is loaded.
 *
 * Ids that no longer resolve (deleted file, stale YouTube entry) are dropped
 * rather than shown as broken rows.
 */
@Composable
