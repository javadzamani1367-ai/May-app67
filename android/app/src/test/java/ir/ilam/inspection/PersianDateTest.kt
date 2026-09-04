package ir.ilam.inspection

import ir.ilam.inspection.util.PersianDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianDateTest {

    @Test
    fun `converts known gregorian dates to jalali`() {
        assertEquals(PersianDate.Jalali(1404, 6, 14), PersianDate.fromGregorian(2025, 9, 5))
        assertEquals(PersianDate.Jalali(1403, 1, 1), PersianDate.fromGregorian(2024, 3, 20))
        assertEquals(PersianDate.Jalali(1400, 1, 1), PersianDate.fromGregorian(2021, 3, 21))
        assertEquals(PersianDate.Jalali(1378, 10, 11), PersianDate.fromGregorian(2000, 1, 1))
        assertEquals(PersianDate.Jalali(1357, 11, 22), PersianDate.fromGregorian(1979, 2, 11))
    }

    @Test
    fun `converts jalali back to gregorian`() {
        assertEquals(Triple(2025, 9, 5), PersianDate.toGregorian(1404, 6, 14))
        assertEquals(Triple(2024, 3, 20), PersianDate.toGregorian(1403, 1, 1))
    }

    @Test
    fun `round trips every day across a wide range`() {
        var year = 1350
        while (year <= 1450) {
            for (month in 1..12) {
                val length = PersianDate.monthLength(year, month)
                for (day in 1..length) {
                    val (gy, gm, gd) = PersianDate.toGregorian(year, month, day)
                    assertEquals(
                        PersianDate.Jalali(year, month, day),
                        PersianDate.fromGregorian(gy, gm, gd)
                    )
                }
            }
            year++
        }
    }

    @Test
    fun `knows leap years`() {
        assertTrue(PersianDate.isLeapYear(1403))
        assertFalse(PersianDate.isLeapYear(1404))
        assertEquals(30, PersianDate.monthLength(1403, 12))
        assertEquals(29, PersianDate.monthLength(1404, 12))
        assertEquals(31, PersianDate.monthLength(1404, 6))
        assertEquals(30, PersianDate.monthLength(1404, 7))
    }

    @Test
    fun `formats dates with persian digits`() {
        // 1405/06/14 is the date behind the tracking code documented in
        // CLAUDE.md: M-۰۱-۰۵۰۶۱۴-۴۸۲۹۱۷.
        val millis = PersianDate.toEpochMillis(1405, 6, 14)
        assertEquals("۱۴۰۵/۰۶/۱۴", PersianDate.format(millis))
        assertEquals("050614", PersianDate.trackingStamp(millis))
    }

    @Test
    fun `counts whole days between instants`() {
        val from = PersianDate.toEpochMillis(1404, 6, 1)
        val to = PersianDate.toEpochMillis(1404, 6, 14)
        assertEquals(13, PersianDate.daysBetween(from, to))
        assertEquals(0, PersianDate.daysBetween(to, to))
    }
}
