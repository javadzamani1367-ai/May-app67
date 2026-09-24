package ir.roozban.feature.habits

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import ir.roozban.core.domain.HabitDayStatus
import ir.roozban.core.domain.HabitUseCases
import ir.roozban.core.domain.ProvisionalEntitlements
import ir.roozban.core.domain.RoutineReminders
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.testing.FakeHabitRepository
import ir.roozban.core.testing.FakeRoutineAlarms
import ir.roozban.core.testing.FakeSettingsRepository
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
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {
    // پنجشنبه ۲ مهر ۱۴۰۵
    private val clock = TestClock(jalali("1405-07-02").atTime(10, 0))
    private val habits = FakeHabitRepository()
    private val alarms = FakeRoutineAlarms()
    private val routines = RoutineReminders(habits, FakeSettingsRepository(), alarms, clock)
    private val useCases = HabitUseCases(habits, routines, clock)
    private val today = clock.now.toLocalDate()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `create, tap today and see the week and progress`() = runTest {
        val vm = HabitsViewModel(habits, useCases, ProvisionalEntitlements(clock), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.save(HabitDraft(name = "ورزش", schedule = HabitSchedule.Daily, reminder = LocalTime.of(20, 0)))
        val card = vm.state.value.active.single()
        assertThat(card.week).hasSize(7)
        assertThat(card.week.first().date.dayOfWeek).isEqualTo(java.time.DayOfWeek.SATURDAY)
        assertThat(card.week.single { it.isToday }.status).isEqualTo(HabitDayStatus.PENDING)
        assertThat(card.week.last().isFuture).isTrue()
        assertThat(vm.state.value.dueToday).isEqualTo(1)
        assertThat(alarms.habits).hasSize(1)

        vm.tap(card.habit, today)
        val after = vm.state.value
        assertThat(after.doneToday).isEqualTo(1)
        assertThat(after.active.single().stats.current).isEqualTo(1)
    }

    @Test
    fun `editing keeps history and archiving moves the habit`() = runTest {
        val vm = HabitsViewModel(habits, useCases, ProvisionalEntitlements(clock), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.save(HabitDraft(name = "کتاب"))
        val habit = vm.state.value.active.single().habit
        vm.tap(habit, today)
        vm.save(HabitDraft.of(habit).copy(name = "کتاب خواندن", target = 2))
        val edited = vm.state.value.active.single()
        assertThat(edited.habit.name).isEqualTo("کتاب خواندن")
        assertThat(edited.week.single { it.isToday }.count).isEqualTo(1)
        vm.setArchived(edited.habit, true)
        assertThat(vm.state.value.active).isEmpty()
        assertThat(vm.state.value.archived).hasSize(1)
    }

    @Test
    fun `detail shows the Jalali month with leading blanks`() = runTest {
        val habit = useCases.create("ورزش", 0, HabitSchedule.Daily, 1, null)!!
        val vm = HabitDetailViewModel(SavedStateHandle(mapOf("habitId" to habit.id)), habits, useCases, ProvisionalEntitlements(clock), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val s = vm.state.value
        // ۱ مهر ۱۴۰۵ is a Wednesday: four blanks (Sat..Tue) then 30 days.
        assertThat(s.cells.take(4).all { it == null }).isTrue()
        assertThat(s.cells.filterNotNull()).hasSize(30)
        vm.tap(today)
        assertThat(vm.state.value.stats!!.current).isEqualTo(1)
        vm.delete()
        assertThat(vm.state.value.deleted).isTrue()
    }
}
