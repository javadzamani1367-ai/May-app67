package ir.roozban.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * An immutable date in the Solar Hijri (Jalali) calendar, the official calendar of Iran.
 *
 * Conversion uses the Borkowski break-year algorithm (as in `jalaali-js`, MIT), which matches
 * the official astronomical calendar for the whole supported range [MIN_YEAR]..[MAX_YEAR].
 * All arithmetic goes through the epoch day, so it is interoperable with [LocalDate].
 */
class JalaliDate private constructor(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<JalaliDate> {

    val isLeapYear: Boolean get() = isLeapYear(year)

    val lengthOfMonth: Int get() = monthLength(year, month)

    val dayOfWeek: DayOfWeek get() = toLocalDate().dayOfWeek

    /** Position in the Iranian week: 0 = Saturday (شنبه) … 6 = Friday (جمعه). */
    val persianDayOfWeek: Int get() = PersianWeek.indexOf(dayOfWeek)

    /** 1-based day of the Jalali year. */
    val dayOfYear: Int get() = (if (month <= 7) (month - 1) * 31 else 6 * 31 + (month - 7) * 30) + day

    fun toEpochDay(): Long = JalaliConverter.toEpochDay(year, month, day)

    fun toLocalDate(): LocalDate = LocalDate.ofEpochDay(toEpochDay())

    fun plusDays(days: Long): JalaliDate = if (days == 0L) this else ofEpochDay(toEpochDay() + days)

    fun minusDays(days: Long): JalaliDate = plusDays(-days)

    /** Adds months; if the target month is shorter, the day is clamped (e.g. 31 Shahrivar + 1 month = 30 Mehr). */
    fun plusMonths(months: Long): JalaliDate {
        if (months == 0L) return this
        val total = year.toLong() * 12 + (month - 1) + months
        val newYear = Math.floorDiv(total, 12L).toInt()
        val newMonth = Math.floorMod(total, 12L).toInt() + 1
        return of(newYear, newMonth, minOf(day, monthLength(newYear, newMonth)))
    }

    fun plusYears(years: Long): JalaliDate {
        if (years == 0L) return this
        val newYear = Math.addExact(year, years.toInt())
        return of(newYear, month, minOf(day, monthLength(newYear, month)))
    }

    fun withDayOfMonth(day: Int): JalaliDate = of(year, month, day)

    fun firstDayOfMonth(): JalaliDate = of(year, month, 1)

    fun lastDayOfMonth(): JalaliDate = of(year, month, lengthOfMonth)

    override fun compareTo(other: JalaliDate): Int = toEpochDay().compareTo(other.toEpochDay())

    override fun equals(other: Any?): Boolean =
        other is JalaliDate && other.year == year && other.month == month && other.day == day

    override fun hashCode(): Int = (year * 12 + month) * 31 + day

    /** ISO-like representation with ASCII digits, e.g. `1405-07-02`. Use [PersianDateFormatter] for UI. */
    override fun toString(): String =
        "%04d-%02d-%02d".format(year, month, day)

    companion object {
        const val MIN_YEAR = 1
        const val MAX_YEAR = 3177

        fun of(year: Int, month: Int, day: Int): JalaliDate {
            require(year in MIN_YEAR..MAX_YEAR) { "Jalali year out of range: $year" }
            require(month in 1..12) { "Invalid Jalali month: $month" }
            require(day in 1..monthLength(year, month)) { "Invalid day $day for $year/$month" }
            return JalaliDate(year, month, day)
        }

        fun ofEpochDay(epochDay: Long): JalaliDate {
            val (y, m, d) = JalaliConverter.fromEpochDay(epochDay)
            return JalaliDate(y, m, d)
        }

        fun from(date: LocalDate): JalaliDate = ofEpochDay(date.toEpochDay())

        fun isLeapYear(year: Int): Boolean = JalaliConverter.isLeapYear(year)

        fun monthLength(year: Int, month: Int): Int = when {
            month <= 6 -> 31
            month <= 11 -> 30
            else -> if (isLeapYear(year)) 30 else 29
        }

        /** Parses `yyyy-MM-dd` or `yyyy/MM/dd` with ASCII or Persian digits. */
        fun parse(text: String): JalaliDate {
            val parts = PersianDigits.toAscii(text.trim()).split('-', '/')
            require(parts.size == 3) { "Expected yyyy-MM-dd: $text" }
            return of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }
    }
}

fun LocalDate.toJalali(): JalaliDate = JalaliDate.from(this)

/** Low-level conversion (Borkowski's algorithm). Kept internal; use [JalaliDate]. */
internal object JalaliConverter {

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private class YearInfo(val leap: Int, val gregorianYear: Int, val marchDay: Int)

    // Integer division/modulo truncating toward zero, as in the reference implementation.
    private fun div(a: Int, b: Int): Int = a / b
    private fun mod(a: Int, b: Int): Int = a - (a / b) * b

    private fun yearInfo(jy: Int): YearInfo {
        require(jy >= BREAKS.first() && jy < BREAKS.last()) { "Jalali year out of range: $jy" }
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return YearInfo(leap, gy, march)
    }

    fun isLeapYear(jy: Int): Boolean = yearInfo(jy).leap == 0

    /** Epoch day of 1 Farvardin of [jy]. */
    private fun nowruzEpochDay(info: YearInfo): Long =
        LocalDate.of(info.gregorianYear, 3, info.marchDay).toEpochDay()

    fun toEpochDay(jy: Int, jm: Int, jd: Int): Long {
        val info = yearInfo(jy)
        return nowruzEpochDay(info) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    fun fromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
        val gy = LocalDate.ofEpochDay(epochDay).year
        var jy = gy - 621
        val info = yearInfo(jy)
        var k = (epochDay - nowruzEpochDay(info)).toInt()
        if (k >= 0) {
            if (k <= 185) return Triple(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (info.leap == 1) k += 1
        }
        return Triple(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }
}
