package ir.roozban.core.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PersianWeekTest {
    @Test
    fun `week starts on Saturday`() {
        assertThat(PersianWeek.indexOf(DayOfWeek.SATURDAY)).isEqualTo(0)
        assertThat(PersianWeek.indexOf(DayOfWeek.FRIDAY)).isEqualTo(6)
        assertThat(PersianWeek.DAYS.map(PersianWeek::indexOf)).containsExactly(0, 1, 2, 3, 4, 5, 6).inOrder()
    }

    @Test
    fun `start of week`() {
        val thursday = LocalDate.of(2026, 9, 24)
        assertThat(PersianWeek.startOfWeek(thursday)).isEqualTo(LocalDate.of(2026, 9, 19))
        val saturday = LocalDate.of(2026, 9, 19)
        assertThat(PersianWeek.startOfWeek(saturday)).isEqualTo(saturday)
        assertThat(PersianWeek.startOfWeek(JalaliDate.of(1405, 7, 2))).isEqualTo(JalaliDate.of(1405, 6, 28))
        assertThat(PersianWeek.dayInWeek(thursday, DayOfWeek.FRIDAY)).isEqualTo(LocalDate.of(2026, 9, 25))
    }
}
