package ir.roozban.feature.reports

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.domain.Period
import ir.roozban.core.domain.ReportRange
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.Task
import ir.roozban.core.model.TimeEntry
import ir.roozban.core.model.TimeSource
import ir.roozban.core.testing.FakeFocusRepository
import ir.roozban.core.testing.FakeHabitRepository
import ir.roozban.core.testing.FakeProjectRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TEHRAN
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private val clock = TestClock(jalali("1405-07-02").atTime(10, 0))
    private val tasks = FakeTaskRepository()
    private val focus = FakeFocusRepository(tasks)
    private val habits = FakeHabitRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `week report, previous period and navigation`() = runTest {
        val now = Instant.now(clock)
        tasks.upsert(Task("a", "گزارش", completedAt = now, createdAt = now, updatedAt = now))
        focus.record(
            FocusSession("s", "a", now.minusSeconds(1500), now, 25, 1500, true),
            TimeEntry("e", "a", now.minusSeconds(1500), now, TimeSource.FOCUS),
        )
        val vm = ReportsViewModel(ReportSource(tasks, focus, habits, clock), FakeProjectRepository(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val s = vm.state.value
        assertThat(s.report!!.period).isEqualTo(Period.week(clock.now.toLocalDate()))
        assertThat(s.report!!.completed).isEqualTo(1)
        assertThat(s.report!!.focusMinutes).isEqualTo(25)
        assertThat(s.previous!!.completed).isEqualTo(0)
        assertThat(s.isCurrent).isTrue()

        vm.next() // no future periods
        assertThat(vm.state.value.isCurrent).isTrue()
        vm.previous()
        assertThat(vm.state.value.isCurrent).isFalse()
        assertThat(vm.state.value.report!!.completed).isEqualTo(0)
        vm.setRange(ReportRange.MONTH)
        assertThat(vm.state.value.report!!.period.start).isEqualTo(jalali("1405-07-01"))
    }

    @Test
    fun `formatting in Persian`() {
        assertThat(formatMinutes(45)).isEqualTo("۴۵ دقیقه")
        assertThat(formatMinutes(120)).isEqualTo("۲ ساعت")
        assertThat(formatMinutes(80)).isEqualTo("۱ ساعت و ۲۰ دقیقه")
        assertThat(formatMinutesShort(90)).isEqualTo("۱٫۵س")
        assertThat(formatHour(10)).isEqualTo("۱۰ صبح")
        assertThat(formatHour(15)).isEqualTo("۳ بعدازظهر")
        assertThat(formatChange(12, 10)).isEqualTo("+۲۰٪")
        assertThat(formatChange(5, 10)).isEqualTo("−۵۰٪")
        assertThat(formatChange(5, 0)).isNull()
        assertThat(formatPeriod(Period(jalali("1405-07-04"), jalali("1405-07-10")), false)).isEqualTo("۴ تا ۱۰ مهر ۱۴۰۵")
        assertThat(formatPeriod(Period(jalali("1405-06-29"), jalali("1405-07-04")), false)).isEqualTo("۲۹ شهریور تا ۴ مهر ۱۴۰۵")
        assertThat(TEHRAN.id).isEqualTo("Asia/Tehran")
    }
}
