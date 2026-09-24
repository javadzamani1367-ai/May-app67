package ir.roozban.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/** [UserSettings] persisted in Preferences DataStore. Missing keys fall back to the defaults. */
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<UserSettings> = store.data.map { it.toSettings() }

    override suspend fun current(): UserSettings = settings.first()

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private object Keys {
        val MORNING = intPreferencesKey("morning_hour")
        val NOON = intPreferencesKey("noon_hour")
        val AFTERNOON = intPreferencesKey("afternoon_hour")
        val EVENING = intPreferencesKey("evening_hour")
        val NIGHT = intPreferencesKey("night_hour")
        /** Minute of day, or -1 for none. */
        val ALL_DAY_REMINDER = intPreferencesKey("all_day_reminder_minute")
        /** ReminderKind name, or "NONE". */
        val DEFAULT_REMINDER_KIND = stringPreferencesKey("default_reminder_kind")
        val DEFAULT_REMINDER_OFFSET = intPreferencesKey("default_reminder_offset")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    private companion object {
        const val NONE = "NONE"
        val DEFAULTS = UserSettings()

        fun Preferences.toSettings(): UserSettings {
            val allDay = this[Keys.ALL_DAY_REMINDER]
            val kindName = this[Keys.DEFAULT_REMINDER_KIND]
            return UserSettings(
                morningHour = this[Keys.MORNING] ?: DEFAULTS.morningHour,
                noonHour = this[Keys.NOON] ?: DEFAULTS.noonHour,
                afternoonHour = this[Keys.AFTERNOON] ?: DEFAULTS.afternoonHour,
                eveningHour = this[Keys.EVENING] ?: DEFAULTS.eveningHour,
                nightHour = this[Keys.NIGHT] ?: DEFAULTS.nightHour,
                allDayReminderTime = when (allDay) {
                    null -> DEFAULTS.allDayReminderTime
                    -1 -> null
                    else -> LocalTime.of(allDay / 60, allDay % 60)
                },
                defaultReminder = when (kindName) {
                    null -> DEFAULTS.defaultReminder
                    NONE -> null
                    else -> runCatching { ReminderKind.valueOf(kindName) }.getOrNull()
                        ?.let { ReminderSetting(it, this[Keys.DEFAULT_REMINDER_OFFSET] ?: 0) }
                },
                dynamicColor = this[Keys.DYNAMIC_COLOR] ?: DEFAULTS.dynamicColor,
            )
        }

        fun MutablePreferences.write(s: UserSettings) {
            this[Keys.MORNING] = s.morningHour
            this[Keys.NOON] = s.noonHour
            this[Keys.AFTERNOON] = s.afternoonHour
            this[Keys.EVENING] = s.eveningHour
            this[Keys.NIGHT] = s.nightHour
            this[Keys.ALL_DAY_REMINDER] = s.allDayReminderTime?.let { it.hour * 60 + it.minute } ?: -1
            this[Keys.DEFAULT_REMINDER_KIND] = s.defaultReminder?.kind?.name ?: NONE
            this[Keys.DEFAULT_REMINDER_OFFSET] = s.defaultReminder?.offsetMinutes ?: 0
            this[Keys.DYNAMIC_COLOR] = s.dynamicColor
        }
    }
}
