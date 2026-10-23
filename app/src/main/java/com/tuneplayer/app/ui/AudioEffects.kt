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
 *
 * Band levels are in millibels, the unit the platform effect uses; the UI
 * never converts them, so band positions survive device changes.
 */
object AudioEffects {

    /** One equalizer band as reported by the platform. */
    data class Band(
        val index: Int,
        val centerFreqHz: Int,
        val levelMb: Int,
        val minMb: Int,
        val maxMb: Int,
    )

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var attachedSessionId: Int? = null

    @Synchronized
    fun apply(
        sessionId: Int,
        enabled: Boolean,
        bass: Int,
        virtualizerLevel: Int,
        bands: List<Int> = emptyList(),
    ) {
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
        bands.forEachIndexed { index, level -> setBandLevel(index, level) }
    }

    /** Both BassBoost and Virtualizer use the platform short range 0..1000. */
    private fun levelToStrength(level: Int): Short = (level.coerceIn(0, 100) * 10).toShort()

    @Synchronized
    fun bands(): List<Band> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            (0 until eq.numberOfBands.toInt()).map { i ->
                val band = i.toShort()
                Band(
                    index = i,
                    // getCenterFreq reports milliHertz.
                    centerFreqHz = eq.getCenterFreq(band) / 1000,
                    levelMb = eq.getBandLevel(band).toInt(),
                    minMb = min,
                    maxMb = max,
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun setBandLevel(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
        runCatching {
            val range = eq.bandLevelRange
            val clamped = levelMb.coerceIn(range[0].toInt(), range[1].toInt())
            eq.setBandLevel(index.toShort(), clamped.toShort())
        }
    }

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
