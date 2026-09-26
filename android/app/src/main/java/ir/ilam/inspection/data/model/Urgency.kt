package ir.ilam.inspection.data.model

/**
 * How urgent a pending case has become. The same thresholds colour a card,
 * count the dashboard's warnings and sort nothing else, so they live in one
 * place: past a week it is amber, past two it is red.
 */
enum class Urgency {
    NORMAL, WARN, LATE;

    companion object {
        const val WARN_DAYS = 7
        const val LATE_DAYS = 15

        fun of(daysWaiting: Int): Urgency = when {
            daysWaiting > LATE_DAYS -> LATE
            daysWaiting > WARN_DAYS -> WARN
            else -> NORMAL
        }
    }
}
