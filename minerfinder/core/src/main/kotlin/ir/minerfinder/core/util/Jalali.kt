package ir.minerfinder.core.util

import java.util.Calendar
import java.util.TimeZone

/** Solar Hijri (Jalali) date. */
data class JalaliDate(val year: Int, val month: Int, val day: Int)

object Jalali {
    private val monthStart = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun fromGregorian(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + monthStart[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return JalaliDate(jy, jm, jd)
    }

    fun fromMillis(ms: Long, zone: TimeZone = TimeZone.getDefault()): JalaliDate {
        val c = Calendar.getInstance(zone).apply { timeInMillis = ms }
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** "1405/08/07 14:05" in Latin digits; the app converts digits for display. */
    fun format(ms: Long, zone: TimeZone = TimeZone.getDefault(), withTime: Boolean = true): String {
        val j = fromMillis(ms, zone)
        val date = "%04d/%02d/%02d".format(j.year, j.month, j.day)
        if (!withTime) return date
        val c = Calendar.getInstance(zone).apply { timeInMillis = ms }
        return date + " %02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}
