package ir.roozban.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import ir.roozban.core.database.RoozbanDatabase
import ir.roozban.core.domain.AddTaskUseCase
import ir.roozban.core.domain.QuickAddParser
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RestoreMode
import ir.roozban.core.domain.RestoreResult
import ir.roozban.core.domain.TagResolver
import ir.roozban.core.testing.FakeAlarmScheduler
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
    private val backup = RoomBackupService(db.backupDao(), settings, sync, clock)
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
}
