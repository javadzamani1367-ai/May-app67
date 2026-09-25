package ir.roozban.learning

import java.time.DayOfWeek
import kotlin.math.exp
import kotlin.math.ln

/** What is known about a task when it is planned. */
data class TaskFeatures(
    val important: Boolean,
    val urgent: Boolean,
    val estimateMinutes: Int?,
    /** Days between creating the task and its due date. */
    val leadDays: Long,
    val dueDay: DayOfWeek,
    val hasTime: Boolean,
    val recurring: Boolean,
    val hasProject: Boolean,
    /** How often this task's category ran late before (smoothed), 0..1. */
    val categoryLateRate: Double,
)

/** A past task with a due date and whether it was finished after that date. */
data class LateSample(val features: TaskFeatures, val late: Boolean)

/**
 * Probability that a task ends up done late (or not at all by its date). A small logistic
 * regression trained on the phone by gradient descent with L2; below [MIN_SAMPLES] samples a
 * Beta prior on the plain late rate is used instead.
 */
sealed interface OverdueModel {
    fun probability(f: TaskFeatures): Double

    data class Prior(val lateRate: Double, val samples: Int) : OverdueModel {
        override fun probability(f: TaskFeatures) = lateRate
    }

    class Logistic(val weights: DoubleArray, val samples: Int) : OverdueModel {
        override fun probability(f: TaskFeatures) = sigmoid(dot(weights, OverdueRisk.vector(f)))
    }

    companion object {
        internal fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))

        internal fun dot(a: DoubleArray, b: DoubleArray): Double {
            var s = 0.0
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }
}

object OverdueRisk {
    const val MIN_SAMPLES = 30

    /** Beta(2, 6): about one task in four runs late until the user's own data says otherwise. */
    private const val PRIOR_LATE = 2.0
    private const val PRIOR_ON_TIME = 6.0

    fun vector(f: TaskFeatures): DoubleArray = doubleArrayOf(
        1.0,
        if (f.important) 1.0 else 0.0,
        if (f.urgent) 1.0 else 0.0,
        ln(1.0 + (f.estimateMinutes ?: 30)) / 5.0,
        if (f.estimateMinutes == null) 1.0 else 0.0,
        ln(1.0 + f.leadDays.coerceAtLeast(0)) / 3.0,
        // The Iranian weekend.
        if (f.dueDay == DayOfWeek.THURSDAY || f.dueDay == DayOfWeek.FRIDAY) 1.0 else 0.0,
        if (f.hasTime) 1.0 else 0.0,
        if (f.recurring) 1.0 else 0.0,
        if (f.hasProject) 1.0 else 0.0,
        f.categoryLateRate,
    )

    fun fit(samples: List<LateSample>, l2: Double = 0.05, rate: Double = 0.5, iterations: Int = 400): OverdueModel {
        val late = samples.count { it.late }
        if (samples.size < MIN_SAMPLES) {
            return OverdueModel.Prior((late + PRIOR_LATE) / (samples.size + PRIOR_LATE + PRIOR_ON_TIME), samples.size)
        }
        val xs = samples.map { vector(it.features) }
        val ys = samples.map { if (it.late) 1.0 else 0.0 }
        val w = DoubleArray(xs[0].size)
        // Start from the plain late rate so the bias is right from the first step.
        val base = (late + PRIOR_LATE) / (samples.size + PRIOR_LATE + PRIOR_ON_TIME)
        w[0] = ln(base / (1 - base))
        val n = xs.size.toDouble()
        repeat(iterations) {
            val grad = DoubleArray(w.size)
            for (i in xs.indices) {
                val err = OverdueModel.sigmoid(OverdueModel.dot(w, xs[i])) - ys[i]
                for (j in w.indices) grad[j] += err * xs[i][j]
            }
            for (j in w.indices) {
                val reg = if (j == 0) 0.0 else l2 * w[j]
                w[j] -= rate * (grad[j] / n + reg)
            }
        }
        return OverdueModel.Logistic(w, samples.size)
    }

    /** Late rate per category, shrunk toward the overall rate. */
    fun categoryRates(labelled: List<Pair<String?, Boolean>>, strength: Double = 5.0): Map<String, Double> {
        if (labelled.isEmpty()) return emptyMap()
        val overall = (labelled.count { it.second } + PRIOR_LATE) / (labelled.size + PRIOR_LATE + PRIOR_ON_TIME)
        return labelled.filter { it.first != null }.groupBy { it.first!! }.mapValues { (_, l) ->
            (l.count { it.second } + strength * overall) / (l.size + strength)
        }
    }
}
