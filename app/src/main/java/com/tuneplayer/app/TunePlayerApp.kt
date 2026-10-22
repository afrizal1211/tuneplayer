package com.tuneplayer.app

import android.app.Application
import com.tuneplayer.app.data.local.LocalMusicRepository
import com.tuneplayer.app.data.youtube.YouTubeMusicRepository

class TunePlayerApp : Application() {
    val localMusicRepository: LocalMusicRepository by lazy {
        LocalMusicRepository(this)
    }

    val youTubeMusicRepository: YouTubeMusicRepository by lazy {
        YouTubeMusicRepository()
    }
}
