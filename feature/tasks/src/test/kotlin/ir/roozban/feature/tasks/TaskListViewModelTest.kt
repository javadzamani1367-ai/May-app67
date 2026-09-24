package ir.roozban.feature.tasks

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import ir.roozban.core.domain.AddTaskUseCase
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.HighlightKind
import ir.roozban.core.domain.QuickAddParser
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.ReopenTaskUseCase
import ir.roozban.core.domain.TagResolver
import ir.roozban.core.testing.FakeAlarmScheduler
import ir.roozban.core.testing.FakeLabelRepository
import ir.roozban.core.testing.FakeProjectRepository
import ir.roozban.core.testing.FakeReminderRepository
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {
    private val clock = TestClock(jalali("1405-07-02").atTime(10, 0))
    private val tasks = FakeTaskRepository()
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeAlarmScheduler()
    private val sync = ReminderSync(FakeReminderRepository(), tasks, settings, scheduler, clock)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val projects = FakeProjectRepository()
    private val labels = FakeLabelRepository()

    private fun viewModel(mode: ListMode = ListMode.TODAY) = TaskListViewModel(
        tasks = tasks,
        projects = projects,
        labels = labels,
        settings = settings,
        parser = QuickAddParser(),
        addTask = AddTaskUseCase(tasks, settings, sync, TagResolver(projects, labels, clock), clock),
        completeTask = CompleteTaskUseCase(tasks, sync, clock),
        reopenTask = ReopenTaskUseCase(tasks, sync, clock),
        clock = clock,
    ).also { it.setMode(mode) }

    @Test
    fun `quick add previews, saves and shows the task today`() = runTest {
        val vm = viewModel()
        vm.state.test {
            assertThat(awaitItem().sections).isEmpty()

            vm.onQuickAddTextChange("جلسه با علی امروز ساعت ۵ !مهم")
            val preview = vm.quickAdd.value.preview!!
            assertThat(preview.title).isEqualTo("جلسه با علی")
            assertThat(preview.chips.map { it.kind }).containsExactly(ChipKind.TIME, ChipKind.PRIORITY).inOrder()
            assertThat(preview.highlights.map { it.kind }).containsExactly(HighlightKind.TIME, HighlightKind.PRIORITY).inOrder()

            assertThat(vm.submitQuickAdd()).isTrue()
            var state = awaitItem()
            while (state.sections.isEmpty()) state = awaitItem()
            val today = state.sections.single { it.kind == SectionKind.TODAY }
            assertThat(today.tasks.single().title).isEqualTo("جلسه با علی")
            assertThat(today.tasks.single().dueLabel).isEqualTo("۱۷:۰۰")
            assertThat(scheduler.scheduled).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing offers undo`() = runTest {
        val vm = viewModel()
        vm.onQuickAddTextChange("خرید نان امروز عصر")
        vm.submitQuickAdd()
        val id = tasks.tasks.value.keys.single()
        vm.events.test {
            assertThat(awaitItem()).isInstanceOf(TaskListEvent.Message::class.java) // «ثبت شد»
            vm.onToggleComplete(id)
            val done = awaitItem() as TaskListEvent.Message
            assertThat(done.text).isEqualTo("انجام شد")
            assertThat(tasks.get(id)!!.isCompleted).isTrue()
            vm.undo(done.undo!!)
            assertThat(tasks.get(id)!!.isCompleted).isFalse()
        }
    }

    @Test
    fun `quick add with a project shows it on the row`() = runTest {
        val vm = viewModel()
        vm.state.test {
            vm.onQuickAddTextChange("خرید نان امروز #خانه @خرید")
            assertThat(vm.quickAdd.value.preview!!.chips.map { it.kind })
                .containsExactly(ChipKind.TIME, ChipKind.PROJECT, ChipKind.LABEL).inOrder()
            vm.submitQuickAdd()
            var state = awaitItem()
            while (state.sections.isEmpty() || state.sections.first().tasks.first().project == null) state = awaitItem()
            val item = state.sections.first().tasks.single()
            assertThat(item.project?.name).isEqualTo("خانه")
            assertThat(item.labels.map { it.name }).containsExactly("خرید")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a title is required`() = runTest {
        val vm = viewModel()
        vm.onQuickAddTextChange("فردا عصر")
        assertThat(vm.submitQuickAdd()).isFalse()
        assertThat(tasks.tasks.value).isEmpty()
    }
}
