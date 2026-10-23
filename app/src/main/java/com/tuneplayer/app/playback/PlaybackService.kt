package com.tuneplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tuneplayer.app.TunePlayerApp
import com.tuneplayer.app.data.prefs.SettingsStore
import com.tuneplayer.app.ui.AudioEffects

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val settingsStore: SettingsStore
        get() = (application as TunePlayerApp).settingsStore

    /**
     * Effects live here, not in the UI: the ExoPlayer session belongs to this
     * service and keeps playing while the activity is gone. The UI only writes
     * preferences; this listener applies them to the live session.
     */
    private val audioSettingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyAudioSettings() }

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            applyAudioSettings()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // A previous service instance may have died without releasing its
        // effects. Start from a clean slate; sessions can be reused.
        AudioEffects.release()

        val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            "Referer" to "https://music.youtube.com/",
            "Origin" to "https://music.youtube.com",
            "Accept-Language" to "en-US,en;q=0.9",
        )

        // HTTP data source factory with default headers for YouTube
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(defaultHeaders)
            .setAllowCrossProtocolRedirects(true)

        // DataSource factory handles content:// (local), file://, http://, https://
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        this.player = player
        player.addListener(playerListener)
        settingsStore.registerListener(audioSettingsListener)
        applyAudioSettings()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, com.tuneplayer.app.MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    /**
     * Reads the shared settings and pushes them into the live player.
     *
     * Called after the session is created, when the platform changes the
     * audio session, and whenever the UI writes a preference. Calls before
     * the audio session exists are harmless: [AudioEffects.apply] releases
     * and waits for the next session callback.
     */
    private fun applyAudioSettings() {
        val player = player ?: return
        val settings = settingsStore
        player.skipSilenceEnabled = settings.skipSilence
        AudioEffects.apply(
            sessionId = player.audioSessionId,
            enabled = settings.equalizerEnabled,
            bass = settings.bassBoostLevel,
            virtualizerLevel = settings.virtualizerLevel,
            bands = settings.eqBands,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        settingsStore.unregisterListener(audioSettingsListener)
        player?.removeListener(playerListener)
        AudioEffects.release()
        mediaSession?.run {
            player.release()
            release()
        }
        player = null
        mediaSession = null
        super.onDestroy()
    }
}
