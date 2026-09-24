package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.UserSettings
import ir.roozban.core.testing.FakeAlarmScheduler
import ir.roozban.core.testing.FakeReminderRepository
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import org.junit.jupiter.api.Test
import java.time.LocalTime

class QuickAddParserTest {
    private val parser = QuickAddParser()
    private val now = jalali("1405-07-02").atTime(10, 0)

    private fun parse(text: String, settings: UserSettings = UserSettings()) = parser.parse(text, now, settings)

    @Test
    fun `time and title`() {
        val r = parse("خرید نان فردا عصر")
        assertThat(r.title).isEqualTo("خرید نان")
        assertThat(r.due).isEqualTo(TaskDue.At(jalali("1405-07-03"), LocalTime.of(17, 0)))
        assertThat(r.important).isFalse()
    }

    @Test
    fun `priority markers`() {
        parse("گزارش !مهم فردا").let {
            assertThat(it.title).isEqualTo("گزارش")
            assertThat(it.important).isTrue()
            assertThat(it.urgent).isFalse()
            assertThat(it.highlights.map { h -> h.kind }).containsExactly(HighlightKind.PRIORITY, HighlightKind.TIME).inOrder()
        }
        parse("تمدید بیمه !فوری").let {
            assertThat(it.urgent).isTrue()
            assertThat(it.important).isFalse()
            assertThat(it.title).isEqualTo("تمدید بیمه")
        }
        parse("!! سرور خراب شده").let {
            assertThat(it.important && it.urgent).isTrue()
            assertThat(it.title).isEqualTo("سرور خراب شده")
        }
        // Not a marker inside a word or without "!".
        parse("کار مهم").let {
            assertThat(it.important).isFalse()
            assertThat(it.title).isEqualTo("کار مهم")
        }
    }

    @Test
    fun `settings change part-of-day hours`() {
        val r = parse("فردا عصر ورزش", UserSettings(eveningHour = 18))
        assertThat(r.due).isEqualTo(TaskDue.At(jalali("1405-07-03"), LocalTime.of(18, 0)))
    }

    @Test
    fun `recurrence and estimate`() {
        val r = parse("ورزش هر روز صبح ۳۰ دقیقه")
        assertThat(r.recurrence?.toRRule()).isEqualTo("FREQ=DAILY")
        assertThat(r.estimate?.toMinutes()).isEqualTo(30)
        assertThat(r.title).isEqualTo("ورزش")
    }

    @Test
    fun `project and labels`() {
        val r = parse("خرید نان #خانه @خرید @فوری_امروز فردا")
        assertThat(r.title).isEqualTo("خرید نان")
        assertThat(r.projectName).isEqualTo("خانه")
        assertThat(r.labelNames).containsExactly("خرید", "فوری امروز").inOrder()
        assertThat(r.highlights.map { it.kind })
            .containsExactly(HighlightKind.PROJECT, HighlightKind.LABEL, HighlightKind.LABEL, HighlightKind.TIME).inOrder()
        // An e-mail address is not a label.
        assertThat(parse("ارسال به ali@example.com").labelNames).isEmpty()
    }
}
