package ir.ilam.inspection.data.model

/**
 * How one destination unit has performed: what it was sent, what it looked at,
 * what it answered, and how long it took.
 *
 * Rates and averages are computed, never stored. Storing them would mean
 * something has to recompute them whenever a dispatch is answered, and the
 * first time nothing does the manager's report becomes quietly wrong.
 */
data class UnitPerformance(
    val unit: DispatchUnit,
    val sent: Int,
    val seen: Int,
    val answered: Int,
    val overdue: Int,
    val onTime: Int,
    val averageAnswerHours: Double?
) {
    /** Percentage answered, to one decimal. Zero sent reads as zero, not as an error. */
    val answerRate: Double
        get() = if (sent == 0) 0.0 else (answered.toDouble() / sent) * 100

    /** Of the answers that came with a deadline, how many arrived in time. */
    val onTimeRate: Double
        get() = if (answered == 0) 0.0 else (onTime.toDouble() / answered) * 100

    companion object {
        /**
         * One row per unit, in a fixed order, so a unit that was sent nothing
         * still appears with zeros. A missing row would read as "no problem"
         * when it may mean nobody is using that unit at all.
         */
        fun table(rows: List<UnitPerformance>): List<UnitPerformance> =
            DispatchUnit.entries.map { unit ->
                rows.firstOrNull { it.unit == unit } ?: empty(unit)
            }

        fun empty(unit: DispatchUnit): UnitPerformance = UnitPerformance(
            unit = unit,
            sent = 0,
            seen = 0,
            answered = 0,
            overdue = 0,
            onTime = 0,
            averageAnswerHours = null
        )
    }
}
