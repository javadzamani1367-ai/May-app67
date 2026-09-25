package ir.roozban.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.Reminder
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.ReminderState
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: RoozbanDatabase
    private val tasks get() = db.taskDao()
    private val reminders get() = db.reminderDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RoozbanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private val day = LocalDate.of(2026, 9, 25)

    private fun task(id: String, due: TaskDue? = null, created: Long = 0) = Task(
        id = id,
        title = "کار $id",
        due = due,
        important = true,
        estimateMinutes = 30,
        recurrence = "FREQ=DAILY",
        recurrenceStart = day,
        reminder = ReminderSetting(ReminderKind.ALARM, 10),
        createdAt = Instant.ofEpochMilli(created),
        updatedAt = Instant.ofEpochMilli(created),
    )

    @Test
    fun `task round trip`() = runTest {
        val t = task("a", TaskDue.At(day, LocalTime.of(17, 30)))
        tasks.upsert(t.toEntity())
        assertThat(tasks.get("a")?.toModel()).isEqualTo(t)
        val allDay = task("b", TaskDue.AllDay(day))
        tasks.upsert(allDay.toEntity())
        assertThat(tasks.get("b")?.toModel()).isEqualTo(allDay)
    }

    @Test
    fun `open tasks are ordered by date, time, then undated`() = runTest {
        tasks.upsert(task("undated", created = 1).toEntity())
        tasks.upsert(task("later", TaskDue.At(day.plusDays(1), LocalTime.of(8, 0))).toEntity())
        tasks.upsert(task("allday", TaskDue.AllDay(day)).toEntity())
        tasks.upsert(task("evening", TaskDue.At(day, LocalTime.of(20, 0))).toEntity())
        tasks.upsert(task("morning", TaskDue.At(day, LocalTime.of(9, 0))).toEntity())
        tasks.upsert(task("done", TaskDue.AllDay(day)).copy(completedAt = Instant.ofEpochMilli(5)).toEntity())
        assertThat(tasks.observeOpen().first().map { it.task.id })
            .containsExactly("morning", "evening", "allday", "later", "undated").inOrder()
    }

    @Test
    fun `soft delete, restore and purge`() = runTest {
        tasks.upsert(task("a").toEntity())
        tasks.softDelete("a", at = 100)
        assertThat(tasks.get("a")).isNull()
        assertThat(tasks.observeOpen().first()).isEmpty()
        tasks.restore("a")
        assertThat(tasks.get("a")).isNotNull()
        tasks.softDelete("a", at = 100)
        assertThat(tasks.purgeDeleted(before = 200)).isEqualTo(1)
        tasks.restore("a")
        assertThat(tasks.get("a")).isNull()
    }

    @Test
    fun `pending reminders until a time, and cascade on purge`() = runTest {
        tasks.upsert(task("a").toEntity())
        tasks.upsert(task("b").toEntity())
        val soon = Reminder("a", day.atTime(9, 0), ReminderKind.NOTIFICATION, ReminderState.PENDING)
        val far = Reminder("b", day.plusDays(30).atTime(9, 0), ReminderKind.ALARM, ReminderState.PENDING)
        reminders.upsert(soon.toEntity())
        reminders.upsert(far.toEntity())
        assertThat(reminders.pendingUntil(day.plusDays(7).atTime(0, 0).toFloatingSeconds()).map { it.toModel() })
            .containsExactly(soon)
        reminders.markFired("a")
        assertThat(reminders.get("a")?.toModel()?.state).isEqualTo(ReminderState.FIRED)
        tasks.softDelete("b", at = 1)
        tasks.purgeDeleted(before = 2)
        assertThat(reminders.get("b")).isNull()
    }

    @Test
    fun `labels are stored through the junction table`() = runTest {
        db.labelDao().upsert(LabelEntity("l1", "خرید", 0, 1, 1))
        db.labelDao().upsert(LabelEntity("l2", "فوری", 0, 1, 1))
        val t = task("a").copy(labelIds = setOf("l1", "l2"))
        tasks.upsert(t.toEntity(), t.labelIds)
        assertThat(tasks.get("a")?.toModel()?.labelIds).containsExactly("l1", "l2")
        tasks.upsert(t.toEntity(), setOf("l2"))
        assertThat(tasks.get("a")?.toModel()?.labelIds).containsExactly("l2")
        db.labelDao().delete("l2")
        assertThat(tasks.get("a")?.toModel()?.labelIds).isEmpty()
    }

    @Test
    fun `subtasks are hidden from lists and counted for their parent`() = runTest {
        tasks.upsert(task("parent").toEntity())
        tasks.upsert(task("s1").copy(parentId = "parent").toEntity())
        tasks.upsert(task("s2").copy(parentId = "parent", completedAt = Instant.ofEpochMilli(9)).toEntity())
        assertThat(tasks.observeOpen().first().map { it.task.id }).containsExactly("parent")
        assertThat(tasks.observeSubtasks("parent").first().map { it.task.id }).containsExactly("s1", "s2").inOrder()
        assertThat(tasks.observeSubtaskProgress().first()).containsExactly(SubtaskProgressRow("parent", 2, 1))
        // Purging a deleted parent removes its subtasks too.
        tasks.softDelete("parent", at = 1)
        tasks.purgeDeleted(before = 2)
        assertThat(tasks.observeSubtasks("parent").first()).isEmpty()
    }

    @Test
    fun `projects count open top-level tasks and detach on delete`() = runTest {
        db.projectDao().upsert(ProjectEntity("p", "کار", 1, false, 0, 1, 1))
        tasks.upsert(task("a").copy(projectId = "p").toEntity())
        tasks.upsert(task("b").copy(projectId = "p").toEntity())
        tasks.upsert(task("done").copy(projectId = "p", completedAt = Instant.ofEpochMilli(1)).toEntity())
        assertThat(db.projectDao().observeOpenCounts().first()).containsExactly(ProjectCountRow("p", 2))
        assertThat(tasks.observeProjectTasks("p").first().map { it.task.id }).containsExactly("a", "b")
        tasks.detachProject("p")
        db.projectDao().delete("p")
        assertThat(tasks.get("a")?.task?.projectId).isNull()
    }

    @Test
    fun `backup replaceAll swaps the whole dataset`() = runTest {
        tasks.upsert(task("old").toEntity())
        db.backupDao().replaceAll(
            tasks = listOf(task("new").toEntity()),
            projects = listOf(ProjectEntity("p", "خانه", 0, false, 0, 1, 1)),
            labels = listOf(LabelEntity("l", "خرید", 0, 1, 1)),
            taskLabels = listOf(TaskLabelEntity("new", "l"), TaskLabelEntity("missing", "l")),
            completions = listOf(CompletionEntity("new", 1, 1)),
        )
        assertThat(db.backupDao().tasks().map { it.id }).containsExactly("new")
        assertThat(db.backupDao().taskLabels()).containsExactly(TaskLabelEntity("new", "l"))
        assertThat(db.backupDao().completions()).hasSize(1)
    }

    @Test
    fun completions() = runTest {
        tasks.upsert(task("a").toEntity())
        tasks.insertCompletion(CompletionEntity("a", day.toEpochDay(), 1))
        tasks.insertCompletion(CompletionEntity("a", day.plusDays(1).toEpochDay(), 2))
        tasks.deleteCompletion("a", day.toEpochDay())
        assertThat(tasks.completions("a").map { it.occurrence }).containsExactly(day.plusDays(1).toEpochDay())
    }

    @Test
    fun `completion events include finished tasks and recurring occurrences`() = runTest {
        tasks.upsert(task("a").copy(completedAt = Instant.ofEpochMilli(5_000)).toEntity())
        tasks.upsert(task("r").toEntity())
        tasks.insertCompletion(CompletionEntity("r", 20_000, 6_000))
        tasks.insertCompletion(CompletionEntity("r", 19_999, 100))
        val events = tasks.observeCompletionEvents(1_000, 10_000).first()
        assertThat(events.map { it.taskId to it.at }).containsExactly("a" to 5_000L, "r" to 6_000L)
        assertThat(events.first { it.taskId == "r" }.title).isEqualTo("کار r")
    }

    @Test
    fun `focus sessions and tracked time with their task`() = runTest {
        tasks.upsert(task("a").toEntity())
        val focus = db.focusDao()
        focus.record(FocusSessionEntity("s1", "a", 0, 1_500_000, 25, 1500, true), TimeEntryEntity("e1", "a", 0, 1_500_000, "FOCUS"))
        focus.upsertEntry(TimeEntryEntity("e2", null, 2_000_000, 2_600_000, "MANUAL"))
        assertThat(focus.observeSessions(0, 2_000_000).first().map { it.id }).containsExactly("s1")
        val tracked = focus.observeTracked(1_000_000, 3_000_000).first()
        assertThat(tracked.map { it.entry.id to it.taskTitle }).containsExactly("e1" to "کار a", "e2" to null).inOrder()
        assertThat(focus.observeTaskSeconds("a").first()).isEqualTo(1500L)
        focus.deleteEntry("e1")
        assertThat(focus.observeTaskSeconds("a").first()).isEqualTo(0L)
    }

    @Test
    fun `habit logs are ranged and deleted with the habit`() = runTest {
        val habits = db.habitDao()
        habits.upsert(HabitEntity("h", "ورزش", 0, "D", 1, null, 20_000, false, 0, 1, 1))
        habits.upsertLog(HabitLogEntity("h", 20_001, 1, 1))
        habits.upsertLog(HabitLogEntity("h", 20_005, 1, 1))
        assertThat(habits.observeLogs(20_000, 20_003).first().map { it.date }).containsExactly(20_001L)
        habits.upsertLog(HabitLogEntity("h", 20_001, 3, 2))
        assertThat(habits.log("h", 20_001)?.count).isEqualTo(3)
        habits.delete("h")
        assertThat(habits.observeHabitLogs("h").first()).isEmpty()
    }

    @Test
    fun `personal events map round-trip`() = runTest {
        val event = ir.roozban.core.model.PersonalEvent(
            id = "e", title = "سالگرد ازدواج", kind = ir.roozban.core.model.EventKind.WEDDING, color = 4,
            calendar = ir.roozban.core.model.EventCalendar.GREGORIAN, month = 6, day = 12, year = 2015,
            remindDaysBefore = setOf(0, 7, 1), reminderTime = LocalTime.of(20, 15), notes = "رستوران",
            createdAt = Instant.ofEpochMilli(1), updatedAt = Instant.ofEpochMilli(2),
        )
        db.eventDao().upsert(event.toEntity())
        assertThat(db.eventDao().get("e")!!.toModel()).isEqualTo(event)
        assertThat(db.eventDao().observeAll().first()).hasSize(1)
        db.eventDao().delete("e")
        assertThat(db.eventDao().all()).isEmpty()
    }
}
