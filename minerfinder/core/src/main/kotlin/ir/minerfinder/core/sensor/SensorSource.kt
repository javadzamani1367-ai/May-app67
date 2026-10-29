package ir.minerfinder.core.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * One source of evidence: the phone microphone, the magnetometer, an external
 * coil, and later an RTL-SDR over USB OTG or an ESP32 over BLE.
 *
 * The core knows nothing about any particular source. A new one implements
 * this interface and is registered in the app's source catalog; the engine,
 * the settings (weights) and the search screen pick it up from there.
 */
interface SensorSource {
    /** Stable key: weights and on/off settings are stored under it. Never change it once shipped. */
    val id: String

    /** Shown to the user. Android sources read it from string resources. */
    val label: String

    /** Latest reading, or null before the first one. */
    val readings: StateFlow<SourceReading?>

    val status: StateFlow<SourceStatus>

    /** Whether the hardware for this source exists right now (sensor present, device plugged in). */
    fun isAvailable(): Boolean

    /** Starts producing readings inside [scope]. Calling it twice is harmless. */
    fun start(scope: CoroutineScope)

    fun stop()
}

enum class SourceStatus { IDLE, RUNNING, UNAVAILABLE, ERROR }

/**
 * What every source reports.
 *
 * @param score 0..100: how strongly this source alone points at a miner.
 * @param confidence 0..1: how much the source trusts its own score (enough
 *   data collected, sensor fast enough, signal not clipped...).
 * @param raw named measurements for display, e.g. "f0Hz" or "ac50uT". The app
 *   labels the keys it knows and shows unknown keys as they are, so a new
 *   source can report anything without a change to the screens.
 * @param detail optional rich payload for a source's own screen (the audio
 *   source puts its whole spectrum frame here).
 */
data class SourceReading(
    val sourceId: String,
    val score: Float,
    val confidence: Float,
    val timestampMs: Long,
    val raw: Map<String, Double> = emptyMap(),
    val detail: Any? = null,
) {
    init {
        require(score in 0f..100f) { "score out of range: $score" }
        require(confidence in 0f..1f) { "confidence out of range: $confidence" }
    }
}
