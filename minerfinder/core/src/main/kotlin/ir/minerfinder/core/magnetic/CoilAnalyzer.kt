package ir.minerfinder.core.magnetic

import ir.minerfinder.core.dsp.Goertzel
import kotlin.math.log10
import kotlin.math.sqrt

/** Mains levels picked up by an external coil, one block. Amplitudes are in input full-scale units. */
data class CoilFrame(
    val timestampMs: Long,
    val a50: Double,
    val a150: Double,
    val a250: Double,
    /** Level of 50 Hz above the calibrated baseline (dB), or dBFS when not calibrated. */
    val levelDb: Double,
    /** Coefficient of variation of the 50 Hz level over the window: low means a steady load. */
    val variation: Double,
    /** (a150 + a250) / a50: rectifier and switching supplies make these strong. */
    val harmonicRatio: Double,
    val heavy: Double,
    val steady: Double,
    val score: Double,
    val confidence: Double,
    val calibrated: Boolean,
)

/**
 * External coil on the audio input (microphone jack or USB sound card).
 *
 * The main criterion is a heavy *and steady* consumption: a mining farm draws
 * nearly the same current all day, a house or workshop swings. 150 and 250 Hz
 * (3rd and 5th harmonics of the mains) add a little, since the miners' power
 * supplies produce them.
 */
class CoilAnalyzer(
    val sampleRate: Int = 48_000,
    var windowMs: Long = 20_000L,
) {
    /** Half a second: a whole number of cycles of 50, 150 and 250 Hz. */
    val blockSize: Int = sampleRate / 2

    private data class Level(val tMs: Long, val a50: Double)

    private val history = ArrayDeque<Level>()
    private var baseline: Double? = null
    private var calibrationSum = 0.0
    private var calibrationCount = -1

    val isCalibrating: Boolean @Synchronized get() = calibrationCount >= 0

    @Synchronized
    fun setBaseline(value: Double?) {
        baseline = value?.takeIf { it > 0 }
    }

    @Synchronized
    fun startCalibration() {
        calibrationSum = 0.0
        calibrationCount = 0
    }

    @Synchronized
    fun finishCalibration(): Double? {
        val count = calibrationCount
        calibrationCount = -1
        if (count <= 0) return null
        baseline = (calibrationSum / count).coerceAtLeast(1e-9)
        history.clear()
        return baseline
    }

    /** Analyses one block of [blockSize] samples. */
    @Synchronized
    fun process(block: FloatArray, timestampMs: Long): CoilFrame {
        val rate = sampleRate.toDouble()
        val a50 = Goertzel.amplitude(block, rate, 50.0)
        val a150 = Goertzel.amplitude(block, rate, 150.0)
        val a250 = Goertzel.amplitude(block, rate, 250.0)
        if (calibrationCount >= 0) {
            calibrationSum += a50
            calibrationCount++
        }

        history.addLast(Level(timestampMs, a50))
        while (history.isNotEmpty() && timestampMs - history.first().tMs > windowMs) history.removeFirst()
        val mean = history.sumOf { it.a50 } / history.size
        val sd = sqrt(history.sumOf { (it.a50 - mean) * (it.a50 - mean) } / history.size)
        val variation = if (mean > 1e-12) sd / mean else 1.0

        val base = baseline
        val levelDb = if (base != null) db(mean / base) else db(mean)
        // Calibrated: 0 dB above ambient is nothing, 30 dB is a lot. Not
        // calibrated: −70 dBFS is noise, −25 dBFS a strong field.
        val heavy = if (base != null) (levelDb / 30.0).coerceIn(0.0, 1.0) else ((levelDb + 70.0) / 45.0).coerceIn(0.0, 1.0)
        val steady = (1.0 - variation / 0.25).coerceIn(0.0, 1.0)
        val harmonicRatio = if (a50 > 1e-12) (a150 + a250) / a50 else 0.0
        val score = 100.0 * (0.5 * heavy + 0.35 * steady * heavy + 0.15 * harmonicRatio.coerceIn(0.0, 1.0) * heavy)
        val span = history.last().tMs - history.first().tMs
        val confidence = (0.3 + 0.7 * (span.toDouble() / windowMs)).coerceIn(0.0, 1.0) * (if (base != null) 1.0 else 0.7)

        return CoilFrame(timestampMs, a50, a150, a250, levelDb, variation, harmonicRatio, heavy, steady,
            score.coerceIn(0.0, 100.0), confidence, base != null)
    }

    @Synchronized
    fun reset() = history.clear()

    private fun db(ratio: Double) = if (ratio > 1e-12) 20.0 * log10(ratio) else -240.0
}
