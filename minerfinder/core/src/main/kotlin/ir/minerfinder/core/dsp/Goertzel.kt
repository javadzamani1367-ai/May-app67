package ir.minerfinder.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Single-frequency DFT. Much cheaper than an FFT when only 50, 150 and 250 Hz
 * matter, and the target need not fall on a bin.
 *
 * Returns the amplitude: a sine of amplitude A reads ≈ A when the block holds
 * a whole number of its cycles (0.5 s at 50 Hz is 25 cycles).
 */
object Goertzel {

    fun amplitude(samples: DoubleArray, sampleRate: Double, freqHz: Double, from: Int = 0, count: Int = samples.size - from): Double =
        run(count, sampleRate, freqHz) { samples[from + it] }

    fun amplitude(samples: FloatArray, sampleRate: Double, freqHz: Double, from: Int = 0, count: Int = samples.size - from): Double =
        run(count, sampleRate, freqHz) { samples[from + it].toDouble() }

    private inline fun run(count: Int, sampleRate: Double, freqHz: Double, sample: (Int) -> Double): Double {
        if (count <= 0) return 0.0
        val coeff = 2.0 * cos(2.0 * PI * freqHz / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until count) {
            val s0 = sample(i) + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return 2.0 * sqrt(power.coerceAtLeast(0.0)) / count
    }
}
