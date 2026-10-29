package ir.minerfinder.core.dsp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * @param presence fraction of frames in the window in which a series was found.
 * @param consistency fraction of those whose f0 sits within [StabilityTracker.matchFraction] of the median.
 * @param driftFraction standard deviation of the consistent f0 values / median.
 * @param coverage how much of the window has been filled with frames (0..1).
 * @param score presence × consistency × steadiness, 0..1.
 */
data class Stability(
    val presence: Double,
    val consistency: Double,
    val driftFraction: Double,
    val coverage: Double,
    val medianF0: Double?,
    val score: Double,
)

/**
 * A miner's fans run day and night at a fixed speed: the same series, frame
 * after frame, for the whole window. Voices, traffic and passing machines come
 * and go or glide in pitch.
 */
class StabilityTracker(
    var windowMs: Long = 20_000L,
    private val matchFraction: Double = 0.03,
    private val maxDriftFraction: Double = 0.02,
) {
    private class Sample(val tMs: Long, val f0: Double?)

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun reset() = samples.clear()

    @Synchronized
    fun add(tMs: Long, f0: Double?): Stability {
        samples.addLast(Sample(tMs, f0))
        while (samples.isNotEmpty() && tMs - samples.first().tMs > windowMs) samples.removeFirst()
        return compute()
    }

    private fun compute(): Stability {
        if (samples.isEmpty()) return Stability(0.0, 0.0, 0.0, 0.0, null, 0.0)
        val span = samples.last().tMs - samples.first().tMs
        val coverage = (span.toDouble() / windowMs).coerceIn(0.0, 1.0)
        val present = samples.mapNotNull { it.f0 }
        val presence = present.size.toDouble() / samples.size
        if (present.isEmpty()) return Stability(0.0, 0.0, 0.0, coverage, null, 0.0)

        val median = present.sorted()[present.size / 2]
        val consistent = present.filter { abs(it - median) <= median * matchFraction }
        val consistency = consistent.size.toDouble() / present.size
        val mean = consistent.average()
        val sd = sqrt(consistent.sumOf { (it - mean) * (it - mean) } / consistent.size)
        val drift = sd / median
        val steadiness = (1.0 - drift / maxDriftFraction).coerceIn(0.0, 1.0)
        val score = presence * consistency * steadiness
        return Stability(presence, consistency, drift, coverage, median, score)
    }
}
