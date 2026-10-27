package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.TrackList

@Composable
internal fun StatsScreen(
    totalPlays: Int,
    distinctTracks: Int,
    topTracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    if (totalPlays == 0) {
        EmptyState(
            icon = Icons.Default.BarChart,
            title = stringResource(R.string.stats_empty),
            hint = stringResource(R.string.stats_empty_hint),
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatCell(stringResource(R.string.stats_total_plays), totalPlays)
            StatCell(stringResource(R.string.stats_distinct_tracks), distinctTracks)
            // ~4 min average per play
            val minutes = (totalPlays * 4L / 60).toInt()
            val hours = minutes / 60
            val mins = minutes % 60
            StatCell(stringResource(R.string.stats_listening_time), stringResource(R.string.stats_hours, hours, mins), isString = true)
        }
        Text(
            text = stringResource(R.string.stats_top_tracks),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (topTracks.isEmpty()) {
            // Counts exist but nothing resolves: the files moved or the library
            // has not been scanned yet.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.stats_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TrackList(
                tracks = topTracks,
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
internal fun StatCell(label: String, value: Int) = StatCell(label, value.toString())

@Composable
internal fun StatCell(label: String, value: String, isString: Boolean = false) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
