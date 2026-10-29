package ir.minerfinder.core.dsp

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10

/**
 * Hann-windowed power spectrum restricted to one band.
 *
 * Power is scaled so a sine of amplitude A that falls on a bin reads A², which
 * makes levels comparable between FFT sizes and windows.
 */
class SpectrumAnalyzer(
    val sampleRate: Int,
    val size: Int,
    minHz: Double,
    maxHz: Double,
) {
    private val fft = Fft(size)
    private val window = Windows.hann(size)
    private val windowSum = window.sum()
    private val re = DoubleArray(size)
    private val im = DoubleArray(size)

    val binHz: Double = sampleRate.toDouble() / size
    val firstBin: Int = ceil(minHz / binHz).toInt().coerceAtLeast(1)
    val lastBin: Int = floor(maxHz / binHz).toInt().coerceAtMost(size / 2 - 1)
    val bandBins: Int = lastBin - firstBin + 1
    val firstHz: Double get() = firstBin * binHz

    fun frequencyOf(bandIndex: Double): Double = (firstBin + bandIndex) * binHz

    /** Band power of [samples] (at least [size] long, the last [size] samples are used). */
    @Synchronized
    fun power(samples: FloatArray): DoubleArray {
        require(samples.size >= size) { "need $size samples, got ${samples.size}" }
        val offset = samples.size - size
        for (i in 0 until size) {
            re[i] = samples[offset + i] * window[i]
            im[i] = 0.0
        }
        fft.transform(re, im)
        val scale = (2.0 / windowSum) * (2.0 / windowSum)
        return DoubleArray(bandBins) { i ->
            val k = firstBin + i
            (re[k] * re[k] + im[k] * im[k]) * scale
        }
    }

    companion object {
        const val SILENCE_DB = -200.0

        fun toDb(power: Double): Double = if (power > 1e-20) 10.0 * log10(power) else SILENCE_DB

        fun toDb(power: DoubleArray): DoubleArray = DoubleArray(power.size) { toDb(power[it]) }

        /** Median, the noise floor estimate: tonal peaks are few, so they barely move it. */
        fun median(values: DoubleArray): Double {
            if (values.isEmpty()) return SILENCE_DB
            val sorted = values.copyOf().also { it.sort() }
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }
}
