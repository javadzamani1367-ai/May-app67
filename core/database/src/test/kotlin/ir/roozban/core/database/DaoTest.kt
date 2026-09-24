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
        assertThat(tasks.observeOpen().first().map { it.id })
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
    fun completions() = runTest {
        tasks.upsert(task("a").toEntity())
        tasks.insertCompletion(CompletionEntity("a", day.toEpochDay(), 1))
        tasks.insertCompletion(CompletionEntity("a", day.plusDays(1).toEpochDay(), 2))
        tasks.deleteCompletion("a", day.toEpochDay())
        assertThat(tasks.completions("a").map { it.occurrence }).containsExactly(day.plusDays(1).toEpochDay())
    }
}
