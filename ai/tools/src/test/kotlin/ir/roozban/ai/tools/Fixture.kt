package ir.roozban.ai.tools

import ir.roozban.core.domain.AddTaskUseCase
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.DeleteTaskUseCase
import ir.roozban.core.domain.FocusService
import ir.roozban.core.domain.HabitUseCases
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RoutineReminders
import ir.roozban.core.domain.TagResolver
import ir.roozban.core.domain.UpdateTaskUseCase
import ir.roozban.core.model.Habit
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.testing.FakeAlarmScheduler
import ir.roozban.core.testing.FakeFocusRepository
import ir.roozban.core.testing.FakeFocusStateStore
import ir.roozban.core.testing.FakeFocusSystem
import ir.roozban.core.testing.FakeHabitRepository
import ir.roozban.core.testing.FakeLabelRepository
import ir.roozban.core.testing.FakeProjectRepository
import ir.roozban.core.testing.FakeReminderRepository
import ir.roozban.core.testing.FakeRoutineAlarms
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/** Everything wired to fakes, at پنجشنبه ۲ مهر ۱۴۰۵ ۱۰:۰۰. */
class Fixture {
    val clock = TestClock(jalali("1405-07-02").atTime(10, 0))
    val today: LocalDate = clock.now.toLocalDate()
    val tasks = FakeTaskRepository()
    val settings = FakeSettingsRepository()
    val sync = ReminderSync(FakeReminderRepository(), tasks, settings, FakeAlarmScheduler(), clock)
    val projects = FakeProjectRepository()
    val add = AddTaskUseCase(tasks, settings, sync, TagResolver(projects, FakeLabelRepository(), clock), clock)
    val habits = FakeHabitRepository()
    val habitUseCases = HabitUseCases(habits, RoutineReminders(habits, settings, FakeRoutineAlarms(), clock), clock)
    val focusStore = FakeFocusStateStore()
    val focus = FocusService(focusStore, FakeFocusRepository(tasks), FakeFocusSystem(), tasks, settings, clock)
    val executor = ToolExecutor(
        tasks, add, UpdateTaskUseCase(tasks, sync, clock), CompleteTaskUseCase(tasks, sync, clock),
        DeleteTaskUseCase(tasks, sync), habits, habitUseCases, focus, clock,
    )

    suspend fun task(title: String, due: TaskDue? = null, minutes: Int? = null): Task {
        val now = clock.instant()
        val t = Task(id = "t-${title.hashCode()}", title = title, due = due, estimateMinutes = minutes, createdAt = now, updatedAt = now)
        tasks.upsert(t)
        return t
    }

    suspend fun habit(name: String, perDay: Int = 1): Habit =
        habitUseCases.create(name, 0, ir.roozban.core.model.HabitSchedule.Daily, perDay, null)!!

    suspend fun context() = AssistantContext(clock.now, tasks.observeOpenTasks().first(), habits.all(), settings.current())

    fun at(days: Long, h: Int, m: Int = 0) = TaskDue.At(today.plusDays(days), LocalTime.of(h, m))
}
