package com.tuneplayer.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.util.DownloadUiState
import com.tuneplayer.app.ui.util.LocalDownloadTrack
import com.tuneplayer.app.ui.util.LocalDownloadUiState
import com.tuneplayer.app.ui.util.LocalHapticsEnabled
import com.tuneplayer.app.ui.util.LocalNowPlayingId
import com.tuneplayer.app.ui.util.LocalRemoveDownload

/** Honors the haptics switch without threading a boolean through every row. */
internal val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Lets list rows highlight the track that is actually playing. */
internal val LocalNowPlayingId = staticCompositionLocalOf<String?> { null }

internal data class DownloadUiState(
    val downloadedIds: Set<String> = emptySet(),
    val progress: Map<String, Float> = emptyMap(),
)

internal val LocalDownloadUiState = compositionLocalOf { DownloadUiState() }
internal val LocalDownloadTrack = compositionLocalOf<(Track) -> Unit> { {} }
internal val LocalRemoveDownload = compositionLocalOf<(Track) -> Unit> { {} }
