package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.youtube.YouTubeHomeShelf
import com.tuneplayer.app.ui.components.Artwork
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.TrackRow
import com.tuneplayer.app.ui.fadingEdge

@Composable
internal fun BrowseScreen(
    shelves: List<YouTubeHomeShelf>,
    isLoading: Boolean,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
    onPlayShelf: (List<Track>) -> Unit,
) {
    if (isLoading && shelves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (shelves.isEmpty()) {
        EmptyState(
            icon = androidx.compose.material.icons.Icons.Default.Home,
            title = stringResource(R.string.browse_empty),
            hint = stringResource(R.string.browse_empty_hint),
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        shelves.forEachIndexed { index, shelf ->
            Text(
                text = shelf.title.ifEmpty { stringResource(R.string.browse_for_you) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )
            // First shelf is the quick-pick grid; the rest are horizontal rails.
            if (index == 0) {
                QuickPickGrid(
                    tracks = shelf.tracks,
                    favoriteIds = favoriteIds,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onToggleFavorite = onToggleFavorite,
                    onShare = onShare,
                )
            } else {
                ShelfRail(
                    tracks = shelf.tracks,
                    favoriteIds = favoriteIds,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onToggleFavorite = onToggleFavorite,
                    onShare = onShare,
                )
            }
        }
    }
}

@Composable
private fun QuickPickGrid(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tracks.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pair.forEach { track ->
                    Box(Modifier.weight(1f)) {
                        TrackRow(
                            track = track,
                            isFavorite = track.id in favoriteIds,
                            onPlay = { onPlay(track) },
                            onPlayNext = { onPlayNext(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onShare = { onShare(track) },
                        )
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShelfRail(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fadingEdge(bottom = 0.dp),
    ) {
        items(tracks, key = { it.id }) { track ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { onPlay(track) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(
                    uri = track.artworkUri,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
