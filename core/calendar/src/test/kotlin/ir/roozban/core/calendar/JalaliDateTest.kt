package ir.roozban.core.calendar

import com.google.common.truth.Truth.assertThat
import com.ibm.icu.util.PersianCalendar
import com.ibm.icu.util.TimeZone
import com.ibm.icu.util.ULocale
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.DayOfWeek
import java.time.LocalDate

class JalaliDateTest {

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(
        "1300-01-01, 1921-03-21",
        "1357-11-22, 1979-02-11",
        "1399-01-01, 2020-03-20",
        "1399-12-30, 2021-03-20",
        "1400-01-01, 2021-03-21",
        "1403-01-01, 2024-03-20",
        "1403-12-30, 2025-03-20",
        "1404-01-01, 2025-03-21",
        "1405-01-01, 2026-03-21",
        "1405-07-01, 2026-09-23",
        "1405-07-02, 2026-09-24",
        "1405-12-29, 2027-03-20",
        "1406-01-01, 2027-03-21",
    )
    fun `known dates`(jalali: String, gregorian: String) {
        val j = JalaliDate.parse(jalali)
        val g = LocalDate.parse(gregorian)
        assertThat(j.toLocalDate()).isEqualTo(g)
        assertThat(g.toJalali()).isEqualTo(j)
    }

    @Test
    fun `leap years`() {
        val leaps = (1370..1412).filter { JalaliDate.isLeapYear(it) }
        assertThat(leaps).containsExactly(1370, 1375, 1379, 1383, 1387, 1391, 1395, 1399, 1403, 1408, 1412).inOrder()
    }

    @Test
    fun `round trip every day from 1300 to 1500`() {
        val start = JalaliDate.of(1300, 1, 1).toEpochDay()
        val end = JalaliDate.of(1500, 12, 29).toEpochDay()
        var previous: JalaliDate? = null
        for (day in start..end) {
            val j = JalaliDate.ofEpochDay(day)
            assertThat(j.toEpochDay()).isEqualTo(day)
            previous?.let { p ->
                // Consecutive days: either next day in month, or first day of the next month/year.
                val expectedNext = when {
                    p.day < p.lengthOfMonth -> Triple(p.year, p.month, p.day + 1)
                    p.month < 12 -> Triple(p.year, p.month + 1, 1)
                    else -> Triple(p.year + 1, 1, 1)
                }
                assertThat(Triple(j.year, j.month, j.day)).isEqualTo(expectedNext)
            }
            previous = j
        }
    }

    /** ICU uses a different (arithmetic) algorithm; both agree over the practical range. */
    @Test
    fun `matches ICU PersianCalendar from 1300 to 1470`() {
        val icu = PersianCalendar(TimeZone.GMT_ZONE, ULocale("fa_IR"))
        var date = LocalDate.of(1921, 3, 21)
        val end = JalaliDate.of(1470, 12, 29).toLocalDate()
        while (!date.isAfter(end)) {
            icu.timeInMillis = date.toEpochDay() * 86_400_000L
            val j = date.toJalali()
            assertThat(Triple(j.year, j.month, j.day)).isEqualTo(
                Triple(icu.get(PersianCalendar.EXTENDED_YEAR), icu.get(PersianCalendar.MONTH) + 1, icu.get(PersianCalendar.DAY_OF_MONTH)),
            )
            date = date.plusDays(1)
        }
    }

    @Test
    fun `month lengths`() {
        assertThat((1..12).map { JalaliDate.monthLength(1405, it) })
            .containsExactly(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29).inOrder()
        assertThat(JalaliDate.monthLength(1403, 12)).isEqualTo(30)
    }

    @Test
    fun `invalid dates are rejected`() {
        assertThrows<IllegalArgumentException> { JalaliDate.of(1405, 7, 31) }
        assertThrows<IllegalArgumentException> { JalaliDate.of(1405, 12, 30) }
        assertThrows<IllegalArgumentException> { JalaliDate.of(1405, 13, 1) }
        assertThrows<IllegalArgumentException> { JalaliDate.of(1405, 1, 0) }
    }

    @Test
    fun `plus months clamps the day`() {
        assertThat(JalaliDate.of(1405, 6, 31).plusMonths(1)).isEqualTo(JalaliDate.of(1405, 7, 30))
        assertThat(JalaliDate.of(1405, 11, 30).plusMonths(1)).isEqualTo(JalaliDate.of(1405, 12, 29))
        assertThat(JalaliDate.of(1405, 12, 15).plusMonths(2)).isEqualTo(JalaliDate.of(1406, 2, 15))
        assertThat(JalaliDate.of(1405, 1, 15).plusMonths(-1)).isEqualTo(JalaliDate.of(1404, 12, 15))
        assertThat(JalaliDate.of(1403, 12, 30).plusYears(1)).isEqualTo(JalaliDate.of(1404, 12, 29))
    }

    @Test
    fun `day of week and day of year`() {
        val d = JalaliDate.of(1405, 7, 2)
        assertThat(d.dayOfWeek).isEqualTo(DayOfWeek.THURSDAY)
        assertThat(d.persianDayOfWeek).isEqualTo(5)
        assertThat(JalaliDate.of(1405, 1, 1).dayOfYear).isEqualTo(1)
        assertThat(JalaliDate.of(1405, 7, 1).dayOfYear).isEqualTo(187)
        assertThat(JalaliDate.of(1405, 12, 29).dayOfYear).isEqualTo(365)
    }

    @Test
    fun `parse accepts Persian digits and slashes`() {
        assertThat(JalaliDate.parse("۱۴۰۵/۰۷/۰۲")).isEqualTo(JalaliDate.of(1405, 7, 2))
        assertThat(JalaliDate.of(1405, 7, 2).toString()).isEqualTo("1405-07-02")
    }
}
