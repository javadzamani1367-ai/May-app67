package ir.roozban.core.domain

import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.UserSettings
import ir.roozban.core.recurrence.RecurrenceSpec
import ir.roozban.core.timeparser.PersianTimeParser
import ir.roozban.core.timeparser.ResolvedTime
import ir.roozban.core.timeparser.SpanKind
import ir.roozban.core.timeparser.TimeParserPrefs
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

enum class HighlightKind { TIME, RECURRENCE, DURATION, PRIORITY }

data class Highlight(val start: Int, val end: Int, val kind: HighlightKind)

data class QuickAddResult(
    val title: String,
    val due: TaskDue?,
    val recurrence: RecurrenceSpec?,
    val estimate: Duration?,
    val important: Boolean,
    val urgent: Boolean,
    val highlights: List<Highlight>,
    val confidence: Float,
)

/**
 * Quick-add syntax: free Persian text with a time expression, plus Eisenhower markers
 * `!مهم` (important), `!فوری` (urgent) and `!!` (both).
 */
class QuickAddParser @Inject constructor() {

    fun parse(text: String, now: LocalDateTime, settings: UserSettings): QuickAddResult {
        val parser = PersianTimeParser(settings.toParserPrefs())
        val result = parser.parse(text, now)

        var important = false
        var urgent = false
        val markerSpans = MARKER.findAll(text).map { m ->
            when (m.groupValues[1]) {
                "!" -> { important = true; urgent = true }
                "مهم" -> important = true
                "فوری" -> urgent = true
            }
            Highlight(m.range.first, m.range.last + 1, HighlightKind.PRIORITY)
        }.toList()

        val due = when (val t = result.time) {
            null -> null
            is ResolvedTime.AllDay -> TaskDue.AllDay(t.date)
            is ResolvedTime.At -> TaskDue.At(t.dateTime.toLocalDate(), t.dateTime.toLocalTime())
        }
        val highlights = result.spans.map {
            val kind = when (it.kind) {
                SpanKind.TIME -> HighlightKind.TIME
                SpanKind.RECURRENCE -> HighlightKind.RECURRENCE
                SpanKind.DURATION -> HighlightKind.DURATION
            }
            Highlight(it.start, it.end, kind)
        } + markerSpans

        return QuickAddResult(
            title = result.title.replace(MARKER, " ").replace(SPACES, " ").trim(),
            due = due,
            recurrence = result.recurrence,
            estimate = result.estimate,
            important = important,
            urgent = urgent,
            highlights = highlights.sortedBy { it.start },
            confidence = result.confidence,
        )
    }

    private companion object {
        val MARKER = Regex("(?<!\\S)!(!|مهم|فوری)(?!\\S)")
        val SPACES = Regex("\\s+")
    }
}

fun UserSettings.toParserPrefs() = TimeParserPrefs(
    morningHour = morningHour,
    noonHour = noonHour,
    afternoonHour = afternoonHour,
    eveningHour = eveningHour,
    nightHour = nightHour,
)
