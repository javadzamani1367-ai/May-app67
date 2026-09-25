package ir.roozban.ai.tools

import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.domain.toParserPrefs
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.UserSettings
import ir.roozban.core.recurrence.RecurrenceSpec
import ir.roozban.core.timeparser.PersianTimeParser
import ir.roozban.core.timeparser.ResolvedTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Turns the model's time phrases into dates with the app's own Persian parser, so dates are
 * never computed by the model. Also accepts `yyyy-mm-dd [hh:mm]` and Jalali `yyyy/mm/dd`.
 */
class WhenResolver(settings: UserSettings) {
    private val parser = PersianTimeParser(settings.toParserPrefs())

    fun due(text: String, now: LocalDateTime): TaskDue? {
        val t = PersianDigits.toAscii(text.trim())
        ISO.matchEntire(t)?.let { m ->
            val date = runCatching { LocalDate.of(m.int(1), m.int(2), m.int(3)) }.getOrNull() ?: return null
            val time = m.groups[4]?.let { runCatching { LocalTime.of(m.int(4), m.int(5)) }.getOrNull() }
            return if (time != null) TaskDue.At(date, time) else TaskDue.AllDay(date)
        }
        JALALI.matchEntire(t)?.let { m ->
            val date = runCatching { JalaliDate.of(m.int(1), m.int(2), m.int(3)).toLocalDate() }.getOrNull() ?: return null
            val time = m.groups[4]?.let { runCatching { LocalTime.of(m.int(4), m.int(5)) }.getOrNull() }
            return if (time != null) TaskDue.At(date, time) else TaskDue.AllDay(date)
        }
        return when (val r = parser.parse(text, now).time) {
            null -> null
            is ResolvedTime.AllDay -> TaskDue.AllDay(r.date)
            is ResolvedTime.At -> TaskDue.At(r.dateTime.toLocalDate(), r.dateTime.toLocalTime())
        }
    }

    fun day(text: String, now: LocalDateTime): LocalDate? = due(text, now)?.date

    fun time(text: String, now: LocalDateTime): LocalTime? = (due(text, now) as? TaskDue.At)?.time

    fun recurrence(text: String, now: LocalDateTime): RecurrenceSpec? = parser.parse(text, now).recurrence

    private fun MatchResult.int(i: Int) = groupValues[i].toInt()

    private companion object {
        val ISO = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2}))?")
        val JALALI = Regex("(1[34]\\d{2})/(\\d{1,2})/(\\d{1,2})(?: (\\d{1,2}):(\\d{2}))?")
    }
}
