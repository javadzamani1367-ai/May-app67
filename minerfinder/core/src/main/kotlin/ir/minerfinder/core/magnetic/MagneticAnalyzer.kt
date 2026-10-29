package ir.minerfinder.core.magnetic

import ir.minerfinder.core.dsp.Goertzel
import kotlin.math.exp
import kotlin.math.sqrt

/** One magnetometer analysis step. Field values are in µT. */
data class MagneticFrame(
    val timestampMs: Long,
    val magnitude: Double,
    /** Change from the Earth's field: |B − baseline| (calibrated) or |B − slow average|. */
    val deltaUt: Double,
    /** Measured sampling rate of this phone's magnetometer, Hz. */
    val rateHz: Double,
    /** 50 Hz amplitude, or null when the sensor is too slow to see it. */
    val ac50Ut: Double?,
    val score: Double,
    val confidence: Double,
    val calibrated: Boolean,
)

/**
 * Phone magnetometer. Static changes (steel, cables carrying DC) say little on
 * their own; an alternating 50 Hz field near heavy wiring says more, but only
 * phones whose sensor runs well above 100 Hz can see it.
 */
class MagneticAnalyzer(
    /** Time constant of the high-pass used when no baseline is calibrated. */
    private val highPassTauS: Double = 2.0,
    /** Seconds of samples used for the 50 Hz estimate. */
    private val acWindowS: Double = 1.0,
) {
    private val times = ArrayDeque<Long>()
    private val xs = ArrayDeque<Double>()
    private val ys = ArrayDeque<Double>()
    private val zs = ArrayDeque<Double>()
    private var slow: DoubleArray? = null
    private var lastNanos = 0L
    private var baseline: DoubleArray? = null
    private var calibration: DoubleArray? = null
    private var calibrationCount = 0

    val isCalibrating: Boolean @Synchronized get() = calibration != null

    @Synchronized
    fun setBaseline(vector: DoubleArray?) {
        baseline = vector?.takeIf { it.size == 3 }
    }

    @Synchronized
    fun startCalibration() {
        calibration = DoubleArray(3)
        calibrationCount = 0
    }

    @Synchronized
    fun finishCalibration(): DoubleArray? {
        val sum = calibration ?: return null
        calibration = null
        if (calibrationCount == 0) return null
        return DoubleArray(3) { sum[it] / calibrationCount }.also { baseline = it }
    }

    /** Adds one sample; [timestampNanos] is the sensor event time. */
    @Synchronized
    fun add(timestampNanos: Long, x: Double, y: Double, z: Double) {
        calibration?.let { it[0] += x; it[1] += y; it[2] += z; calibrationCount++ }
        val s = slow
        if (s == null) {
            slow = doubleArrayOf(x, y, z)
        } else {
            val dt = ((timestampNanos - lastNanos) / 1e9).coerceIn(0.0, 1.0)
            val a = 1.0 - exp(-dt / highPassTauS)
            s[0] += a * (x - s[0]); s[1] += a * (y - s[1]); s[2] += a * (z - s[2])
        }
        lastNanos = timestampNanos
        times.addLast(timestampNanos); xs.addLast(x); ys.addLast(y); zs.addLast(z)
        val keepNanos = (acWindowS * 1e9).toLong()
        while (times.size > 2 && timestampNanos - times.first() > keepNanos) {
            times.removeFirst(); xs.removeFirst(); ys.removeFirst(); zs.removeFirst()
        }
    }

    @Synchronized
    fun frame(timestampMs: Long): MagneticFrame? {
        if (times.size < 2) return null
        val x = xs.last(); val y = ys.last(); val z = zs.last()
        val magnitude = sqrt(x * x + y * y + z * z)
        val ref = baseline ?: slow ?: return null
        val delta = sqrt(sq(x - ref[0]) + sq(y - ref[1]) + sq(z - ref[2]))

        val spanS = (times.last() - times.first()) / 1e9
        val rate = if (spanS > 0) (times.size - 1) / spanS else 0.0
        val ac50 = if (rate >= MIN_RATE_FOR_50HZ && spanS >= 0.5) ac50(rate) else null

        val anomaly = (delta / 40.0).coerceIn(0.0, 1.0)
        val score: Double
        val confidence: Double
        if (ac50 != null) {
            score = 100.0 * (0.65 * (ac50 / 2.0).coerceIn(0.0, 1.0) + 0.35 * anomaly)
            confidence = 0.8
        } else {
            score = 100.0 * anomaly
            confidence = 0.35
        }
        return MagneticFrame(timestampMs, magnitude, delta, rate, ac50, score.coerceIn(0.0, 100.0), confidence, baseline != null)
    }

    /**
     * The samples arrive unevenly spaced, so each axis is resampled onto a
     * uniform grid at the measured rate before Goertzel; the three axis
     * amplitudes combine as a vector.
     */
    private fun ac50(rate: Double): Double {
        val t0 = times.first()
        val n = ((times.last() - t0) / 1e9 * rate).toInt()
        if (n < 16) return 0.0
        val tNanos = times.toLongArray()
        var sumSq = 0.0
        for (axis in listOf(xs, ys, zs)) {
            val values = axis.toDoubleArray()
            val uniform = DoubleArray(n)
            var j = 0
            for (i in 0 until n) {
                val t = t0 + (i / rate * 1e9).toLong()
                while (j < tNanos.size - 2 && tNanos[j + 1] < t) j++
                val span = (tNanos[j + 1] - tNanos[j]).toDouble()
                val f = if (span > 0) ((t - tNanos[j]) / span).coerceIn(0.0, 1.0) else 0.0
                uniform[i] = values[j] + f * (values[j + 1] - values[j])
            }
            val mean = uniform.average()
            for (i in uniform.indices) uniform[i] -= mean
            val a = Goertzel.amplitude(uniform, rate, 50.0)
            sumSq += a * a
        }
        return sqrt(sumSq)
    }

    private fun sq(v: Double) = v * v

    companion object {
        /** Nyquist for 50 Hz is 100 Hz; a margin above it keeps aliasing out. */
        const val MIN_RATE_FOR_50HZ = 120.0
    }
}
