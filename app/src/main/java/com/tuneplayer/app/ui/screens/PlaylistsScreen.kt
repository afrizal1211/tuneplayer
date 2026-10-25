package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.prefs.PlaylistStore
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.TrackList
import com.tuneplayer.app.ui.fadingEdge

@Composable
internal fun PlaylistsScreen(
    playlists: List<PlaylistStore.Playlist>,
    tracksFor: (PlaylistStore.Playlist) -> List<Track>,
    favoriteIds: Set<String>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onAddQueue: (String) -> Unit,
    onRemoveTrack: (String, String) -> Unit,
    onPlay: (PlaylistStore.Playlist, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    var openId by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tab_playlists),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showCreate = true }) {
                Text(stringResource(R.string.action_create_playlist))
            }
        }

        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.Default.PlaylistPlay,
                title = stringResource(R.string.playlists_empty),
                hint = stringResource(R.string.playlists_empty_hint),
                action = {
                    TextButton(onClick = { showCreate = true }) {
                        Text(stringResource(R.string.action_create_playlist))
                    }
                },
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().fadingEdge(bottom = 12.dp)) {
            items(items = playlists, key = { it.id }) { playlist ->
                val count = tracksFor(playlist).size
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = {
                        Text(stringResource(R.string.playlist_track_count, count))
                    },
                    leadingContent = {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onAddQueue(playlist.id) }) {
                                Icon(
                                    Icons.Default.PlaylistAdd,
                                    contentDescription = stringResource(R.string.action_add_to_playlist),
                                )
                            }
                            IconButton(onClick = { onPlay(playlist, 0) }) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.action_play),
                                )
                            }
                            IconButton(onClick = { onDelete(playlist.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete_playlist),
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { openId = playlist.id },
                )
                HorizontalDivider()
            }
        }
    }

    if (showCreate) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_create_playlist),
            initialName = "",
            onConfirm = { name ->
                onCreate(name)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    val open = playlists.firstOrNull { it.id == openId }
    if (open != null) {
        val tracks = tracksFor(open)
        AlertDialog(
            onDismissRequest = { openId = null },
            title = { Text(open.name) },
            text = {
                if (tracks.isEmpty()) {
                    Text(stringResource(R.string.playlist_empty))
                } else {
                    LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp).fadingEdge(top = 8.dp, bottom = 8.dp),
        ) {
                        itemsIndexed(items = tracks, key = { index, _ -> index }) { index, track ->
                            TrackRow(
                                track = track,
                                isFavorite = track.id in favoriteIds,
                                onPlay = { onPlay(open, index) },
                                onPlayNext = { onPlayNext(track) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onShare = { onShare(track) },
                                trailing = {
                                    IconButton(onClick = { onRemoveTrack(open.id, track.id) }) {
                                        Icon(
                                            Icons.Default.RemoveCircleOutline,
                                            contentDescription = stringResource(
                                                R.string.action_remove_from_playlist,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openId = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameId = open.id; openId = null }) {
                    Text(stringResource(R.string.action_rename_playlist))
                }
            },
        )
    }

    val renaming = playlists.firstOrNull { it.id == renameId }
    if (renaming != null) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_rename_playlist),
            confirmLabel = stringResource(R.string.action_rename_playlist),
            initialName = renaming.name,
            onConfirm = { name ->
                onRename(renaming.id, name)
                renameId = null
                openId = null
            },
            onDismiss = { renameId = null },
        )
    }
}

@Composable
internal fun PlaylistNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel ?: stringResource(R.string.action_create_playlist)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
