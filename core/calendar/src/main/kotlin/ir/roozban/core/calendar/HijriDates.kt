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
}
