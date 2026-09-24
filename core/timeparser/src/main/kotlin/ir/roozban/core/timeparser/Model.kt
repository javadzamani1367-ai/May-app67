package ir.roozban.core.timeparser

import ir.roozban.core.recurrence.RecurrenceSpec
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
