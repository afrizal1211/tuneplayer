package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.TrackList
import com.tuneplayer.app.ui.screens.HistoryScreen

internal fun HistoryScreen(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = stringResource(R.string.history_empty),
            hint = stringResource(R.string.history_empty_hint),
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
            )
        }
    }
}

@Composable
