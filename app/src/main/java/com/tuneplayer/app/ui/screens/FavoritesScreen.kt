package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.TrackList
import com.tuneplayer.app.ui.screens.FavoritesScreen

internal fun FavoritesScreen(
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
            icon = Icons.Default.FavoriteBorder,
            title = stringResource(R.string.liked_empty),
            hint = stringResource(R.string.liked_empty_hint),
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

/** Small play/shuffle pair used above list screens that have no toolbar. */
@Composable
internal fun BulkActionRow(
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onShuffleAll,
            label = { Text(stringResource(R.string.action_shuffle_all)) },
            leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
        )
        AssistChip(
            onClick = onPlayAll,
            label = { Text(stringResource(R.string.action_play_all)) },
            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
