package ir.roozban.core.calendar

import java.time.DateTimeException
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class HijriDate(val year: Int, val month: Int, val day: Int)

/**
 * Lunar Hijri dates via the Umm al-Qura calendar shipped with java.time (available on Android 26+).
 *
 * Iran's official lunar calendar is based on moon sighting and can differ by a day, so callers pass
 * a user-adjustable [offsetDays] (typically -1, 0 or +1). Religious holidays must come from the
 * holiday data file, never from this computation.
 */
object HijriDates {
    fun from(date: LocalDate, offsetDays: Int = 0): HijriDate? = try {
        val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
        HijriDate(
            year = h.get(ChronoField.YEAR),
            month = h.get(ChronoField.MONTH_OF_YEAR),
            day = h.get(ChronoField.DAY_OF_MONTH),
        )
    } catch (e: DateTimeException) {
        null // Outside the supported Umm al-Qura range.
    }

    /**
     * The Gregorian date of a Hijri date (inverse of [from] with the same offset). A day past the
     * month's end (30 in a 29-day month) falls back to the last day. Null outside the supported range.
     */
    fun toLocalDate(year: Int, month: Int, day: Int, offsetDays: Int = 0): LocalDate? {
        for (d in day downTo maxOf(1, day - 2)) {
            try {
                return LocalDate.from(HijrahDate.of(year, month, d)).minusDays(offsetDays.toLong())
            } catch (e: DateTimeException) {
                // Day out of range for this month: try one less.
            }
        }
        return null
    }
}
