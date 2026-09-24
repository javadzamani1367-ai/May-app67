package ir.roozban.core.timeparser

import ir.roozban.core.calendar.PersianWeek
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** The resolved point in time of a parsed expression. */
sealed interface ResolvedTime {
    /** A specific moment, e.g. «فردا ساعت ۵». */
    data class At(val dateTime: LocalDateTime) : ResolvedTime

    /** A whole day without a time, e.g. «آخر ماه». */
    data class AllDay(val date: LocalDate) : ResolvedTime
}

enum class SpanKind { TIME, RECURRENCE, DURATION }

/** Character range [start, end) in the original input. */
data class MatchedSpan(val start: Int, val end: Int, val kind: SpanKind)

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A recurrence rule. Monthly and yearly rules are evaluated in the **Jalali** calendar
 * (RFC 7529 `RSCALE=PERSIAN`), so «آخر هر ماه» means the last day of each Jalali month.
 */
data class RecurrenceSpec(
    val frequency: Frequency,
    val interval: Int = 1,
    val byWeekdays: Set<DayOfWeek> = emptySet(),
    /** Day of the Jalali month; -1 means the last day. Only for [Frequency.MONTHLY]. */
    val jalaliMonthDay: Int? = null,
) {
    fun toRRule(): String = buildList {
        if (frequency == Frequency.MONTHLY || frequency == Frequency.YEARLY) add("RSCALE=PERSIAN")
        add("FREQ=${frequency.name}")
        if (interval > 1) add("INTERVAL=$interval")
        if (byWeekdays.isNotEmpty()) {
            add("BYDAY=" + PersianWeek.DAYS.filter { it in byWeekdays }.joinToString(",") { it.name.take(2) })
        }
        jalaliMonthDay?.let { add("BYMONTHDAY=$it") }
    }.joinToString(";")
}

data class ParseResult(
    val input: String,
    /** Null when no time expression was found. */
    val time: ResolvedTime?,
    val recurrence: RecurrenceSpec?,
    /** Estimated effort, e.g. «۳۰ دقیقه». */
    val estimate: Duration?,
    val spans: List<MatchedSpan>,
    /**
     * 0..1. Below about 0.6 the caller should confirm with the user or ask the language model
     * for help (phase 4). 0 when nothing was found.
     */
    val confidence: Float,
    /** The input with all matched spans removed, i.e. the task title. */
    val title: String,
)
