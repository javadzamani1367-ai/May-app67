package ir.minerfinder.core.acoustic

import ir.minerfinder.core.dsp.HarmonicDetector
import ir.minerfinder.core.dsp.PeakFinder
import ir.minerfinder.core.dsp.SpectrumAnalyzer
import ir.minerfinder.core.dsp.StabilityTracker
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The miner-fan detector: FFT → median noise floor → tonal peaks → harmonic
 * series → stability over the window → one 0..100 score.
 *
 * Score weights: tonal 20%, harmonic 40%, stability 40%. Stability needs the
 * window to fill, so a fresh detection climbs over the first seconds; the
 * reported confidence says how full the window is.
 */
class AcousticAnalyzer(config: AcousticConfig = AcousticConfig()) {

    var config: AcousticConfig = config
        private set

    private var spectrum = SpectrumAnalyzer(config.sampleRate, config.fftSize, config.minHz, config.maxHz)
    private val peakFinder = PeakFinder(config.peakThresholdDb)
    private val harmonics = HarmonicDetector(config.minF0, config.maxF0, config.maxHz, config.minPartials)
    private val stability = StabilityTracker(config.stabilityWindowMs)

    private var averaged: DoubleArray? = null
    private var noiseProfile: DoubleArray? = null
    private var calibrationSum: DoubleArray? = null
    private var calibrationFrames = 0

    val binHz: Double get() = spectrum.binHz
    val firstHz: Double get() = spectrum.firstHz
    val bandBins: Int get() = spectrum.bandBins
    val isCalibrating: Boolean @Synchronized get() = calibrationSum != null

    @Synchronized
    fun reconfigure(newConfig: AcousticConfig) {
        val spectral = newConfig.sampleRate != config.sampleRate || newConfig.fftSize != config.fftSize ||
            newConfig.minHz != config.minHz || newConfig.maxHz != config.maxHz
        config = newConfig
        if (spectral) {
            spectrum = SpectrumAnalyzer(newConfig.sampleRate, newConfig.fftSize, newConfig.minHz, newConfig.maxHz)
            noiseProfile = null
            averaged = null
        }
        peakFinder.minProminenceDb = newConfig.peakThresholdDb
        harmonics.minF0 = newConfig.minF0
        harmonics.maxF0 = newConfig.maxF0
        harmonics.maxHz = newConfig.maxHz
        harmonics.minPartials = newConfig.minPartials
        stability.windowMs = newConfig.stabilityWindowMs
        stability.reset()
    }

    /** Starts recording the ambient profile; frames keep being analysed meanwhile. */
    @Synchronized
    fun startCalibration() {
        calibrationSum = DoubleArray(spectrum.bandBins)
        calibrationFrames = 0
    }

    /** Ends calibration and returns the profile (band power), or null if no frame arrived. */
    @Synchronized
    fun finishCalibration(): DoubleArray? {
        val sum = calibrationSum ?: return null
        calibrationSum = null
        if (calibrationFrames == 0) return null
        val profile = DoubleArray(sum.size) { sum[it] / calibrationFrames }
        noiseProfile = profile
        stability.reset()
        return profile
    }

    /** Restores a stored profile; ignored if it was made with another band layout. */
    @Synchronized
    fun setNoiseProfile(profile: DoubleArray?) {
        noiseProfile = profile?.takeIf { it.size == spectrum.bandBins }
        stability.reset()
    }

    @Synchronized
    fun reset() {
        averaged = null
        stability.reset()
    }

    /** Analyses the last [AcousticConfig.fftSize] samples of [samples]. */
    @Synchronized
    fun process(samples: FloatArray, timestampMs: Long): AcousticFrame {
        val power = spectrum.power(samples)
        calibrationSum?.let { sum ->
            for (i in sum.indices) sum[i] += power[i]
            calibrationFrames++
        }

        val alpha = 1.0 / config.averagingFrames.coerceAtLeast(1)
        val avg = averaged?.also { a -> for (i in a.indices) a[i] += alpha * (power[i] - a[i]) } ?: power.copyOf()
        averaged = avg

        val profile = noiseProfile
        val clean = if (profile == null) avg.copyOf() else {
            // Whatever the ambient profile explains drops to a flat residual at
            // half the median level, so the floor still exists after
            // subtraction and only what is new can stand out of it.
            val residual = 0.5 * SpectrumAnalyzer.median(avg)
            DoubleArray(avg.size) { maxOf(avg[it] - config.subtractionFactor * profile[it], residual) }
        }

        val cleanDb = SpectrumAnalyzer.toDb(clean)
        val floor = SpectrumAnalyzer.median(cleanDb)
        val peaks = peakFinder.find(cleanDb, floor, spectrum.firstHz, spectrum.binHz)
        val series = harmonics.detect(peaks)
        val stab = stability.add(timestampMs, series?.fundamentalHz)

        val top = peaks.take(3)
        val meanProminence = if (top.isEmpty()) 0.0 else top.sumOf { it.prominenceDb } / top.size
        val tonal = ((meanProminence - config.peakThresholdDb) / 20.0 + 0.25).coerceIn(0.0, 1.0)
            .let { if (top.isEmpty()) 0.0 else it }
        val harmonic = if (series == null) 0.0 else
            0.5 * ((series.count - 2) / 3.0).coerceIn(0.0, 1.0) + 0.5 * series.coverage
        val score = 100.0 * (0.2 * tonal + 0.4 * harmonic + 0.4 * stab.score)
        val confidence = 0.3 + 0.7 * stab.coverage

        return AcousticFrame(
            timestampMs = timestampMs,
            firstHz = spectrum.firstHz,
            binHz = spectrum.binHz,
            rawDb = FloatArray(power.size) { SpectrumAnalyzer.toDb(power[it]).toFloat() },
            cleanDb = FloatArray(cleanDb.size) { cleanDb[it].toFloat() },
            floorDb = floor,
            peaks = peaks,
            series = series,
            stability = stab,
            tonalScore = tonal,
            harmonicScore = harmonic,
            score = score.coerceIn(0.0, 100.0),
            confidence = confidence.coerceIn(0.0, 1.0),
            calibrated = profile != null,
            levelDbfs = rms(samples),
        )
    }

    private fun rms(samples: FloatArray): Double {
        val n = config.fftSize.coerceAtMost(samples.size)
        var sum = 0.0
        for (i in samples.size - n until samples.size) sum += samples[i].toDouble() * samples[i]
        val rms = sqrt(sum / n)
        return if (rms > 1e-10) 20.0 * log10(rms) else -200.0
    }
}
