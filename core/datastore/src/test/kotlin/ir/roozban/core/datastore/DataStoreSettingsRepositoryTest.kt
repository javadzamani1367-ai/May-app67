package ir.roozban.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.FocusPhase
import ir.roozban.core.model.FocusSettings
import ir.roozban.core.model.FocusState
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.StartScreen
import ir.roozban.core.model.ThemeMode
import ir.roozban.core.model.ThemePalette
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `defaults, update and persistence`() = runTest {
        val file = folder.newFile("settings.preferences_pb").also { it.delete() }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repo = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })

        assertThat(repo.current()).isEqualTo(UserSettings())

        repo.update {
            it.copy(
                eveningHour = 18,
                allDayReminderTime = null,
                defaultReminder = ReminderSetting(ReminderKind.ALARM, 15),
            )
        }
        val expected = UserSettings(eveningHour = 18, allDayReminderTime = null, defaultReminder = ReminderSetting(ReminderKind.ALARM, 15))
        assertThat(repo.current()).isEqualTo(expected)

        repo.update { it.copy(allDayReminderTime = LocalTime.of(8, 30), defaultReminder = null) }
        assertThat(repo.current().allDayReminderTime).isEqualTo(LocalTime.of(8, 30))
        assertThat(repo.current().defaultReminder).isNull()
        scope.cancel()
    }

    @Test
    fun `focus and review settings persist`() = runTest {
        val file = folder.newFile("s2.preferences_pb").also { it.delete() }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repo = DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        val focus = FocusSettings(workMinutes = 50, shortBreakMinutes = 10, autoStartBreaks = false, silence = false)
        repo.update {
            it.copy(focus = focus, dailyReviewTime = LocalTime.of(21, 30), weeklyReviewDay = DayOfWeek.THURSDAY, weeklyReviewTime = LocalTime.of(19, 0))
        }
        val s = repo.current()
        assertThat(s.focus).isEqualTo(focus)
        assertThat(s.dailyReviewTime).isEqualTo(LocalTime.of(21, 30))
        assertThat(s.weeklyReviewDay).isEqualTo(DayOfWeek.THURSDAY)
        repo.update { it.copy(dailyReviewTime = null) }
        assertThat(repo.current().dailyReviewTime).isNull()

        repo.update {
            it.copy(
                themeMode = ThemeMode.DARK, palette = ThemePalette.ROSE, background = "preset:dawn", backgroundVeil = 0.6f,
                startScreen = StartScreen.CALENDAR, dateNotification = false, prayerCity = "tehran",
            )
        }
        val a = repo.current()
        assertThat(a.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(a.palette).isEqualTo(ThemePalette.ROSE)
        assertThat(a.background).isEqualTo("preset:dawn")
        assertThat(a.backgroundVeil).isEqualTo(0.6f)
        assertThat(a.startScreen).isEqualTo(StartScreen.CALENDAR)
        assertThat(a.dateNotification).isFalse()
        assertThat(a.prayerCity).isEqualTo("tehran")
        repo.update { it.copy(background = null, prayerCity = null) }
        assertThat(repo.current().background).isNull()
        assertThat(repo.current().prayerCity).isNull()
        scope.cancel()
    }

    @Test
    fun `focus state round-trips`() = runTest {
        val file = folder.newFile("focus.preferences_pb").also { it.delete() }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = DataStoreFocusStateStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertThat(store.get()).isEqualTo(FocusState.Idle)
        val t = Instant.parse("2026-09-24T10:00:00Z")
        val states = listOf(
            FocusState.Running(FocusPhase.WORK, 2, "task", t, t.plusSeconds(60), t.plusSeconds(1500), 30_000),
            FocusState.Paused(FocusPhase.SHORT_BREAK, 3, null, t, 120_000, 180_000),
            FocusState.Ready(FocusPhase.LONG_BREAK, 4, "x"),
            FocusState.Idle,
        )
        for (state in states) {
            store.set(state)
            assertThat(store.get()).isEqualTo(state)
        }
        scope.cancel()
    }
}
