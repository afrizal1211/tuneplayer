package com.tuneplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tuneplayer.app.data.model.Track

@Composable
internal fun Artwork(
    track: Track,
    size: Int,
    cornerRadius: Int = 8,
    shadowElevation: Int = 0,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    val baseModifier = modifier
        .size(size.dp)
        .shadow(elevation = shadowElevation.dp, shape = shape)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (track.artworkUri != null) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            modifier = baseModifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = baseModifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size * 0.28f).dp),
            )
        }
    }
}
