package ir.roozban.feature.today

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.timeparser.Frequency
import ir.roozban.core.timeparser.RecurrenceSpec
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration

class PreviewFormatterTest {

    @Test
    fun recurrence() {
        fun text(r: RecurrenceSpec) = PreviewFormatter.recurrence(r)
        assertThat(text(RecurrenceSpec(Frequency.DAILY))).isEqualTo("هر روز")
        assertThat(text(RecurrenceSpec(Frequency.DAILY, interval = 2))).isEqualTo("یک روز در میان")
        assertThat(text(RecurrenceSpec(Frequency.DAILY, interval = 3))).isEqualTo("هر ۳ روز")
        assertThat(text(RecurrenceSpec(Frequency.WEEKLY))).isEqualTo("هر هفته")
        assertThat(text(RecurrenceSpec(Frequency.WEEKLY, byWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY))))
            .isEqualTo("هر شنبه، دوشنبه")
        assertThat(text(RecurrenceSpec(Frequency.MONTHLY, jalaliMonthDay = -1))).isEqualTo("آخر هر ماه")
        assertThat(text(RecurrenceSpec(Frequency.MONTHLY, jalaliMonthDay = 15))).isEqualTo("روز ۱۵ هر ماه")
        assertThat(text(RecurrenceSpec(Frequency.YEARLY))).isEqualTo("هر سال")
    }

    @Test
    fun duration() {
        assertThat(PreviewFormatter.duration(Duration.ofMinutes(45))).isEqualTo("۴۵ دقیقه")
        assertThat(PreviewFormatter.duration(Duration.ofMinutes(60))).isEqualTo("۱ ساعت")
        assertThat(PreviewFormatter.duration(Duration.ofMinutes(90))).isEqualTo("۱ ساعت و نیم")
        assertThat(PreviewFormatter.duration(Duration.ofMinutes(135))).isEqualTo("۲ ساعت و ۱۵ دقیقه")
    }
}
