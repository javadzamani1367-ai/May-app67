package ir.minerfinder.scan

import ir.minerfinder.AppGraph

/**
 * The quantity recorded against the heading during a sweep, 0..100.
 *
 * Not the fused probability: its stability part needs 10–30 s and does not
 * change with the direction the phone faces. The level of the fan's harmonic
 * series above the floor does, by a few dB, as the body shadows the
 * microphone. Without audio the magnetometer's 50 Hz (or change) stands in.
 */
object SweepSignal {
    private const val FRESH_MS = 2_000L

    fun current(graph: AppGraph, nowMs: Long = System.currentTimeMillis()): Double? {
        graph.catalog.audio.frames.value?.takeIf { nowMs - it.timestampMs < FRESH_MS }?.let { f ->
            val partials = f.series?.partials
            val db = if (!partials.isNullOrEmpty()) partials.sumOf { it.peak.prominenceDb } / partials.size
            else f.peaks.firstOrNull()?.prominenceDb ?: 0.0
            return (db * 2).coerceIn(0.0, 100.0)
        }
        graph.catalog.magnetometer.frames.value?.takeIf { nowMs - it.timestampMs < FRESH_MS }?.let { f ->
            return f.ac50Ut?.let { (it * 50).coerceIn(0.0, 100.0) } ?: (f.deltaUt * 2.5).coerceIn(0.0, 100.0)
        }
        return null
    }
}
