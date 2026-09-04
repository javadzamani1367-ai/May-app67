package ir.ilam.inspection.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Jalali (Hijri Shamsi) calendar conversion.
 *
 * The database always stores unix milliseconds; every Jalali value in the app is
 * produced here, in the presentation layer only. The algorithm is the Borkowski
 * leap-year table, which is exact for the whole range the app can ever see.
 */
object PersianDate {

    /** Tehran is the single civil timezone the app works in. */
    val zone: TimeZone = TimeZone.getTimeZone("Asia/Tehran")

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    data class Jalali(val year: Int, val month: Int, val day: Int)

    private data class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun jalaliCal(jy: Int): Cal {
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        require(jy in jp until BREAKS[BREAKS.size - 1]) { "jalali year out of range: $jy" }
        var jump = 0
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1

        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG

        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return Cal(leap, gy, march)
    }

    private fun gregorianToDayNumber(gy: Int, gm: Int, gd: Int): Int {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 +
            (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
        d -= ((((gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4) - 752
        return d
    }

    private fun dayNumberToGregorian(dayNumber: Int): Triple<Int, Int, Int> {
        var j = 4 * dayNumber + 139361631
        j += (((4 * dayNumber + 183187720) / 146097) * 3) / 4 * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val gd = ((i % 153) / 5) + 1
        val gm = ((i / 153) % 12) + 1
        val gy = j / 1461 - 100100 + (8 - gm) / 6
        return Triple(gy, gm, gd)
    }

    private fun jalaliToDayNumber(jy: Int, jm: Int, jd: Int): Int {
        val r = jalaliCal(jy)
        return gregorianToDayNumber(r.gy, 3, r.march) + (jm - 1) * 31 -
            (jm / 7) * (jm - 7) + jd - 1
    }

    private fun dayNumberToJalali(dayNumber: Int): Jalali {
        val (gy, _, _) = dayNumberToGregorian(dayNumber)
        var jy = gy - 621
        val r = jalaliCal(jy)
        val firstDay = gregorianToDayNumber(r.gy, 3, r.march)
        var k = dayNumber - firstDay
        if (k >= 0) {
            if (k <= 185) return Jalali(jy, 1 + k / 31, (k % 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return Jalali(jy, 7 + k / 30, (k % 30) + 1)
    }

    fun fromGregorian(gy: Int, gm: Int, gd: Int): Jalali =
        dayNumberToJalali(gregorianToDayNumber(gy, gm, gd))

    fun toGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> =
        dayNumberToGregorian(jalaliToDayNumber(jy, jm, jd))

    fun isLeapYear(jy: Int): Boolean = jalaliCal(jy).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        isLeapYear(jy) -> 30
        else -> 29
    }

    fun of(epochMillis: Long): Jalali {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = epochMillis
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun toEpochMillis(jy: Int, jm: Int, jd: Int): Long {
        val (gy, gm, gd) = toGregorian(jy, jm, jd)
        val c = Calendar.getInstance(zone)
        c.clear()
        c.set(gy, gm - 1, gd, 0, 0, 0)
        return c.timeInMillis
    }

    fun today(): Jalali = of(System.currentTimeMillis())

    /** `۱۴۰۴/۰۶/۱۴` — the display form used everywhere in the UI. */
    fun format(epochMillis: Long): String = format(of(epochMillis))

    fun format(j: Jalali): String =
        PersianNumbers.toPersian("%04d/%02d/%02d".format(j.year, j.month, j.day))

    /** `۱۴۰۴/۰۶/۱۴ - ۰۹:۳۵` for photo stamps and media captions. */
    fun formatWithTime(epochMillis: Long): String {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = epochMillis
        val time = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        return format(epochMillis) + " - " + PersianNumbers.toPersian(time)
    }

    /** `050614` in latin digits — the date segment of a tracking code. */
    fun trackingStamp(epochMillis: Long): String {
        val j = of(epochMillis)
        return "%02d%02d%02d".format(j.year % 100, j.month, j.day)
    }

    /** Whole days between two instants, counted on the civil calendar. */
    fun daysBetween(fromMillis: Long, toMillis: Long): Int {
        val a = startOfDay(fromMillis)
        val b = startOfDay(toMillis)
        return ((b - a) / 86_400_000L).toInt()
    }

    fun startOfDay(epochMillis: Long): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = epochMillis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun endOfDay(epochMillis: Long): Long = startOfDay(epochMillis) + 86_400_000L - 1
}
