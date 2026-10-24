package com.tuneplayer.app

import android.app.Application
import com.tuneplayer.app.data.discord.DiscordRpcClient
import com.tuneplayer.app.data.downloads.OfflineDownloadManager
import com.tuneplayer.app.data.local.LocalMusicRepository
import com.tuneplayer.app.data.lyrics.LrclibLyricsRepository
import com.tuneplayer.app.data.lyrics.LyricsCache
import com.tuneplayer.app.data.lyrics.OvhLyricsRepository
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

    override fun onCreate() {
        super.onCreate()
        syncDiscordRpc()
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
        YouTubeMusicRepository().also { repository ->
            settingsStore.youtubeCookie?.let { cookie ->
                repository.applySession(
                    cookie = cookie,
                    dataSyncId = settingsStore.youtubeDataSyncId,
                    authUser = settingsStore.youtubeAuthUser,
                )
            }
        }
    }

    val lrclibLyricsRepository: LrclibLyricsRepository by lazy {
        LrclibLyricsRepository()
    }

    val ovhLyricsRepository: OvhLyricsRepository by lazy {
        OvhLyricsRepository()
    }

    val lyricsCache: LyricsCache by lazy {
        LyricsCache(this)
    }

    val offlineDownloadManager: OfflineDownloadManager by lazy {
        OfflineDownloadManager(this, youTubeMusicRepository)
    }

    val discordRpcClient: DiscordRpcClient by lazy {
        DiscordRpcClient()
    }

    /** Applies the toggle/token pair to the live gateway client. */
    fun syncDiscordRpc() {
        val enabled = settingsStore.discordRpcEnabled
        val token = settingsStore.discordToken
        if (enabled && token != null) {
            discordRpcClient.start(token)
        } else {
            discordRpcClient.stop()
        }
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
