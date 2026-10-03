package ir.ilam.inspection.field.data

import kotlin.math.abs

/**
 * The feeder reading: the main switch and four feeders, each feeder with an
 * explicit state, so a feeder left empty on purpose is never mistaken for
 * one that was forgotten.
 *
 * Layout, seen from the front of the panel:
 *   A — front right, B — front left, C — back, behind B, D — back, behind A.
 */
enum class FeederState { MEASURED, ABSENT, NOT_MEASURED }

data class PhaseReading(val r: String = "", val s: String = "", val t: String = "") {
    fun values(): List<Double?> = listOf(r, s, t).map { it.toDoubleOrNull() }
    fun complete(): Boolean = values().all { it != null }
}

data class FeederReading(
    val state: FeederState? = null,
    val phases: PhaseReading = PhaseReading(),
    val reason: String = ""
)

/** What the sum check found for one phase or for the total. */
data class PhaseCheck(val main: Double, val feeders: Double, val tolerance: Double) {
    val difference: Double get() = feeders - main
    val ok: Boolean get() = abs(difference) <= tolerance
}

data class FeederVerdict(
    /** Every phase R, S, T, then the three together. Empty when nothing could be compared. */
    val phases: List<PhaseCheck>,
    /** False when a feeder was not measured: the sum cannot be checked, and is not held against saving. */
    val possible: Boolean,
    /** Load imbalance across the main switch's phases, in percent; for information only. */
    val imbalancePct: Double?
) {
    val mismatched: Boolean get() = possible && phases.any { !it.ok }
}

object FeederCheck {
    const val FEEDERS = 4
    /** No feeder of a distribution panel carries this; a value above it is a typing slip. */
    const val MAX_AMPERE = 4000.0

    /**
     * Compares, per phase and in total, the sum of the measured feeders with
     * the main switch. The allowed difference is the larger of a percentage
     * of the main reading and an absolute floor, so a lightly loaded panel is
     * not failed over a couple of amperes. Both come from the manager's
     * settings.
     */
    fun verdict(main: PhaseReading, feeders: List<FeederReading>, tolerancePct: Int, toleranceMinA: Int): FeederVerdict {
        val mainValues = main.values()
        val imbalance = imbalancePct(mainValues)
        if (mainValues.any { it == null }) return FeederVerdict(emptyList(), possible = false, imbalancePct = imbalance)
        val possible = feeders.none { it.state == FeederState.NOT_MEASURED }
        val measured = feeders.filter { it.state == FeederState.MEASURED }
        val sums = (0 until 3).map { phase -> measured.sumOf { it.phases.values()[phase] ?: 0.0 } }
        fun check(mainValue: Double, sum: Double) =
            PhaseCheck(mainValue, sum, maxOf(mainValue * tolerancePct / 100.0, toleranceMinA.toDouble()))
        val perPhase = (0 until 3).map { check(mainValues[it]!!, sums[it]) }
        val total = check(mainValues.sumOf { it!! }, sums.sum())
        return FeederVerdict(perPhase + total, possible, imbalance)
    }

    /** (largest − smallest) / mean of the three phases, in percent. */
    fun imbalancePct(values: List<Double?>): Double? {
        if (values.any { it == null }) return null
        val v = values.map { it!! }
        val mean = v.average()
        if (mean <= 0.0) return null
        return (v.max() - v.min()) / mean * 100.0
    }

    /** What is still missing before the reading may be saved, as keys the screen turns into words. */
    fun missing(main: PhaseReading, feeders: List<FeederReading>): List<String> = buildList {
        if (!main.complete()) add("main")
        if (main.values().any { (it ?: 0.0) > MAX_AMPERE }) add("main_range")
        feeders.forEachIndexed { index, feeder ->
            val name = "ABCD"[index].toString()
            when (feeder.state) {
                null -> add("state:$name")
                FeederState.MEASURED -> {
                    if (!feeder.phases.complete()) add("values:$name")
                    if (feeder.phases.values().any { (it ?: 0.0) > MAX_AMPERE }) add("range:$name")
                }
                FeederState.NOT_MEASURED -> if (feeder.reason.isBlank()) add("reason:$name")
                FeederState.ABSENT -> Unit
            }
        }
    }
}
