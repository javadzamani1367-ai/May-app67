package ir.minerfinder.core.feedback

import kotlin.math.pow

/**
 * Geiger-style feedback: the closer to the source (higher score), the faster
 * the clicks. Exponential, so the change is audible across the whole range.
 */
object ProximityMapper {
    const val SLOWEST_MS = 1500.0
    const val FASTEST_MS = 60.0

    /** Pause between beeps for [score] 0..100, or null below [silentBelow]. */
    fun beepIntervalMs(score: Float, silentBelow: Float = 5f): Long? {
        if (score < silentBelow) return null
        val t = (score / 100.0).coerceIn(0.0, 1.0)
        return (SLOWEST_MS * (FASTEST_MS / SLOWEST_MS).pow(t)).toLong()
    }

    /** Vibration pulse length for [score], 0 below [threshold]. */
    fun vibrationMs(score: Float, threshold: Float): Long =
        if (score < threshold) 0L else (20 + (score - threshold) / (100f - threshold).coerceAtLeast(1f) * 60).toLong()

    /** 0 = cold (blue), 1 = hot (red), with a dead zone at the bottom so noise stays cold. */
    fun heat(score: Float): Float = ((score - 10f) / 80f).coerceIn(0f, 1f)
}
