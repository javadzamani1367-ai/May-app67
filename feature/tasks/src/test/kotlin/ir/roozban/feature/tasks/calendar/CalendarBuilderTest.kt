package ir.roozban.feature.tasks.calendar

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.model.EventCalendar
import ir.roozban.core.model.EventKind
import ir.roozban.core.model.PersonalEvent
import ir.roozban.core.model.Quadrant
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.testing.jalali
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class CalendarBuilderTest {
    private val today = jalali("1405-07-02") // Thursday

    @Test
    fun ranges() {
        val week = CalendarBuilder.range(CalendarMode.WEEK, today)
        assertThat(week).hasSize(7)
        assertThat(week.first().dayOfWeek).isEqualTo(DayOfWeek.SATURDAY)
        assertThat(week.first().toJalali().toString()).isEqualTo("1405-06-28")

        val month = CalendarBuilder.range(CalendarMode.MONTH, today)
        assertThat(month).hasSize(42)
        assertThat(month.first().dayOfWeek).isEqualTo(DayOfWeek.SATURDAY)
        assertThat(month.map { it.toJalali().toString() }).contains("1405-07-01")
        assertThat(CalendarBuilder.range(CalendarMode.DAY, today)).containsExactly(today)
    }

    @Test
    fun titles() {
        assertThat(CalendarBuilder.title(CalendarMode.MONTH, today)).isEqualTo("مهر ۱۴۰۵")
        assertThat(CalendarBuilder.title(CalendarMode.WEEK, today)).isEqualTo("۲۸ شهریور تا ۳ مهر")
        assertThat(CalendarBuilder.title(CalendarMode.DAY, today)).isEqualTo("پنجشنبه ۲ مهر")
    }

    @Test
    fun `days carry tasks, holidays and secondary dates`() {
        val tasks = listOf(
            Task(id = "a", title = "صبح", due = TaskDue.At(today, LocalTime.of(9, 0)), estimateMinutes = 60, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
            Task(id = "b", title = "بی‌ساعت", due = TaskDue.AllDay(today), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        )
        val days = CalendarBuilder.build(CalendarMode.WEEK, today, today, tasks, showGregorian = true, showHijri = false, hijriOffset = 0)
        val thursday = days.single { it.date == today }
        assertThat(thursday.isToday).isTrue()
        assertThat(thursday.jalaliDay).isEqualTo("۲")
        assertThat(thursday.gregorianDay).isEqualTo("۲۴")
        assertThat(thursday.hijriDay).isNull()
        assertThat(thursday.timed.single().durationMinutes).isEqualTo(60)
        assertThat(thursday.allDay.single().id).isEqualTo("b")
        assertThat(days.last().isHoliday).isTrue() // Friday
        val nowruz = CalendarBuilder.build(CalendarMode.DAY, jalali("1406-01-01"), today, emptyList(), false, false, 0).single()
        assertThat(nowruz.holidayNames).contains("عید نوروز")
    }

    @Test
    fun `overlapping tasks share lanes`() {
        fun t(id: String, h: Int, m: Int, dur: Int) = CalendarTask(id, id, LocalTime.of(h, m), dur, Quadrant.NONE)
        val laid = CalendarBuilder.layoutLanes(
            listOf(t("a", 9, 0, 60), t("b", 9, 30, 60), t("c", 10, 45, 30), t("d", 13, 0, 30)),
        ).associateBy { it.id }
        assertThat(laid.getValue("a").lane to laid.getValue("a").lanes).isEqualTo(0 to 2)
        assertThat(laid.getValue("b").lane to laid.getValue("b").lanes).isEqualTo(1 to 2)
        // c starts after a ends (10:00) but overlaps b (until 10:30)? No: b ends 10:30, c starts 10:45 → new cluster.
        assertThat(laid.getValue("c").lanes).isEqualTo(1)
        assertThat(laid.getValue("d").lanes).isEqualTo(1)
    }

    @Test
    fun `drop positions snap to quarter hours within the day`() {
        assertThat(CalendarBuilder.snapTime(9 * 60 + 7f)).isEqualTo(LocalTime.of(9, 0))
        assertThat(CalendarBuilder.snapTime(9 * 60 + 8f)).isEqualTo(LocalTime.of(9, 15))
        assertThat(CalendarBuilder.snapTime(-30f)).isEqualTo(LocalTime.MIDNIGHT)
        assertThat(CalendarBuilder.snapTime(24 * 60 + 20f)).isEqualTo(LocalTime.of(23, 45))
    }

    @Test
    fun `month navigation follows Jalali months`() {
        assertThat(CalendarBuilder.shift(CalendarMode.MONTH, jalali("1405-06-31"), 1).toJalali().toString()).isEqualTo("1405-07-30")
        assertThat(CalendarBuilder.shift(CalendarMode.WEEK, today, -1)).isEqualTo(today.minusWeeks(1))
    }

    @Test
    fun `month grid carries occasions and personal events`() {
        val birthday = PersonalEvent(
            id = "b", title = "تولد سارا", kind = EventKind.BIRTHDAY, calendar = EventCalendar.JALALI,
            month = 7, day = 20, year = 1375, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        val days = CalendarBuilder.build(CalendarMode.MONTH, today, today, emptyList(), true, true, 0, listOf(birthday))
        val hafez = days.single { it.date == jalali("1405-07-20") }
        assertThat(hafez.occasions.map { it.title }).contains("روز بزرگداشت حافظ")
        assertThat(hafez.events.single().count).isEqualTo(30)
        assertThat(hafez.isHoliday).isFalse()
        assertThat(days.filter { it.events.isNotEmpty() }).hasSize(1)
    }
}
