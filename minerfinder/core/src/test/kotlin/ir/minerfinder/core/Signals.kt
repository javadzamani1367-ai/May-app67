package ir.minerfinder.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/** Synthetic test signals, generated as one continuous stream so phase never jumps between chunks. */
class Signals(val sampleRate: Int = 48_000, seed: Long = 42) {
    private val random = Random(seed)
    private var n = 0L

    /** Next [count] samples of Σ amplitude·sin(2π·f·t) plus Gaussian noise of standard deviation [noise]. */
    fun tones(count: Int, tones: List<Pair<Double, Double>>, noise: Double): FloatArray =
        FloatArray(count) {
            val t = (n + it).toDouble() / sampleRate
            var v = 0.0
            for ((f, a) in tones) v += a * sin(2 * PI * f * t)
            (v + noise * random.nextGaussian()).toFloat()
        }.also { n += count }

    fun noise(count: Int, sd: Double): FloatArray = tones(count, emptyList(), sd)
}
