package com.tuneplayer.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Fades a scrollable container's content out at the named edges.
 *
 * Without this a list simply ends at the viewport edge with a hard cut, which
 * reads as a rendering fault rather than as "more content here".
 *
 * [alpha] exists because [BlendMode.DstIn] on an opaque layer is a no-op; the
 * 0.99f forces the layer to be treated as non-opaque. Do not remove it.
 */
fun Modifier.fadingEdge(
    top: Dp? = null,
    bottom: Dp? = null,
    left: Dp? = null,
    right: Dp? = null,
): Modifier = this
    .graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        top?.let {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = it.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        bottom?.let {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - it.toPx(),
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        left?.let {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = it.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        right?.let {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - it.toPx(),
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
