package ir.roozban.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
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
}
