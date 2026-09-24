package ir.roozban.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
}
