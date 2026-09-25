package ir.roozban.learning

import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/** A finished task with both an estimate and recorded time. [category] is its project (or null). */
data class EstimateSample(val category: String?, val estimateMinutes: Int, val actualMinutes: Double, val at: Instant)

data class Factor(val value: Double, val samples: Int)

/** How much longer (>1) or shorter (<1) tasks take than estimated, overall and per category. */
data class EstimateFactors(val global: Factor, val byCategory: Map<String, Factor>) {
    fun factorFor(category: String?): Double = category?.let { byCategory[it]?.value } ?: global.value

    fun correct(estimateMinutes: Int, category: String?): Int = (estimateMinutes * factorFor(category)).toInt().coerceIn(5, 12 * 60)

    companion object {
        val NONE = EstimateFactors(Factor(1.0, 0), emptyMap())
    }
}

/**
 * Estimate correction: the exponentially weighted mean of log(actual / estimate), per category,
 * shrunk toward the overall mean (and that toward 0, i.e. factor 1) so a few samples cannot make
 * it jump. The factor is exp(mean).
 */
object EstimateCorrection {
    /** Log ratios are clamped to 4× either way: a forgotten timer should not dominate. */
    private val CLAMP = ln(4.0)

    fun fit(
        samples: List<EstimateSample>,
        now: Instant,
        halfLifeDays: Double = 30.0,
        globalPrior: Double = 3.0,
        categoryPrior: Double = 5.0,
    ): EstimateFactors {
        val usable = samples.filter { it.estimateMinutes > 0 && it.actualMinutes >= 1.0 }
        if (usable.isEmpty()) return EstimateFactors.NONE
        fun weight(s: EstimateSample) = 0.5.pow(Duration.between(s.at, now).toHours().coerceAtLeast(0) / 24.0 / halfLifeDays)
        fun logRatio(s: EstimateSample) = ln(s.actualMinutes / s.estimateMinutes).coerceIn(-CLAMP, CLAMP)

        var sw = 0.0
        var swx = 0.0
        usable.forEach {
            val w = weight(it)
            sw += w
            swx += w * logRatio(it)
        }
        val globalMean = swx / (sw + globalPrior)
        val byCategory = usable.filter { it.category != null }.groupBy { it.category!! }.mapValues { (_, list) ->
            var cw = 0.0
            var cwx = 0.0
            list.forEach {
                val w = weight(it)
                cw += w
                cwx += w * logRatio(it)
            }
            Factor(exp((cwx + categoryPrior * globalMean) / (cw + categoryPrior)), list.size)
        }
        return EstimateFactors(Factor(exp(globalMean), usable.size), byCategory)
    }
}
