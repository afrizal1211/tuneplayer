package com.tuneplayer.app.ui

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

/**
 * Ties audio effects to one ExoPlayer audio session.
 *
 * Session IDs are reused by the platform, so effects are released before a new
 * one is attached. Levels are 0..100 from the UI and mapped to the effect's own
 * strength range.
 */
object AudioEffects {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var attachedSessionId: Int? = null

    @Synchronized
    fun apply(sessionId: Int, enabled: Boolean, bass: Int, virtualizerLevel: Int) {
        if (!enabled) {
            release()
            return
        }
        if (attachedSessionId != sessionId) {
            release()
            equalizer = runCatching { Equalizer(0, sessionId).apply { this.enabled = true } }.getOrNull()
            bassBoost = runCatching { BassBoost(0, sessionId).apply { this.enabled = true } }.getOrNull()
            this.virtualizer =
                runCatching { Virtualizer(0, sessionId).apply { this.enabled = true } }.getOrNull()
            attachedSessionId = sessionId
        }
        bassBoost?.let { if (it.strengthSupported) it.setStrength(levelToStrength(bass)) }
        this.virtualizer?.let { if (it.strengthSupported) it.setStrength(levelToStrength(virtualizerLevel)) }
    }

    /** Both BassBoost and Virtualizer use the platform short range 0..1000. */
    private fun levelToStrength(level: Int): Short = (level.coerceIn(0, 100) * 10).toShort()

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        attachedSessionId = null
    }
}
