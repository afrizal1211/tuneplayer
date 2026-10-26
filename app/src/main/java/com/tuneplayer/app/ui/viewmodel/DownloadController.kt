package com.tuneplayer.app.ui.viewmodel

import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.TunePlayerViewModel

internal fun TunePlayerViewModel.downloadTrack(track: Track) {
    app.offlineDownloadManager.download(track, uiState.youtubeAudioQuality)
}

internal fun TunePlayerViewModel.removeDownload(track: Track) {
    app.offlineDownloadManager.remove(track.id)
}

internal fun TunePlayerViewModel.clearDownloads() {
    app.offlineDownloadManager.clearAll()
}
