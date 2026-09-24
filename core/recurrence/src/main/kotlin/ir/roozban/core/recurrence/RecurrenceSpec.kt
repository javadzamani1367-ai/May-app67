package ir.roozban.core.recurrence

import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import java.time.DayOfWeek
import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A recurrence rule: the subset of RFC 5545 RRULE that the app produces, with monthly and yearly
 * rules evaluated in the **Jalali** calendar (RFC 7529 `RSCALE=PERSIAN`). So «آخر هر ماه» is the
 * last day of each Jalali month and a yearly task on ۱۵ آبان stays on ۱۵ آبان.
 *
 * Deviation from RFC 5545, chosen for a to-do app: a monthly day that does not exist in a month
 * (e.g. the 31st in Mehr) falls on that month's last day instead of skipping the month.
 */
data class RecurrenceSpec(
    val frequency: Frequency,
    val interval: Int = 1,
    val byWeekdays: Set<DayOfWeek> = emptySet(),
    /** Day of the Jalali month; -1 means the last day. Only for [Frequency.MONTHLY]. */
    val jalaliMonthDay: Int? = null,
) {
    init {
        require(interval >= 1) { "interval must be positive: $interval" }
        require(jalaliMonthDay == null || jalaliMonthDay == -1 || jalaliMonthDay in 1..31) { "bad month day $jalaliMonthDay" }
    }

    fun toRRule(): String = buildList {
        if (frequency == Frequency.MONTHLY || frequency == Frequency.YEARLY) add("RSCALE=PERSIAN")
        add("FREQ=${frequency.name}")
        if (interval > 1) add("INTERVAL=$interval")
        if (byWeekdays.isNotEmpty()) {
            add("BYDAY=" + PersianWeek.DAYS.filter { it in byWeekdays }.joinToString(",") { it.name.take(2) })
        }
        jalaliMonthDay?.let { add("BYMONTHDAY=$it") }
    }.joinToString(";")

    /**
     * The first occurrence strictly after [after], for a series whose first occurrence is [start].
     * [start] fixes the phase of the interval and the defaults (weekday, day of month, date of year).
     */
    fun nextAfter(after: LocalDate, start: LocalDate): LocalDate {
        if (after.isBefore(start)) return start
        return when (frequency) {
            Frequency.DAILY -> {
                val elapsed = after.toEpochDay() - start.toEpochDay()
                start.plusDays((elapsed / interval + 1) * interval)
            }
            Frequency.WEEKLY -> nextWeekly(after, start)
            Frequency.MONTHLY -> nextMonthly(after, start)
            Frequency.YEARLY -> nextYearly(after, start)
        }
    }

    private fun weekIndex(date: LocalDate): Long = Math.floorDiv(PersianWeek.startOfWeek(date).toEpochDay(), 7L)

    private fun nextWeekly(after: LocalDate, start: LocalDate): LocalDate {
        val days = byWeekdays.ifEmpty { setOf(start.dayOfWeek) }
        val startWeek = weekIndex(start)
        var d = after.plusDays(1)
        // At most interval weeks of skipped weeks plus one full week to scan.
        repeat(7 * (interval + 1)) {
            if (d.dayOfWeek in days && Math.floorMod(weekIndex(d) - startWeek, interval.toLong()) == 0L) return d
            d = d.plusDays(1)
        }
        error("unreachable: no weekly occurrence found")
    }

    private fun monthIndex(date: JalaliDate): Long = date.year * 12L + (date.month - 1)

    private fun nextMonthly(after: LocalDate, start: LocalDate): LocalDate {
        val js = start.toJalali()
        val day = jalaliMonthDay ?: js.day
        val startIndex = monthIndex(js)
        var month = after.toJalali().firstDayOfMonth()
        repeat(interval + 2) {
            if (Math.floorMod(monthIndex(month) - startIndex, interval.toLong()) == 0L) {
                val candidate = month.withDayOfMonth(if (day == -1) month.lengthOfMonth else minOf(day, month.lengthOfMonth)).toLocalDate()
                if (candidate.isAfter(after)) return candidate
            }
            month = month.plusMonths(1)
        }
        error("unreachable: no monthly occurrence found")
    }

    private fun nextYearly(after: LocalDate, start: LocalDate): LocalDate {
        val js = start.toJalali()
        var year = after.toJalali().year
        repeat(interval + 2) {
            if (Math.floorMod(year - js.year, interval) == 0) {
                val len = JalaliDate.monthLength(year, js.month)
                val candidate = JalaliDate.of(year, js.month, minOf(js.day, len)).toLocalDate()
                if (candidate.isAfter(after)) return candidate
            }
            year++
        }
        error("unreachable: no yearly occurrence found")
    }

    companion object {
        private val DAY_CODES = DayOfWeek.entries.associateBy { it.name.take(2) }

        /** Parses what [toRRule] produces. Returns null for anything outside the supported subset. */
        fun parse(rrule: String): RecurrenceSpec? {
            val parts = rrule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate {
                val (k, v) = it.split('=', limit = 2).let { kv -> kv[0].uppercase() to kv.getOrElse(1) { "" } }
                k to v
            }
            val frequency = parts["FREQ"]?.let { f -> Frequency.entries.firstOrNull { it.name == f } } ?: return null
            val rscale = parts["RSCALE"]
            if ((frequency == Frequency.MONTHLY || frequency == Frequency.YEARLY) && rscale != "PERSIAN") return null
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
            val days = parts["BYDAY"]?.split(',')?.map { DAY_CODES[it] ?: return null }?.toSet().orEmpty()
            val monthDay = parts["BYMONTHDAY"]?.let { it.toIntOrNull() ?: return null }
            val supported = setOf("FREQ", "RSCALE", "INTERVAL", "BYDAY", "BYMONTHDAY")
            if (parts.keys.any { it !in supported } || interval < 1) return null
            return runCatching { RecurrenceSpec(frequency, interval, days, monthDay) }.getOrNull()
        }
    }
}
