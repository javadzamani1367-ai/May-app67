package ir.minerfinder.core.dsp

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

data class Partial(val order: Int, val peak: Peak)

/**
 * A harmonic series. The fundamental counts as the first harmonic, so
 * [partials] of size 3 means e.g. f0, 2·f0 and 3·f0 were all found. The
 * fundamental itself may be missing (fans often radiate it weakly).
 *
 * @param coverage found partials / orders up to the highest one found:
 *   1.0 means no gaps in the series.
 */
data class HarmonicSeries(
    val fundamentalHz: Double,
    val partials: List<Partial>,
    val coverage: Double,
    val strength: Double,
) {
    val count: Int get() = partials.size
}

/**
 * Looks for a fan's blade-pass series: a fundamental between [minF0] and
 * [maxF0] with at least [minPartials] harmonics up to [maxHz].
 *
 * Candidates are every peak and its integer fractions (a missing or weak
 * fundamental still leaves the series). A candidate scores by the summed
 * prominence of its matched partials times coverage², which rejects both
 * octave errors: half the true f0 matches only every other order (coverage
 * 0.5), double the true f0 matches only half the partials.
 */
class HarmonicDetector(
    var minF0: Double = 300.0,
    var maxF0: Double = 1500.0,
    var maxHz: Double = 5000.0,
    var minPartials: Int = 3,
    private val toleranceFraction: Double = 0.012,
    private val minToleranceHz: Double = 9.0,
) {
    fun detect(peaks: List<Peak>): HarmonicSeries? {
        if (peaks.size < minPartials) return null
        val candidates = ArrayList<Double>()
        for (p in peaks) for (d in 1..4) {
            val c = p.freqHz / d
            if (c in minF0..maxF0 && candidates.none { abs(it - c) < tolerance(c) }) candidates += c
        }
        var best: HarmonicSeries? = null
        for (c in candidates) {
            val first = match(c, peaks) ?: continue
            val refined = refine(first)
            val series = match(refined, peaks) ?: first
            if (series.count >= minPartials && (best == null || series.strength > best.strength)) best = series
        }
        return best
    }

    private fun tolerance(freq: Double) = max(minToleranceHz, freq * toleranceFraction)

    private fun match(f0: Double, peaks: List<Peak>): HarmonicSeries? {
        val maxOrder = floor(maxHz / f0).toInt()
        if (maxOrder < 1) return null
        val partials = ArrayList<Partial>()
        for (k in 1..maxOrder) {
            val target = k * f0
            val tol = tolerance(target)
            val hit = peaks.filter { abs(it.freqHz - target) <= tol }.maxByOrNull { it.prominenceDb } ?: continue
            partials += Partial(k, hit)
        }
        if (partials.isEmpty()) return null
        val coverage = partials.size.toDouble() / partials.last().order
        val strength = partials.sumOf { it.peak.prominenceDb } * coverage * coverage
        return HarmonicSeries(f0, partials, coverage, strength)
    }

    /** Least-squares f0 through the origin: Σ k·f / Σ k². */
    private fun refine(series: HarmonicSeries): Double {
        val num = series.partials.sumOf { it.order * it.peak.freqHz }
        val den = series.partials.sumOf { (it.order * it.order).toDouble() }
        return num / den
    }
}
