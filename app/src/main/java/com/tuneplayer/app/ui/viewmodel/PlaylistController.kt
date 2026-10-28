package com.tuneplayer.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.tuneplayer.app.data.db.PlaylistEntity
import com.tuneplayer.app.data.db.SongEntity
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.ui.TunePlayerViewModel
import kotlinx.coroutines.launch
import com.tuneplayer.app.ui.TunePlayerViewModel.Companion.TAG

internal fun TunePlayerViewModel.refreshPlaylists() {
    viewModelScope.launch {
        runCatching {
            val snapshot = app.database.dao().playlistsSnapshot()
            uiState = uiState.copy(
                playlists = snapshot.map { it.playlist },
                playlistTrackIds = snapshot.associate { it.playlist.id to it.songIds },
            )
        }.onFailure { LogCatcher.e(TAG, "refreshPlaylists failed", it) }
    }
}

internal fun TunePlayerViewModel.createPlaylist(name: String): PlaylistEntity? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val id = "pl_${System.currentTimeMillis()}"
    viewModelScope.launch {
        runCatching {
            app.database.dao().insertPlaylist(
                PlaylistEntity(
                    id = id,
                    name = trimmed,
                    createdAt = System.currentTimeMillis(),
                    position = app.database.dao().nextPlaylistPosition(),
                ),
            )
            refreshPlaylists()
            LogCatcher.d(TAG, "createPlaylist: $trimmed")
        }.onFailure { LogCatcher.e(TAG, "createPlaylist failed", it) }
    }
    return PlaylistEntity(id = id, name = trimmed, createdAt = System.currentTimeMillis())
}

internal fun TunePlayerViewModel.deletePlaylist(id: String) {
    viewModelScope.launch {
        app.database.dao().deletePlaylist(id)
        refreshPlaylists()
    }
}

internal fun TunePlayerViewModel.renamePlaylist(id: String, name: String) {
    viewModelScope.launch {
        app.database.dao().renamePlaylist(id, name.trim())
        refreshPlaylists()
    }
}

internal fun TunePlayerViewModel.addToPlaylist(playlistId: String, track: Track) {
    viewModelScope.launch {
        runCatching {
            val dao = app.database.dao()
            dao.upsertSong(SongEntity.fromTrack(track))
            val next = (dao.maxPlaylistPosition(playlistId) ?: -1) + 1
            dao.insertPlaylistSongMaps(
                listOf(
                    com.tuneplayer.app.data.db.PlaylistSongMap(
                        playlistId = playlistId,
                        songId = track.id,
                        position = next,
                    ),
                ),
            )
            refreshPlaylists()
            LogCatcher.d(TAG, "addToPlaylist: ${track.title} -> $playlistId")
        }.onFailure { LogCatcher.e(TAG, "addToPlaylist failed", it) }
    }
}

internal fun TunePlayerViewModel.removeFromPlaylist(playlistId: String, trackId: String) {
    viewModelScope.launch {
        app.database.dao().removePlaylistSong(playlistId, trackId)
        refreshPlaylists()
    }
}

internal fun TunePlayerViewModel.moveInPlaylist(playlistId: String, from: Int, to: Int) {
    viewModelScope.launch {
        runCatching {
            val dao = app.database.dao()
            val ids = dao.playlistSongIds(playlistId).toMutableList()
            if (from !in ids.indices || to !in ids.indices) return@launch
            ids.add(to, ids.removeAt(from))
            dao.setPlaylistSongs(playlistId, ids)
            refreshPlaylists()
        }.onFailure { LogCatcher.e(TAG, "moveInPlaylist failed", it) }
    }
}

/** Bulk-adds everything currently queued; used by the playlist screen. */
internal fun TunePlayerViewModel.addQueueToPlaylist(playlistId: String) {
    val tracks = uiState.playbackQueue
    viewModelScope.launch {
        runCatching {
            val dao = app.database.dao()
            dao.upsertSongs(tracks.map { SongEntity.fromTrack(it) })
            var next = (dao.maxPlaylistPosition(playlistId) ?: -1) + 1
            val existing = dao.playlistSongIds(playlistId).toSet()
            val additions = tracks.filter { it.id !in existing }
                .map { PlaylistSongMapLite(playlistId, it.id) }
            dao.insertPlaylistSongMaps(
                additions.mapIndexed { i, item ->
                    com.tuneplayer.app.data.db.PlaylistSongMap(
                        playlistId = item.playlistId,
                        songId = item.songId,
                        position = next + i,
                    )
                },
            )
            refreshPlaylists()
        }.onFailure { LogCatcher.e(TAG, "addQueueToPlaylist failed", it) }
    }
}

private data class PlaylistSongMapLite(val playlistId: String, val songId: String)

/** Resolves playlist ids against the local library, downloads, and current search results. */
internal fun TunePlayerViewModel.tracksForPlaylist(playlist: PlaylistEntity): List<Track> {
    val known = (uiState.localTracks + uiState.downloadedTracks + uiState.searchResults)
        .associateBy { it.id }
    return uiState.playlistTrackIds[playlist.id].orEmpty().mapNotNull { known[it] }
}

internal fun TunePlayerViewModel.playPlaylist(playlist: PlaylistEntity, startIndex: Int = 0) {
    val ids = uiState.playlistTrackIds[playlist.id].orEmpty()
    if (ids.isEmpty()) return
    viewModelScope.launch {
        val dao = app.database.dao()
        val known = (uiState.localTracks + uiState.downloadedTracks + uiState.searchResults)
            .associateBy { it.id }
        val resolved = ids.mapNotNull { known[it] }
        if (resolved.isEmpty()) return@launch
        val index = startIndex.coerceIn(resolved.indices)
        uiState = uiState.copy(playbackQueue = resolved, queueIndex = index)
        pushQueueToPlayer(resolved, index)
        val first = resolved[index]
        startTrack(first)
    }
}

internal fun TunePlayerViewModel.topTracks(limit: Int = 20): List<Track> {
    val known = (uiState.localTracks + uiState.downloadedTracks + uiState.searchResults)
        .associateBy { it.id }
    return runCatching {
        kotlinx.coroutines.runBlocking {
            app.database.dao().topSongIds(limit)
        }
    }.getOrNull()?.mapNotNull { known[it] } ?: emptyList()
}

internal fun TunePlayerViewModel.totalPlays(): Int = runCatching {
    kotlinx.coroutines.runBlocking { app.database.dao().totalPlays() }
}.getOrDefault(0)

internal fun TunePlayerViewModel.distinctTracks(): Int = runCatching {
    kotlinx.coroutines.runBlocking {
        app.database.dao().allPlayCounts()
    }.distinctBy { it.songId }.size
}.getOrDefault(0)
