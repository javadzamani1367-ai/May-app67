package ir.roozban.feature.tasks

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.TaskDue
import ir.roozban.core.recurrence.Frequency
import ir.roozban.core.recurrence.RecurrenceSpec
import ir.roozban.core.testing.jalali
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

class TaskFormatterTest {
    private val today = jalali("1405-07-02")

    @Test
    fun recurrence() {
        fun text(r: RecurrenceSpec) = TaskFormatter.recurrence(r)
        assertThat(text(RecurrenceSpec(Frequency.DAILY))).isEqualTo("هر روز")
        assertThat(text(RecurrenceSpec(Frequency.DAILY, interval = 2))).isEqualTo("یک روز در میان")
        assertThat(text(RecurrenceSpec(Frequency.DAILY, interval = 3))).isEqualTo("هر ۳ روز")
        assertThat(text(RecurrenceSpec(Frequency.WEEKLY))).isEqualTo("هر هفته")
        assertThat(text(RecurrenceSpec(Frequency.WEEKLY, byWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY))))
            .isEqualTo("هر شنبه، دوشنبه")
        assertThat(text(RecurrenceSpec(Frequency.MONTHLY, jalaliMonthDay = -1))).isEqualTo("آخر هر ماه")
        assertThat(text(RecurrenceSpec(Frequency.MONTHLY, jalaliMonthDay = 15))).isEqualTo("روز ۱۵ هر ماه")
        assertThat(text(RecurrenceSpec(Frequency.YEARLY))).isEqualTo("هر سال")
        assertThat(TaskFormatter.recurrence("FREQ=DAILY")).isEqualTo("هر روز")
        assertThat(TaskFormatter.recurrence("garbage")).isNull()
    }

    @Test
    fun duration() {
        assertThat(TaskFormatter.duration(Duration.ofMinutes(45))).isEqualTo("۴۵ دقیقه")
        assertThat(TaskFormatter.duration(Duration.ofMinutes(60))).isEqualTo("۱ ساعت")
        assertThat(TaskFormatter.duration(Duration.ofMinutes(90))).isEqualTo("۱ ساعت و نیم")
        assertThat(TaskFormatter.duration(Duration.ofMinutes(135))).isEqualTo("۲ ساعت و ۱۵ دقیقه")
    }

    @Test
    fun `due labels`() {
        val tomorrow5 = TaskDue.At(today.plusDays(1), LocalTime.of(17, 0))
        assertThat(TaskFormatter.due(tomorrow5, today)).isEqualTo("فردا، ۱۷:۰۰")
        assertThat(TaskFormatter.dueInSection(tomorrow5, today.plusDays(1), today)).isEqualTo("۱۷:۰۰")
        assertThat(TaskFormatter.dueInSection(TaskDue.AllDay(today), today, today)).isNull()
        assertThat(TaskFormatter.dueInSection(TaskDue.AllDay(today.minusDays(1)), today, today)).isEqualTo("دیروز")
    }

    @Test
    fun `reminder and headings`() {
        assertThat(TaskFormatter.reminder(null)).isEqualTo("بدون یادآوری")
        assertThat(TaskFormatter.reminder(ReminderSetting(ReminderKind.NOTIFICATION))).isEqualTo("اعلان سر وقت")
        assertThat(TaskFormatter.reminder(ReminderSetting(ReminderKind.ALARM, 15))).isEqualTo("آلارم ۱۵ دقیقه قبل")
        assertThat(TaskFormatter.dayHeading(today.plusDays(1), today)).isEqualTo("فردا · ۳ مهر")
        assertThat(TaskFormatter.dayHeading(today.plusDays(3), today)).isEqualTo("یکشنبه · ۵ مهر")
        assertThat(TaskFormatter.dayHeading(today.plusDays(13), today)).isEqualTo("۱۵ مهر")
    }
}
