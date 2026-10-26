package com.tuneplayer.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.AudioInfo
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.theme.ArtworkGradient
import com.tuneplayer.app.ui.haptics.Haptic
import com.tuneplayer.app.ui.haptics.rememberHaptics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.tuneplayer.app.ui.components.DownloadIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingSheet(
    track: Track,
    audioInfo: AudioInfo?,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
    lyricPreview: String?,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val playPauseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "playPauseScale",
    )
    val iconSize = 28.dp
    val sideButtonSize = 56.dp
    val centerButtonSize = 72.dp
    // BitChord's signature touch: the sleeve shrinks while paused, so the
    // play/pause state reads from across the room without looking at the icon.
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artScale",
    )

    // Backdrop tinted by the artwork's own colors, crossfaded when the track
    // changes so the sheet never snaps between two palettes.
    val context = LocalContext.current
    val gradientStops = remember { mutableStateOf<List<androidx.compose.ui.graphics.Color>?>(null) }
    LaunchedEffect(track.artworkUri) {
        val uri = track.artworkUri ?: return@LaunchedEffect
        val colors = runCatching { ArtworkGradient.fromUri(uri, context) }.getOrNull()
        gradientStops.value = colors
    }
    val surface = MaterialTheme.colorScheme.surface
    val topColor by animateColorAsState(
        targetValue = gradientStops.value?.firstOrNull()
            ?.let { it.copy(alpha = 0.55f) } ?: surface,
        animationSpec = tween(450),
        label = "gradientTop",
    )
    val bottomColor by animateColorAsState(
        targetValue = gradientStops.value?.getOrNull(1)
            ?.let { it.copy(alpha = 0.32f) } ?: surface,
        animationSpec = tween(450),
        label = "gradientBottom",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Artwork-derived gradient, softly blended into the sheet surface.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                topColor,
                                lerp(topColor, surface, 0.55f),
                                bottomColor,
                            ),
                            startY = 0f,
                            endY = 900f,
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Artwork(
                        track = track,
                        size = 264,
                        cornerRadius = 24,
                        shadowElevation = 16,
                        modifier = Modifier.graphicsLayer {
                            scaleX = artScale
                            scaleY = artScale
                        },
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val album = track.album?.trim().orEmpty()
                if (album.isNotEmpty() && !album.equals(track.artist, ignoreCase = true)) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!lyricPreview.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = onOpenLyrics)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = lyricPreview,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                val bitrateKbps = audioInfo?.bitrateKbps
                val sampleRateHz = audioInfo?.sampleRateHz
                val channelCount = audioInfo?.channelCount?.takeIf { it > 0 }
                val bitrateText = if (bitrateKbps != null) {
                    stringResource(R.string.audio_bitrate_value, bitrateKbps)
                } else {
                    null
                }
                val sampleRateText = if (sampleRateHz != null && sampleRateHz > 0) {
                    val value = if (sampleRateHz % 1000 == 0) {
                        (sampleRateHz / 1000).toString()
                    } else {
                        "%.1f".format(sampleRateHz / 1000f)
                    }
                    stringResource(R.string.audio_sample_rate_value, value)
                } else {
                    null
                }
                val channelText = channelCount?.let {
                    stringResource(R.string.audio_channels_value, it)
                }
                val audioMeta = listOfNotNull(
                    audioInfo?.codec,
                    bitrateText,
                    sampleRateText,
                    channelText,
                ).joinToString(" · ")
                if (audioMeta.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = audioMeta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                SeekBarRow(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    loading = isBuffering,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.action_shuffle),
                            modifier = Modifier.size(iconSize),
                            tint = if (isShuffleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(sideButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.action_skip_previous),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                            onTogglePlayPause()
                        },
                        modifier = Modifier
                            .size(centerButtonSize)
                            .graphicsLayer {
                                scaleX = playPauseScale
                                scaleY = playPauseScale
                            },
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.action_pause else R.string.action_play,
                            ),
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(sideButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.action_skip_next),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Default.RepeatOne
                            } else {
                                Icons.Default.Repeat
                            },
                            contentDescription = stringResource(R.string.action_repeat),
                            modifier = Modifier.size(iconSize),
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            haptics.play(if (isFavorite) Haptic.ToggleOff else Haptic.ToggleOn)
                            onToggleFavorite(track)
                        },
                    ) {
                        AnimatedIcon(isFavorite = isFavorite)
                    }
                    IconButton(onClick = onOpenLyrics) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = stringResource(R.string.action_lyrics),
                            modifier = Modifier.size(iconSize),
                        )
                    }
                    DownloadIconButton(track = track)
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = stringResource(R.string.tab_queue),
                            modifier = Modifier.size(iconSize),
                        )
                    }
                    IconButton(onClick = { onShare(track) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share),
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AnimatedIcon(isFavorite: Boolean, modifier: Modifier = Modifier) {
    var animatedIsFavorite by remember { mutableStateOf(isFavorite) }
    val animatedScale by animateFloatAsState(
        targetValue = if (animatedIsFavorite) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "favoriteScale",
    )
    LaunchedEffect(isFavorite) { animatedIsFavorite = isFavorite }
    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = animatedIsFavorite, animationSpec = tween(150)) { state ->
            when (state) {
                true -> Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.action_unfavorite),
                    tint = MaterialTheme.colorScheme.error,
                )
                false -> Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite_add),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
