package com.tuneplayer.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.prefs.PlaylistStore
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.ui.TunePlayerViewModel
import kotlinx.coroutines.launch

internal fun TunePlayerViewModel.playlists(): List<PlaylistStore.Playlist> = playlistStore.all()

/** Re-reads the store after a mutation so the UI sees the new list. */
internal fun TunePlayerViewModel.refreshPlaylists() {
    uiState = uiState.copy(playlists = playlistStore.all())
}

internal fun TunePlayerViewModel.createPlaylist(name: String): PlaylistStore.Playlist? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val created = playlistStore.create(trimmed)
    refreshPlaylists()
    LogCatcher.d(TAG, "createPlaylist: $trimmed")
    return created
}

internal fun TunePlayerViewModel.deletePlaylist(id: String) {
    playlistStore.delete(id)
    refreshPlaylists()
}

internal fun TunePlayerViewModel.renamePlaylist(id: String, name: String) {
    playlistStore.rename(id, name)
    refreshPlaylists()
}

internal fun TunePlayerViewModel.addToPlaylist(playlistId: String, track: Track) {
    playlistStore.addTrack(playlistId, track.id)
    refreshPlaylists()
    LogCatcher.d(TAG, "addToPlaylist: ${track.title} -> $playlistId")
}

internal fun TunePlayerViewModel.removeFromPlaylist(playlistId: String, trackId: String) {
    playlistStore.removeTrack(playlistId, trackId)
    refreshPlaylists()
}

internal fun TunePlayerViewModel.moveInPlaylist(playlistId: String, from: Int, to: Int) {
    playlistStore.moveTrack(playlistId, from, to)
    refreshPlaylists()
}

/** Bulk-adds everything currently queued; used by the playlist screen. */
internal fun TunePlayerViewModel.addQueueToPlaylist(playlistId: String) {
    uiState.playbackQueue.forEach { playlistStore.addTrack(playlistId, it.id) }
    refreshPlaylists()
}

/** Resolves playlist ids against the local library, downloads, and current search results. */
internal fun TunePlayerViewModel.tracksForPlaylist(playlist: PlaylistStore.Playlist): List<Track> {
    val known = (uiState.localTracks + uiState.downloadedTracks + uiState.searchResults)
        .associateBy { it.id }
    return playlist.trackIds.mapNotNull { known[it] }
}

internal fun TunePlayerViewModel.playPlaylist(playlist: PlaylistStore.Playlist, startIndex: Int = 0) {
    val tracks = tracksForPlaylist(playlist)
    if (tracks.isEmpty()) return
    val index = startIndex.coerceIn(tracks.indices)
    uiState = uiState.copy(playbackQueue = tracks, queueIndex = index)
    pushQueueToPlayer(tracks, index)
    val first = tracks[index]
    viewModelScope.launch { startTrack(first) }
}

internal fun TunePlayerViewModel.topTracks(limit: Int = 20): List<Track> {
    val known = (uiState.localTracks + uiState.downloadedTracks + uiState.searchResults)
        .associateBy { it.id }
    return playStatsStore.topIds(limit).mapNotNull { known[it] }
}

internal fun TunePlayerViewModel.totalPlays(): Int = playStatsStore.totalPlays()

internal fun TunePlayerViewModel.distinctTracks(): Int = playStatsStore.distinctTracks()
