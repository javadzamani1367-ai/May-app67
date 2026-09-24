package ir.roozban.feature.focus

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.domain.FocusService
import ir.roozban.core.domain.ProvisionalEntitlements
import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusState
import ir.roozban.core.model.Task
import ir.roozban.core.testing.FakeFocusRepository
import ir.roozban.core.testing.FakeFocusStateStore
import ir.roozban.core.testing.FakeFocusSystem
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class FocusViewModelTest {
    private val clock = TestClock(jalali("1405-07-02").atTime(10, 0))
    private val tasks = FakeTaskRepository()
    private val settings = FakeSettingsRepository()
    private val store = FakeFocusStateStore()
    private val repo = FakeFocusRepository(tasks)
    private val system = FakeFocusSystem()
    private val service = FocusService(store, repo, system, tasks, settings, clock)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = FocusViewModel(service, settings, tasks, repo, ProvisionalEntitlements(clock), clock)

    @Test
    fun `idle shows a full work period and the chosen task`() = runTest {
        val now = Instant.now(clock)
        tasks.upsert(Task("t", "نوشتن", createdAt = now, updatedAt = now))
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.selectTask("t")
        val s = vm.state.value
        assertThat(s.timer).isEqualTo(FocusState.Idle)
        assertThat(s.remainingMillis).isEqualTo(25 * 60_000L)
        assertThat(s.taskTitle).isEqualTo("نوشتن")
        assertThat(s.progress).isEqualTo(0f)
    }

    @Test
    fun `start runs the chosen task and settings are clamped`() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.selectTask("t")
        vm.start()
        val running = store.state.value as FocusState.Running
        assertThat(running.taskId).isEqualTo("t")
        assertThat(running.phase).isEqualTo(FocusPhase.WORK)

        vm.updateSettings { it.copy(workMinutes = 500, shortBreakMinutes = 0) }
        val fs = settings.settings.first().focus
        assertThat(fs.workMinutes).isEqualTo(120)
        assertThat(fs.shortBreakMinutes).isEqualTo(1)
    }

    @Test
    fun `countdown formatting rounds up`() {
        assertThat(formatCountdown(25 * 60_000L)).isEqualTo("۲۵:۰۰")
        assertThat(formatCountdown(59_001)).isEqualTo("۰۱:۰۰")
        assertThat(formatCountdown(0)).isEqualTo("۰۰:۰۰")
    }
}
