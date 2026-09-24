package ir.roozban.core.recurrence

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.toJalali
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class RecurrenceSpecTest {

    private fun d(jalali: String): LocalDate = JalaliDate.parse(jalali).toLocalDate()

    /** The next [count] occurrences after [start] (exclusive), as Jalali strings. */
    private fun RecurrenceSpec.series(start: String, count: Int): List<String> {
        val s = d(start)
        var cur = s
        return List(count) { cur = nextAfter(cur, s); cur.toJalali().toString() }
    }

    @Test
    fun daily() {
        assertThat(RecurrenceSpec(Frequency.DAILY).series("1405-07-29", 3))
            .containsExactly("1405-07-30", "1405-08-01", "1405-08-02").inOrder()
        assertThat(RecurrenceSpec(Frequency.DAILY, interval = 2).series("1405-07-02", 3))
            .containsExactly("1405-07-04", "1405-07-06", "1405-07-08").inOrder()
    }

    @Test
    fun `daily keeps its phase when completed late`() {
        val spec = RecurrenceSpec(Frequency.DAILY, interval = 3)
        // Series started on 07-01; completed on 07-05 → next is 07-07 (01, 04, 07 …).
        assertThat(spec.nextAfter(d("1405-07-05"), d("1405-07-01")).toJalali().toString()).isEqualTo("1405-07-07")
    }

    @Test
    fun weekly() {
        // 1405-07-04 is a Saturday.
        assertThat(RecurrenceSpec(Frequency.WEEKLY, byWeekdays = setOf(SATURDAY, MONDAY, WEDNESDAY)).series("1405-07-04", 4))
            .containsExactly("1405-07-06", "1405-07-08", "1405-07-11", "1405-07-13").inOrder()
        assertThat(RecurrenceSpec(Frequency.WEEKLY).series("1405-07-04", 2))
            .containsExactly("1405-07-11", "1405-07-18").inOrder()
    }

    @Test
    fun `every other week`() {
        assertThat(RecurrenceSpec(Frequency.WEEKLY, interval = 2, byWeekdays = setOf(SATURDAY, MONDAY)).series("1405-07-04", 4))
            .containsExactly("1405-07-06", "1405-07-18", "1405-07-20", "1405-08-02").inOrder()
    }

    @Test
    fun `monthly on the last Jalali day`() {
        assertThat(RecurrenceSpec(Frequency.MONTHLY, jalaliMonthDay = -1).series("1405-06-31", 7))
            .containsExactly("1405-07-30", "1405-08-30", "1405-09-30", "1405-10-30", "1405-11-30", "1405-12-29", "1406-01-31")
            .inOrder()
    }

    @Test
    fun `monthly on day 31 clamps in short months`() {
        assertThat(RecurrenceSpec(Frequency.MONTHLY).series("1405-06-31", 2))
            .containsExactly("1405-07-30", "1405-08-30").inOrder()
    }

    @Test
    fun `quarterly on the 15th`() {
        assertThat(RecurrenceSpec(Frequency.MONTHLY, interval = 3, jalaliMonthDay = 15).series("1405-07-15", 3))
            .containsExactly("1405-10-15", "1406-01-15", "1406-04-15").inOrder()
    }

    @Test
    fun `yearly keeps the Jalali date and clamps 30 Esfand`() {
        assertThat(RecurrenceSpec(Frequency.YEARLY).series("1405-08-15", 2))
            .containsExactly("1406-08-15", "1407-08-15").inOrder()
        assertThat(RecurrenceSpec(Frequency.YEARLY).series("1403-12-30", 2))
            .containsExactly("1404-12-29", "1405-12-29").inOrder()
    }

    @Test
    fun `before the start the start is next`() {
        assertThat(RecurrenceSpec(Frequency.DAILY).nextAfter(d("1405-07-01"), d("1405-07-10"))).isEqualTo(d("1405-07-10"))
    }

    @Test
    fun `rrule round trip`() {
        listOf(
            RecurrenceSpec(Frequency.DAILY),
            RecurrenceSpec(Frequency.DAILY, interval = 2),
            RecurrenceSpec(Frequency.WEEKLY, byWeekdays = setOf(SATURDAY, MONDAY)),
            RecurrenceSpec(Frequency.MONTHLY, jalaliMonthDay = -1),
            RecurrenceSpec(Frequency.MONTHLY, interval = 3, jalaliMonthDay = 15),
            RecurrenceSpec(Frequency.YEARLY),
        ).forEach { assertThat(RecurrenceSpec.parse(it.toRRule())).isEqualTo(it) }
        assertThat(RecurrenceSpec(Frequency.WEEKLY, byWeekdays = setOf(MONDAY, SATURDAY)).toRRule())
            .isEqualTo("FREQ=WEEKLY;BYDAY=SA,MO")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "FREQ=HOURLY", "FREQ=MONTHLY", "FREQ=DAILY;COUNT=3", "FREQ=WEEKLY;BYDAY=XX", "FREQ=DAILY;INTERVAL=0"])
    fun `unsupported rules are rejected`(rule: String) {
        assertThat(RecurrenceSpec.parse(rule)).isNull()
    }
}
