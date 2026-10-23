package com.tuneplayer.app

import android.app.Application
import com.tuneplayer.app.data.local.LocalMusicRepository
import com.tuneplayer.app.data.prefs.FavoriteStore
import com.tuneplayer.app.data.prefs.SettingsStore
import com.tuneplayer.app.data.youtube.YouTubeMusicRepository
import com.tuneplayer.app.data.prefs.PlayStatsStore
import com.tuneplayer.app.data.prefs.PlaylistStore
import com.tuneplayer.app.data.prefs.QueueStore
import com.tuneplayer.app.log.LogCatcher

class TunePlayerApp : Application() {
    init {
        // First thing in the process: uncaught exceptions land in the log
        // buffer the Diagnostics screen reads, instead of vanishing.
        LogCatcher.install()
    }

    val localMusicRepository: LocalMusicRepository by lazy {
        LocalMusicRepository(this)
    }

    val favoriteStore: FavoriteStore by lazy {
        FavoriteStore(this)
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(this)
    }

    val youTubeMusicRepository: YouTubeMusicRepository by lazy {
        YouTubeMusicRepository()
    }

    val playlistStore: PlaylistStore by lazy {
        PlaylistStore(this)
    }

    val playStatsStore: PlayStatsStore by lazy {
        PlayStatsStore(this)
    }

    val queueStore: QueueStore by lazy {
        QueueStore(this)
    }
}
