package com.tuneplayer.app

import android.app.Application
import com.tuneplayer.app.data.local.LocalMusicRepository
import com.tuneplayer.app.data.prefs.FavoriteStore
import com.tuneplayer.app.data.prefs.SettingsStore
import com.tuneplayer.app.data.youtube.YouTubeMusicRepository

class TunePlayerApp : Application() {
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
}
