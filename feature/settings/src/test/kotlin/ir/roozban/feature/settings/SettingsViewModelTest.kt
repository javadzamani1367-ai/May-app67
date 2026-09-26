package ir.roozban.feature.settings

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.domain.BackupService
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RestoreMode
import ir.roozban.core.domain.RestoreResult
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.StartScreen
import ir.roozban.core.model.ThemeMode
import ir.roozban.core.model.ThemePalette
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

private object NoImages : BackgroundImageStore {
    var saved = 0
    override suspend fun save(uri: android.net.Uri): Boolean {
        saved++
        return true
    }
    override suspend fun clear() = Unit
}

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
        val vm = SettingsViewModel(settings, sync, NoBackup, NoImages, NoDownloads, clock)
        repeat(10) { vm.adjustHour(DayPart.EVENING, +1) }
        assertThat(settings.current().eveningHour).isEqualTo(19)
        repeat(10) { vm.adjustHour(DayPart.MORNING, -1) }
        assertThat(settings.current().morningHour).isEqualTo(5)
    }

    @Test
    fun `default reminder kind keeps its offset`() = runTest {
        val vm = SettingsViewModel(settings, sync, NoBackup, NoImages, NoDownloads, clock)
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
        SettingsViewModel(settings, sync, NoBackup, NoImages, NoDownloads, clock).setAllDayReminder(LocalTime.of(7, 30))
        assertThat(scheduler.scheduled["t"]?.second).isEqualTo(jalali("1405-07-04").atTime(7, 30))
    }

    @Test
    fun `hijri offset is limited to two days`() = runTest {
        val vm = SettingsViewModel(settings, sync, NoBackup, NoImages, NoDownloads, clock)
        repeat(5) { vm.adjustHijriOffset(+1) }
        assertThat(settings.current().hijriOffset).isEqualTo(2)
        vm.setShowHijri(false)
        assertThat(settings.current().showHijri).isFalse()
    }

    @Test
    fun `appearance choices are stored`() = runTest {
        val vm = SettingsViewModel(settings, sync, NoBackup, NoImages, NoDownloads, clock)
        vm.setThemeMode(ThemeMode.DARK)
        vm.setPalette(ThemePalette.ROSE)
        vm.setBackgroundPreset("dawn")
        vm.setBackgroundVeil(2f)
        vm.setStartScreen(StartScreen.CALENDAR)
        vm.setDateNotification(false)
        val s = settings.current()
        assertThat(s.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(s.palette).isEqualTo(ThemePalette.ROSE)
        assertThat(s.background).isEqualTo("preset:dawn")
        assertThat(s.backgroundVeil).isEqualTo(0.95f)
        assertThat(s.startScreen).isEqualTo(StartScreen.CALENDAR)
        assertThat(s.dateNotification).isFalse()
        vm.setBackgroundPreset(null)
        assertThat(settings.current().background).isNull()
        vm.setHabitSound("content://media/internal/audio/media/7")
        vm.setEventSound("")
        assertThat(settings.current().habitSound).isEqualTo("content://media/internal/audio/media/7")
        assertThat(settings.current().eventSound).isEqualTo("")
    }
}

private object NoDownloads : ir.roozban.core.domain.DownloadSettings {
    override val wifiOnly = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun setWifiOnly(value: Boolean) {
        wifiOnly.value = value
    }
}
