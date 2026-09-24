package ir.roozban.feature.settings

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.domain.BackupService
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RestoreMode
import ir.roozban.core.domain.RestoreResult
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.testing.FakeAlarmScheduler
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
import java.time.Instant
import java.time.LocalTime

private object NoBackup : BackupService {
    override suspend fun createBackup(password: CharArray) = ByteArray(0)
    override suspend fun restore(file: ByteArray, password: CharArray, mode: RestoreMode) = RestoreResult.WrongPassword
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val clock = TestClock(jalali("1405-07-02").atTime(10, 0))
    private val settings = FakeSettingsRepository()
    private val tasks = FakeTaskRepository()
    private val reminders = FakeReminderRepository()
    private val scheduler = FakeAlarmScheduler()
    private val sync = ReminderSync(reminders, tasks, settings, scheduler, clock)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `hours are clamped to sensible ranges`() = runTest {
        val vm = SettingsViewModel(settings, sync, NoBackup)
        repeat(10) { vm.adjustHour(DayPart.EVENING, +1) }
        assertThat(settings.current().eveningHour).isEqualTo(19)
        repeat(10) { vm.adjustHour(DayPart.MORNING, -1) }
        assertThat(settings.current().morningHour).isEqualTo(5)
    }

    @Test
    fun `default reminder kind keeps its offset`() = runTest {
        val vm = SettingsViewModel(settings, sync, NoBackup)
        vm.setDefaultReminderOffset(15)
        vm.setDefaultReminderKind(ReminderKind.ALARM)
        assertThat(settings.current().defaultReminder).isEqualTo(ReminderSetting(ReminderKind.ALARM, 15))
        vm.setDefaultReminderKind(null)
        assertThat(settings.current().defaultReminder).isNull()
    }

    @Test
    fun `changing the all-day time reschedules existing tasks`() = runTest {
        val task = Task(
            id = "t",
            title = "قبض",
            due = TaskDue.AllDay(jalali("1405-07-04")),
            reminder = ReminderSetting(ReminderKind.NOTIFICATION),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        tasks.upsert(task)
        sync.sync(task)
        SettingsViewModel(settings, sync, NoBackup).setAllDayReminder(LocalTime.of(7, 30))
        assertThat(scheduler.scheduled["t"]?.second).isEqualTo(jalali("1405-07-04").atTime(7, 30))
    }

    @Test
    fun `hijri offset is limited to two days`() = runTest {
        val vm = SettingsViewModel(settings, sync, NoBackup)
        repeat(5) { vm.adjustHijriOffset(+1) }
        assertThat(settings.current().hijriOffset).isEqualTo(2)
        vm.setShowHijri(false)
        assertThat(settings.current().showHijri).isFalse()
    }
}
