package ir.roozban.feature.tasks

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.domain.DoneHistory
import ir.roozban.core.domain.DoneItem
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.ReopenTaskUseCase
import ir.roozban.core.model.Project
import ir.roozban.core.model.Task
import ir.roozban.core.testing.FakeAlarmScheduler
import ir.roozban.core.testing.FakeProjectRepository
import ir.roozban.core.testing.FakeReminderRepository
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DoneViewModelTest {
    private val clock = TestClock(jalali("1405-07-07").atTime(20, 0))
    private val tasks = FakeTaskRepository()
    private val projects = FakeProjectRepository()
    private val settings = FakeSettingsRepository()
    private val sync = ReminderSync(FakeReminderRepository(), tasks, settings, FakeAlarmScheduler(), clock)

    /** Done items and which of them are hidden, like the database. */
    private class FakeHistory(items: List<DoneItem>) : DoneHistory {
        val items = MutableStateFlow(items)
        val hidden = MutableStateFlow<Set<DoneItem>>(emptySet())

        override fun observeDone(): Flow<List<DoneItem>> =
            kotlinx.coroutines.flow.combine(items, hidden) { all, h -> all.filter { it !in h }.sortedByDescending { it.at } }

        override suspend fun hide(item: DoneItem) = hidden.update { it + item }

        override suspend fun unhide(item: DoneItem) = hidden.update { it - item }

        private inline fun <T> MutableStateFlow<T>.update(f: (T) -> T) {
            value = f(value)
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val today: LocalDate = LocalDate.now(clock)
    private fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(clock.zone).toInstant()

    private val report = DoneItem("a", "گزارش ماهانه", "p", at(0, 9), null)
    private val gym = DoneItem("r", "باشگاه", null, at(1, 18), today.minusDays(1))
    private val call = DoneItem("b", "تماس با بانک", null, at(1, 10), null)

    private fun viewModel(history: DoneHistory) = DoneViewModel(
        history = history,
        projects = projects,
        tasks = tasks,
        reopenTask = ReopenTaskUseCase(tasks, sync, clock),
        reminders = sync,
        clock = clock,
    )

    @Test
    fun `groups by day, newest first, and searches titles and projects`() = runTest {
        projects.upsert(Project("p", "کار", createdAt = clock.instant(), updatedAt = clock.instant()))
        val vm = viewModel(FakeHistory(listOf(call, report, gym)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        val days = vm.state.value.days
        assertThat(days.map { it.label }).containsExactly("امروز", "دیروز").inOrder()
        assertThat(days[1].entries.map { it.item.title }).containsExactly("باشگاه", "تماس با بانک").inOrder()
        assertThat(days[0].entries.single().project?.name).isEqualTo("کار")
        assertThat(days[1].entries.first().canReopen).isFalse()

        vm.search("كار") // Arabic kaf still finds the project «کار»
        assertThat(vm.state.value.days.flatMap { it.entries }.map { it.item.taskId }).containsExactly("a")
        assertThat(vm.state.value.total).isEqualTo(3)
        vm.search("بانک تماس")
        assertThat(vm.state.value.days.flatMap { it.entries }.map { it.item.taskId }).containsExactly("b")
    }

    @Test
    fun `removing hides it from the list with undo`() = runTest {
        val history = FakeHistory(listOf(report, call))
        val vm = viewModel(history)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val entry = vm.state.value.days.first().entries.first()

        vm.remove(entry)
        assertThat(vm.state.value.total).isEqualTo(1)
        val message = vm.messages.first()
        vm.undo(message.undo!!)
        assertThat(vm.state.value.total).isEqualTo(2)
    }

    @Test
    fun `reopening sends a one-off task back to the open list, with undo`() = runTest {
        val done = Task("a", "گزارش ماهانه", completedAt = report.at, createdAt = report.at, updatedAt = report.at)
        tasks.upsert(done)
        val history = FakeHistory(emptyList())
        history.items.value = listOf(report)
        val vm = viewModel(history)

        vm.reopen(DoneEntry(report, null))
        assertThat(tasks.get("a")?.isCompleted).isFalse()
        vm.undo(vm.messages.first().undo!!)
        assertThat(tasks.get("a")?.isCompleted).isTrue()
    }

    @Test
    fun `a recurring occurrence cannot be reopened`() = runTest {
        tasks.upsert(Task("r", "باشگاه", recurrence = "FREQ=DAILY", createdAt = gym.at, updatedAt = gym.at))
        val vm = viewModel(FakeHistory(listOf(gym)))
        vm.reopen(DoneEntry(gym, null))
        assertThat(tasks.observeOpenTasks().map { l -> l.map { it.id } }.first()).containsExactly("r")
    }
}
