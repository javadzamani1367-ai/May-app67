package ir.roozban.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.BackupService
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RestoreMode
import ir.roozban.core.domain.RestoreResult
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.designsystem.theme.Backgrounds
import ir.roozban.core.model.StartScreen
import ir.roozban.core.model.ThemeMode
import ir.roozban.core.model.ThemePalette
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

enum class DayPart { MORNING, NOON, AFTERNOON, EVENING, NIGHT }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val reminders: ReminderSync,
    private val backup: BackupService,
    private val images: BackgroundImageStore,
    private val clock: java.time.Clock,
) : ViewModel() {

    val settings: StateFlow<UserSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun update(resync: Boolean = false, transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            repository.update(transform)
            if (resync) reminders.syncAll()
        }
    }

    fun setDefaultReminderKind(kind: ReminderKind?) = update {
        it.copy(defaultReminder = kind?.let { k -> ReminderSetting(k, it.defaultReminder?.offsetMinutes ?: 0) })
    }

    fun setDefaultReminderOffset(minutes: Int) = update {
        it.copy(defaultReminder = it.defaultReminder?.copy(offsetMinutes = minutes))
    }

    /** Changing this re-plans the reminders of existing all-day tasks. */
    fun setAllDayReminder(time: LocalTime?) = update(resync = true) { it.copy(allDayReminderTime = time) }

    fun adjustHour(part: DayPart, delta: Int) = update { s ->
        fun clamp(h: Int, range: IntRange) = (h + delta).coerceIn(range)
        when (part) {
            DayPart.MORNING -> s.copy(morningHour = clamp(s.morningHour, 5..11))
            DayPart.NOON -> s.copy(noonHour = clamp(s.noonHour, 11..14))
            DayPart.AFTERNOON -> s.copy(afternoonHour = clamp(s.afternoonHour, 13..17))
            DayPart.EVENING -> s.copy(eveningHour = clamp(s.eveningHour, 15..19))
            DayPart.NIGHT -> s.copy(nightHour = clamp(s.nightHour, 19..23))
        }
    }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setPalette(palette: ThemePalette) = update { it.copy(palette = palette, dynamicColor = false) }

    /** [presetId] null = plain background. */
    fun setBackgroundPreset(presetId: String?) {
        viewModelScope.launch {
            repository.update { it.copy(background = presetId?.let { id -> Backgrounds.PRESET_PREFIX + id }) }
            images.clear()
        }
    }

    /** Uses a picture from the gallery. Returns false via [onResult] when it could not be read. */
    fun setBackgroundImage(uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = images.save(uri)
            // A new value each time so the screen reloads the picture.
            if (ok) repository.update { it.copy(background = UserSettings.BACKGROUND_IMAGE + ":" + clock.millis()) }
            onResult(ok)
        }
    }

    fun setBackgroundVeil(value: Float) = update { it.copy(backgroundVeil = value.coerceIn(0.3f, 0.95f)) }

    fun setStartScreen(screen: StartScreen) = update { it.copy(startScreen = screen) }

    fun setDateNotification(enabled: Boolean) = update { it.copy(dateNotification = enabled) }

    /** null = default sound, "" = silent, else a sound URI. */
    fun setHabitSound(sound: String?) = update { it.copy(habitSound = sound) }

    fun setEventSound(sound: String?) = update { it.copy(eventSound = sound) }

    fun setShowGregorian(show: Boolean) = update { it.copy(showGregorian = show) }

    fun setShowHijri(show: Boolean) = update { it.copy(showHijri = show) }

    fun adjustHijriOffset(delta: Int) = update { it.copy(hijriOffset = (it.hijriOffset + delta).coerceIn(-2, 2)) }

    /** Encrypts all data with [password]; the caller writes the bytes to the chosen file. */
    suspend fun createBackup(password: CharArray): ByteArray = backup.createBackup(password)

    suspend fun restore(file: ByteArray, password: CharArray, mode: RestoreMode): RestoreResult =
        backup.restore(file, password, mode)
}
