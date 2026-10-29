package ir.minerfinder.core.direction

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Estimated direction of the source. [sharpness] 0..1: how much the peak stands out of the circle. */
data class SweepEstimate(val bearingDeg: Double, val sharpness: Double, val peakScore: Double)

/**
 * Sweep mode: the user turns slowly through 360° while the score is recorded
 * against the compass heading. The phone's body (and the user's) shadows the
 * microphone, so the score is highest facing the source.
 */
class SweepAnalyzer(val binCount: Int = 36) {
    private val sums = DoubleArray(binCount)
    private val counts = IntArray(binCount)
    val binDeg: Double = 360.0 / binCount

    @Synchronized
    fun reset() {
        sums.fill(0.0)
        counts.fill(0)
    }

    @Synchronized
    fun add(headingDeg: Double, score: Double) {
        val bin = floor(normalize(headingDeg) / binDeg).toInt().coerceIn(0, binCount - 1)
        sums[bin] += score
        counts[bin]++
    }

    /** Mean score per bin; NaN where nothing was recorded. */
    @Synchronized
    fun bins(): DoubleArray = DoubleArray(binCount) { if (counts[it] > 0) sums[it] / counts[it] else Double.NaN }

    /** Fraction of the circle covered so far. */
    @Synchronized
    fun coverage(): Double = counts.count { it > 0 }.toDouble() / binCount

    /**
     * Needs [minCoverage] of the circle. The bins are smoothed over their
     * neighbours, and the bearing is the weighted circular mean of the bins
     * within ±45° of the strongest one (weights above the circle's minimum).
     */
    fun estimate(minCoverage: Double = 0.6): SweepEstimate? {
        val raw = bins()
        if (coverage() < minCoverage) return null
        val smooth = DoubleArray(binCount) { i ->
            var sum = 0.0
            var n = 0.0
            for (d in -1..1) {
                val v = raw[(i + d + binCount) % binCount]
                if (!v.isNaN()) {
                    val w = if (d == 0) 2.0 else 1.0
                    sum += v * w; n += w
                }
            }
            if (n > 0) sum / n else Double.NaN
        }
        val filled = smooth.filter { !it.isNaN() }
        val min = filled.minOrNull() ?: return null
        val max = filled.maxOrNull() ?: return null
        val peak = smooth.indices.filter { !smooth[it].isNaN() }.maxByOrNull { smooth[it] } ?: return null
        val reach = (45.0 / binDeg).toInt()
        var sx = 0.0
        var sy = 0.0
        for (d in -reach..reach) {
            val i = (peak + d + binCount) % binCount
            val v = smooth[i]
            if (v.isNaN()) continue
            val w = v - min
            val angle = (i + 0.5) * binDeg * PI / 180.0
            sx += w * sin(angle); sy += w * cos(angle)
        }
        val bearing = if (sx == 0.0 && sy == 0.0) (peak + 0.5) * binDeg else normalize(atan2(sx, sy) * 180.0 / PI)
        val mean = filled.average()
        val sharpness = if (max > 1e-9) ((max - mean) / max).coerceIn(0.0, 1.0) else 0.0
        return SweepEstimate(bearing, sharpness, max)
    }

    companion object {
        fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
    }
}
