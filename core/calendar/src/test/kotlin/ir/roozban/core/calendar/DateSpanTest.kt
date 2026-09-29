package ir.roozban.core.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DateSpanTest {
    private fun j(y: Int, m: Int, d: Int) = JalaliDate.of(y, m, d).toLocalDate()

    @Test
    fun `years, months and days in the Jalali calendar`() {
        val s = DateSpan.between(j(1400, 1, 1), j(1405, 7, 7))
        assertThat(s.jalali).isEqualTo(Span(5, 6, 6))
    }

    @Test
    fun `a day short of a month borrows the length of the month before`() {
        // 31 Shahrivar → 30 Mehr: Shahrivar has 31 days.
        assertThat(DateSpan.between(j(1404, 6, 31), j(1404, 7, 30)).jalali).isEqualTo(Span(0, 0, 30))
        // 15 Esfand → 10 Farvardin: Esfand 1403 has 30 days (leap year).
        assertThat(DateSpan.between(j(1403, 12, 15), j(1404, 1, 10)).jalali).isEqualTo(Span(0, 0, 25))
    }

    @Test
    fun `year end across Nowruz`() {
        val s = DateSpan.between(j(1403, 12, 30), j(1404, 1, 1))
        assertThat(s.totalDays).isEqualTo(1)
        assertThat(s.jalali).isEqualTo(Span(0, 0, 1))
    }

    @Test
    fun `Gregorian counts its own months`() {
        val s = DateSpan.between(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 3, 1))
        assertThat(s.gregorian).isEqualTo(Span(0, 1, 1))
        assertThat(s.totalDays).isEqualTo(30)
    }

    @Test
    fun `including the end day and going backwards`() {
        val a = j(1405, 1, 1)
        val b = j(1405, 1, 8)
        assertThat(DateSpan.between(a, b, includeEnd = true).totalDays).isEqualTo(8)
        val back = DateSpan.between(b, a)
        assertThat(back.totalDays).isEqualTo(-7)
        assertThat(back.jalali).isEqualTo(Span(0, 0, 7))
        assertThat(back.weeks).isEqualTo(1)
        assertThat(back.weekDays).isEqualTo(0)
    }

    @Test
    fun `Hijri span`() {
        val a = CalendarDates.of(CalendarSystem.HIJRI, 1446, 1, 1)!!
        val b = CalendarDates.of(CalendarSystem.HIJRI, 1447, 3, 5)!!
        assertThat(DateSpan.between(a, b).hijri).isEqualTo(Span(1, 2, 4))
    }

    @Test
    fun `dates that do not exist are refused`() {
        assertThat(CalendarDates.of(CalendarSystem.JALALI, 1404, 7, 31)).isNull()
        assertThat(CalendarDates.of(CalendarSystem.JALALI, 1404, 12, 30)).isNull() // 1404 is not leap
        assertThat(CalendarDates.of(CalendarSystem.GREGORIAN, 2025, 2, 29)).isNull()
        assertThat(CalendarDates.of(CalendarSystem.JALALI, 1403, 12, 30)).isNotNull()
    }
}
