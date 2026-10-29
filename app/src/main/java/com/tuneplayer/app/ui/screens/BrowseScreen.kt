package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onOpenArtist: (Track) -> Unit = {},
    onOpenAlbum: (Track) -> Unit = {},
    onOpenCategory: (browseId: String, label: String) -> Unit = { _, _ -> },
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
        // Top-level browse categories; long-press a track still opens artist/album.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryChip("FEmusic_charts", "Charts", onOpenCategory)
            CategoryChip("FEmusic_explore", "Explore", onOpenCategory)
            CategoryChip("FEmusic_moods_and_genres", "Moods & Genres", onOpenCategory)
        }
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
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                )
            } else {
                ShelfRail(
                    tracks = shelf.tracks,
                    favoriteIds = favoriteIds,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onToggleFavorite = onToggleFavorite,
                    onShare = onShare,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
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
    onOpenArtist: (Track) -> Unit = {},
    onOpenAlbum: (Track) -> Unit = {},
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
                            onOpenArtist = { onOpenArtist(track) },
                            onOpenAlbum = { onOpenAlbum(track) },
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
    onOpenArtist: (Track) -> Unit = {},
    onOpenAlbum: (Track) -> Unit = {},
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
                    .combinedClickable(
                        onClick = { onPlay(track) },
                        onLongClick = {
                            when {
                                track.artistBrowseId != null -> onOpenArtist(track)
                                track.albumBrowseId != null -> onOpenAlbum(track)
                            }
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(
                    track = track,
                    size = 140,
                    cornerRadius = 12,
                    modifier = Modifier
                        .size(140.dp),
                )
                Text(
                    text = track.title.ifBlank { track.artist },
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

@Composable
private fun CategoryChip(
    browseId: String,
    label: String,
    onOpen: (String, String) -> Unit,
) {
    Surface(
        onClick = { onOpen(browseId, label) },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.height(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
