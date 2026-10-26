package com.tuneplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.fadingEdge
import com.tuneplayer.app.ui.util.LocalDownloadTrack
import com.tuneplayer.app.ui.util.LocalDownloadUiState
import com.tuneplayer.app.ui.util.LocalHapticsEnabled
import com.tuneplayer.app.ui.util.LocalNowPlayingId
import com.tuneplayer.app.ui.util.LocalRemoveDownload
import com.tuneplayer.app.ui.util.formatDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun TrackList(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
    grid: Boolean = false,
    listState: LazyListState? = null,
) {
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().fadingEdge(bottom = 12.dp),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(items = tracks, key = { it.id }) { track ->
                GridTrackCell(
                    track = track,
                    isFavorite = track.id in favoriteIds,
                    onPlay = { onPlay(track) },
                )
            }
        }
        return
    }
    val effectiveListState = listState ?: rememberLazyListState()
    LazyColumn(
        state = effectiveListState,
        modifier = Modifier.fillMaxSize().fadingEdge(top = 12.dp, bottom = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(items = tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isFavorite = track.id in favoriteIds,
                onPlay = { onPlay(track) },
                onPlayNext = { onPlayNext(track) },
                onToggleFavorite = { onToggleFavorite(track) },
                onShare = { onShare(track) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
internal fun GridTrackCell(
    track: Track,
    isFavorite: Boolean,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
            .padding(4.dp),
    ) {
        Artwork(track = track, size = 160)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TrackRow(
    track: Track,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val downloadState = LocalDownloadUiState.current
    val downloadProgress = downloadState.progress[track.id]
    val onDownloadTrack = LocalDownloadTrack.current
    val onRemoveDownload = LocalRemoveDownload.current
    val isCurrent = LocalNowPlayingId.current == track.id
    val hapticsEnabled = LocalHapticsEnabled.current
    val hapticFeedback = LocalHapticFeedback.current

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        headlineContent = {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append(track.artist)
                    if (track.durationMs > 0) {
                        append(" · ")
                        append(formatDuration(track.durationMs))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(modifier = Modifier.size(52.dp)) {
                Artwork(
                    track = track,
                    size = 52,
                    cornerRadius = 12,
                    shadowElevation = if (isCurrent) 4 else 0,
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (downloadProgress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (hapticsEnabled) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onToggleFavorite()
                    },
                ) {
                    AnimatedIcon(isFavorite = isFavorite)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.tab_more),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_play_next)) },
                            onClick = {
                                menuOpen = false
                                onPlayNext()
                            },
                            leadingIcon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null) },
                        )
                        if (!track.isLocal) {
                            val downloaded = track.id in downloadState.downloadedIds
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            when {
                                                downloaded -> R.string.action_remove_download
                                                downloadProgress != null -> R.string.action_downloading
                                                else -> R.string.action_download
                                            },
                                        ),
                                    )
                                },
                                enabled = downloadProgress == null,
                                onClick = {
                                    menuOpen = false
                                    if (downloaded) {
                                        onRemoveDownload(track)
                                    } else {
                                        onDownloadTrack(track)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        if (downloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        )
                    }
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
internal fun DownloadIconButton(track: Track, modifier: Modifier = Modifier) {
    if (track.isLocal) return
    val downloadState = LocalDownloadUiState.current
    val onDownloadTrack = LocalDownloadTrack.current
    val progress = downloadState.progress[track.id]
    val downloaded = track.id in downloadState.downloadedIds
    IconButton(
        onClick = { onDownloadTrack(track) },
        enabled = progress == null && !downloaded,
        modifier = modifier,
    ) {
        when {
            progress != null -> CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            downloaded -> Icon(
                Icons.Default.DownloadDone,
                contentDescription = stringResource(R.string.action_downloaded),
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                Icons.Default.Download,
                contentDescription = stringResource(R.string.action_download),
            )
        }
    }
}
