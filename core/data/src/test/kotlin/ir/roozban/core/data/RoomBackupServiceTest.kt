package ir.roozban.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import ir.roozban.core.database.RoozbanDatabase
import ir.roozban.core.domain.AddTaskUseCase
import ir.roozban.core.domain.EventReminders
import ir.roozban.core.domain.EventUseCases
import ir.roozban.core.domain.HabitUseCases
import ir.roozban.core.model.EventCalendar
import ir.roozban.core.model.EventKind
import ir.roozban.core.domain.RoutineReminders
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.model.TimeEntry
import ir.roozban.core.model.TimeSource
import java.time.Instant
import java.time.LocalTime
import ir.roozban.core.domain.QuickAddParser
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RestoreMode
import ir.roozban.core.domain.RestoreResult
import ir.roozban.core.domain.TagResolver
import ir.roozban.core.testing.FakeAlarmScheduler
import ir.roozban.core.testing.FakeRoutineAlarms
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomBackupServiceTest {
    private val clock = TestClock(jalali("1405-07-02").atTime(10, 0))
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RoozbanDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val tasks = RoomTaskRepository(db.taskDao(), clock)
    private val projects = RoomProjectRepository(db.projectDao(), db.taskDao())
    private val labels = RoomLabelRepository(db.labelDao())
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeAlarmScheduler()
    private val sync = ReminderSync(RoomReminderRepository(db.reminderDao()), tasks, settings, scheduler, clock)
    private val add = AddTaskUseCase(tasks, settings, sync, TagResolver(projects, labels, clock), clock)
    private val habitRepo = RoomHabitRepository(db.habitDao())
    private val routineAlarms = FakeRoutineAlarms()
    private val routines = RoutineReminders(habitRepo, settings, routineAlarms, clock)
    private val habits = HabitUseCases(habitRepo, routines, clock)
    private val focus = RoomFocusRepository(db.focusDao())
    private val eventRepo = RoomEventRepository(db.eventDao())
    private val eventReminders = EventReminders(eventRepo, settings, routineAlarms, clock)
    private val events = EventUseCases(eventRepo, eventReminders, clock)
    private val backup = RoomBackupService(db.backupDao(), settings, sync, routines, eventReminders, clock)
    private val password = "رمز۱۲۳۴".toCharArray()

    @After
    fun tearDown() = db.close()

    private suspend fun quickAdd(text: String) = add(QuickAddParser().parse(text, clock.now, settings.current()))!!

    @Test
    fun `replace restores everything and reschedules reminders`() = runTest {
        val task = quickAdd("جلسه فردا ساعت ۱۰ #کار @مهم")
        val file = backup.createBackup(password)

        quickAdd("کار اضافه")
        settings.update { it.copy(eveningHour = 18) }
        scheduler.scheduled.clear()

        val result = backup.restore(file, password, RestoreMode.REPLACE)
        assertThat(result).isEqualTo(RestoreResult.Success(tasks = 1, projects = 1))
        val open = tasks.observeOpenTasks().first()
        assertThat(open.map { it.title }).containsExactly("جلسه")
        assertThat(open.single().labelIds).isEqualTo(task.labelIds)
        assertThat(projects.all().single().name).isEqualTo("کار")
        assertThat(settings.current().eveningHour).isEqualTo(17)
        assertThat(scheduler.scheduled).containsKey(task.id)
    }

    @Test
    fun `merge keeps local additions`() = runTest {
        quickAdd("قدیمی")
        val file = backup.createBackup(password)
        quickAdd("جدید")
        backup.restore(file, password, RestoreMode.MERGE)
        assertThat(tasks.observeOpenTasks().first().map { it.title }).containsExactly("قدیمی", "جدید")
    }

    @Test
    fun `wrong password and garbage are reported`() = runTest {
        quickAdd("کار")
        val file = backup.createBackup(password)
        assertThat(backup.restore(file, "اشتباه".toCharArray(), RestoreMode.REPLACE)).isEqualTo(RestoreResult.WrongPassword)
        assertThat(backup.restore(ByteArray(100) { 7 }, password, RestoreMode.REPLACE)).isInstanceOf(RestoreResult.Invalid::class.java)
        assertThat(tasks.observeOpenTasks().first()).hasSize(1)
    }

    @Test
    fun `habits, logs and focus history survive a restore and reminders are re-armed`() = runTest {
        val habit = habits.create("ورزش", 1, HabitSchedule.Daily, 2, LocalTime.of(20, 0))!!
        habits.tap(habit, clock.now.toLocalDate())
        val t = Instant.now(clock)
        focus.record(
            FocusSession("f1", null, t, t.plusSeconds(1500), 25, 1500, true),
            TimeEntry("e1", null, t, t.plusSeconds(1500), TimeSource.FOCUS),
        )
        settings.update { it.copy(focus = it.focus.copy(workMinutes = 50)) }
        val file = backup.createBackup(password)

        habits.delete(habit)
        settings.update { it.copy(focus = it.focus.copy(workMinutes = 25)) }
        routineAlarms.habits.clear()

        backup.restore(file, password, RestoreMode.REPLACE)
        assertThat(habitRepo.all().single().targetPerDay).isEqualTo(2)
        assertThat(habitRepo.log(habit.id, clock.now.toLocalDate())?.count).isEqualTo(1)
        assertThat(focus.observeSessions(t.minusSeconds(1), t.plusSeconds(3600)).first().map { it.id }).containsExactly("f1")
        assertThat(focus.observeTaskSeconds("none").first()).isEqualTo(0L)
        assertThat(settings.current().focus.workMinutes).isEqualTo(50)
        assertThat(routineAlarms.habits).containsKey(habit.id)
    }

    @Test
    fun `personal events survive a restore with their reminders`() = runTest {
        val ev = events.create(
            "تولد سارا", EventKind.BIRTHDAY, 3, jalali("1375-07-15"), EventCalendar.JALALI, true, setOf(0, 7), LocalTime.of(8, 30),
        )!!
        val file = backup.createBackup(password)
        events.delete(ev)
        routineAlarms.events.clear()
        backup.restore(file, password, RestoreMode.REPLACE)
        val restored = eventRepo.all().single()
        assertThat(restored).isEqualTo(ev)
        assertThat(routineAlarms.events).containsKey(ev.id)
    }
}
