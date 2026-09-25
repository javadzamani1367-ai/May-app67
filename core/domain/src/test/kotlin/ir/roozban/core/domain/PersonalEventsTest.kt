package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.calendar.HijriDates
import ir.roozban.core.model.EventCalendar
import ir.roozban.core.model.EventKind
import ir.roozban.core.testing.FakeEventRepository
import ir.roozban.core.testing.FakeRoutineAlarms
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class PersonalEventsTest {
    private class E {
        val h = Harness() // پنجشنبه ۲ مهر ۱۴۰۵، ۱۰:۰۰
        val repo = FakeEventRepository()
        val alarms = FakeRoutineAlarms()
        val reminders = EventReminders(repo, h.settings, alarms, h.clock)
        val use = EventUseCases(repo, reminders, h.clock)
        val today: LocalDate get() = h.clock.now.toLocalDate()
    }

    @Test
    fun `a Jalali birthday repeats every year and counts the age`() = runTest {
        val e = E()
        val birthday = e.use.create("تولد سارا", EventKind.BIRTHDAY, 3, jalali("1375-07-15"), EventCalendar.JALALI, true, setOf(0, 1), LocalTime.of(9, 0))!!
        val next = EventDates.next(birthday, e.today)!!
        assertThat(next.date).isEqualTo(jalali("1405-07-15"))
        assertThat(next.count).isEqualTo(30)
        val all = EventDates.between(birthday, jalali("1405-01-01"), jalali("1407-12-29"))
        assertThat(all.map { it.date }).containsExactly(jalali("1405-07-15"), jalali("1406-07-15"), jalali("1407-07-15")).inOrder()
        // Reminder the day before at 09:00.
        assertThat(e.alarms.events[birthday.id]).isEqualTo(jalali("1405-07-14").atTime(9, 0))
    }

    @Test
    fun `30 Esfand falls on the 29th in a common year and nothing before the first year`() = runTest {
        val e = E()
        // 1403 is a leap year, 1404 is not.
        val ev = e.use.create("سالگرد", EventKind.WEDDING, 0, jalali("1403-12-30"), EventCalendar.JALALI, true, emptySet(), LocalTime.NOON)!!
        val dates = EventDates.between(ev, jalali("1400-01-01"), jalali("1405-01-10")).map { it.date }
        assertThat(dates).containsExactly(jalali("1403-12-30"), jalali("1404-12-29")).inOrder()
        assertThat(e.alarms.events).doesNotContainKey(ev.id)
    }

    @Test
    fun `gregorian and hijri anniversaries`() = runTest {
        val e = E()
        val g = e.use.create("سالگرد عقد", EventKind.ENGAGEMENT, 1, LocalDate.of(2020, 2, 29), EventCalendar.GREGORIAN, true, setOf(0), LocalTime.of(8, 0))!!
        assertThat(EventDates.next(g, e.today)!!.date).isEqualTo(LocalDate.of(2027, 2, 28))
        val start = jalali("1405-01-01")
        val hijri = e.use.create("نذر", EventKind.OTHER, 2, start, EventCalendar.HIJRI, false, setOf(0), LocalTime.of(8, 0))!!
        val next = EventDates.next(hijri, start.plusDays(1))!!
        val h = HijriDates.from(next.date)!!
        assertThat(h.month to h.day).isEqualTo(hijri.month to hijri.day)
        assertThat(next.date.toEpochDay() - start.toEpochDay()).isIn(350L..356L)
    }

    @Test
    fun `an alarm announces the due reminder and arms the next one`() = runTest {
        val e = E()
        val ev = e.use.create("تولد مادر", EventKind.BIRTHDAY, 0, jalali("1350-07-05"), EventCalendar.JALALI, true, setOf(0, 3), LocalTime.of(9, 0))!!
        assertThat(e.alarms.events[ev.id]).isEqualTo(jalali("1405-07-02").atTime(9, 0).let { if (it.isAfter(e.h.clock.now)) it else jalali("1405-07-05").atTime(9, 0) })
        e.h.clock.now = jalali("1405-07-05").atTime(9, 0)
        val due = e.reminders.onAlarm(ev.id)!!
        assertThat(due.daysBefore).isEqualTo(0)
        assertThat(due.occurrence.count).isEqualTo(55)
        // Next: 3 days before next year's birthday.
        assertThat(e.alarms.events[ev.id]).isEqualTo(jalali("1406-07-02").atTime(9, 0))
        e.use.delete(ev)
        assertThat(e.alarms.events).isEmpty()
    }
}
