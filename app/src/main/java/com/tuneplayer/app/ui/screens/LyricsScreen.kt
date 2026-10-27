package com.tuneplayer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.tuneplayer.app.R
import com.tuneplayer.app.ui.components.Artwork
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.SeekBarRow
import com.tuneplayer.app.ui.components.SyncedLyrics
import com.tuneplayer.app.ui.util.labelRes

/**
 * Full-screen lyrics view with blurred artwork backdrop.
 *
 * Reads state directly from the ViewModel (same pattern as BrowseScreen).
 * BackHandler intercepts system back press to dismiss.
 */
@Composable
internal fun LyricsScreen(
    track: com.tuneplayer.app.data.model.Track?,
    lyrics: String?,
    isLoading: Boolean,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    syncedLyrics: List<com.tuneplayer.app.data.lyrics.LyricsLine>,
    activeLyricIndex: Int,
    activeWordIndex: Int,
    activeLyricRows: List<Int>,
    lyricsOffsetMs: Long,
    lyricsSource: com.tuneplayer.app.data.lyrics.LyricsSource?,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onLyricsOffsetChange: (Long) -> Unit,
    onRefreshLyrics: () -> Unit,
    onDismiss: () -> Unit,
) {
    val artworkUri = track?.artworkUri
    val surface = MaterialTheme.colorScheme.surface

    BackHandler { onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred artwork backdrop
        if (artworkUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    .size(512)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(52.dp)
                    .align(Alignment.Center),
                contentScale = ContentScale.Crop,
            )
        }

        // Gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to surface.copy(alpha = 0.88f),
                        0.45f to surface.copy(alpha = 0.62f),
                        1.0f to surface.copy(alpha = 0.96f),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (track == null) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    title = stringResource(R.string.lyrics_nothing),
                    hint = stringResource(R.string.lyrics_none),
                )
                return@Column
            }

            // Track info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track = track, size = 60, cornerRadius = 12, shadowElevation = 4)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Seek bar
            SeekBarRow(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
            )

            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.action_shuffle),
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = stringResource(R.string.action_repeat),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Offset controls (synced only)
            if (syncedLyrics.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_offset_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onLyricsOffsetChange(lyricsOffsetMs - 500L) }) {
                        Text(stringResource(R.string.lyrics_offset_earlier))
                    }
                    Text(
                        text = stringResource(
                            R.string.lyrics_offset_value,
                            if (lyricsOffsetMs == 0L) "0.0"
                            else "%+.1f".format(lyricsOffsetMs / 1000f),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    TextButton(onClick = { onLyricsOffsetChange(lyricsOffsetMs + 500L) }) {
                        Text(stringResource(R.string.lyrics_offset_later))
                    }
                    if (lyricsOffsetMs != 0L) {
                        TextButton(onClick = { onLyricsOffsetChange(0L) }) {
                            Text(stringResource(R.string.lyrics_offset_reset))
                        }
                    }
                }
            }

            // Source + refresh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                lyricsSource?.let { source ->
                    Text(
                        text = stringResource(R.string.lyrics_source, stringResource(source.labelRes())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefreshLyrics,
                    enabled = !isLoading,
                    modifier = Modifier.size(32.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.lyrics_refresh),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Lyrics content
            when {
                isLoading && syncedLyrics.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                syncedLyrics.isNotEmpty() -> {
                    SyncedLyrics(
                        lines = syncedLyrics,
                        activeIndex = activeLyricIndex,
                        activeWordIndex = activeWordIndex,
                        activeRows = activeLyricRows,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                lyrics != null -> {
                    Text(
                        text = lyrics,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                track.isLocal -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.lyrics_none_local),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.lyrics_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
