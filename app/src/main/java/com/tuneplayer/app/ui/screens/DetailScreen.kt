package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.TrackList

@Composable
internal fun DetailScreen(
    title: String,
    tracks: List<Track>,
    isLoading: Boolean,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
) {
    if (tracks.isEmpty() && !isLoading) {
        EmptyState(
            icon = Icons.Default.MusicNote,
            title = title,
            hint = "No tracks found",
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        BulkActionRow(onPlayAll = onPlayAll, onShuffleAll = onShuffleAll)
        Box(modifier = Modifier.weight(1f)) {
            TrackList(
                tracks = tracks,
                favoriteIds = favoriteIds,
                onPlay = onPlay,
                onPlayNext = onPlayNext,
                onToggleFavorite = onToggleFavorite,
                onShare = onShare,
                listState = rememberLazyListState(),
            )
        }
    }
}
