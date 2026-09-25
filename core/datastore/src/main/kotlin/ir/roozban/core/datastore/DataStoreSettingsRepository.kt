package ir.roozban.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.FocusSettings
import ir.roozban.core.model.PlanningSettings
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
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
        val SHOW_GREGORIAN = booleanPreferencesKey("show_gregorian")
        val SHOW_HIJRI = booleanPreferencesKey("show_hijri")
        val HIJRI_OFFSET = intPreferencesKey("hijri_offset")
        val FOCUS_WORK = intPreferencesKey("focus_work_min")
        val FOCUS_SHORT = intPreferencesKey("focus_short_break_min")
        val FOCUS_LONG = intPreferencesKey("focus_long_break_min")
        val FOCUS_CYCLES = intPreferencesKey("focus_cycles")
        val FOCUS_AUTO_BREAK = booleanPreferencesKey("focus_auto_break")
        val FOCUS_AUTO_WORK = booleanPreferencesKey("focus_auto_work")
        val FOCUS_SILENCE = booleanPreferencesKey("focus_silence")
        /** Minute of day, or -1 for off. */
        val DAILY_REVIEW = intPreferencesKey("daily_review_minute")
        val WEEKLY_REVIEW = intPreferencesKey("weekly_review_minute")
        /** ISO day of week. */
        val WEEKLY_REVIEW_DAY = intPreferencesKey("weekly_review_day")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PALETTE = stringPreferencesKey("palette")
        /** "" = none. */
        val BACKGROUND = stringPreferencesKey("background")
        val BACKGROUND_VEIL = floatPreferencesKey("background_veil")
        val START_SCREEN = stringPreferencesKey("start_screen")
        val DATE_NOTIFICATION = booleanPreferencesKey("date_notification")
        /** "" = off. */
        val PRAYER_CITY = stringPreferencesKey("prayer_city")
        /** Absent = default sound. */
        val HABIT_SOUND = stringPreferencesKey("habit_sound")
        val EVENT_SOUND = stringPreferencesKey("event_sound")
        val PLAN_DAY_START = intPreferencesKey("plan_day_start_minute")
        val PLAN_DAY_END = intPreferencesKey("plan_day_end_minute")
        /** Minute of day, or -1 for off. */
        val PLAN_MORNING = intPreferencesKey("plan_morning_minute")
        val PLAN_AUTO = booleanPreferencesKey("plan_auto")
        val LEARNING = booleanPreferencesKey("learning_enabled")
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
                showGregorian = this[Keys.SHOW_GREGORIAN] ?: DEFAULTS.showGregorian,
                showHijri = this[Keys.SHOW_HIJRI] ?: DEFAULTS.showHijri,
                hijriOffset = this[Keys.HIJRI_OFFSET] ?: DEFAULTS.hijriOffset,
                focus = FocusSettings(
                    workMinutes = this[Keys.FOCUS_WORK] ?: DEFAULTS.focus.workMinutes,
                    shortBreakMinutes = this[Keys.FOCUS_SHORT] ?: DEFAULTS.focus.shortBreakMinutes,
                    longBreakMinutes = this[Keys.FOCUS_LONG] ?: DEFAULTS.focus.longBreakMinutes,
                    cyclesBeforeLongBreak = this[Keys.FOCUS_CYCLES] ?: DEFAULTS.focus.cyclesBeforeLongBreak,
                    autoStartBreaks = this[Keys.FOCUS_AUTO_BREAK] ?: DEFAULTS.focus.autoStartBreaks,
                    autoStartWork = this[Keys.FOCUS_AUTO_WORK] ?: DEFAULTS.focus.autoStartWork,
                    silence = this[Keys.FOCUS_SILENCE] ?: DEFAULTS.focus.silence,
                ),
                dailyReviewTime = minuteOrDefault(this[Keys.DAILY_REVIEW], DEFAULTS.dailyReviewTime),
                weeklyReviewTime = minuteOrDefault(this[Keys.WEEKLY_REVIEW], DEFAULTS.weeklyReviewTime),
                weeklyReviewDay = this[Keys.WEEKLY_REVIEW_DAY]?.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: DEFAULTS.weeklyReviewDay,
                themeMode = enumOr(this[Keys.THEME_MODE], DEFAULTS.themeMode),
                palette = enumOr(this[Keys.PALETTE], DEFAULTS.palette),
                background = this[Keys.BACKGROUND]?.let { it.ifEmpty { null } } ?: DEFAULTS.background,
                backgroundVeil = this[Keys.BACKGROUND_VEIL] ?: DEFAULTS.backgroundVeil,
                startScreen = enumOr(this[Keys.START_SCREEN], DEFAULTS.startScreen),
                dateNotification = this[Keys.DATE_NOTIFICATION] ?: DEFAULTS.dateNotification,
                prayerCity = this[Keys.PRAYER_CITY]?.let { it.ifEmpty { null } } ?: DEFAULTS.prayerCity,
                habitSound = this[Keys.HABIT_SOUND],
                eventSound = this[Keys.EVENT_SOUND],
                planning = PlanningSettings(
                    dayStart = minuteOrDefault(this[Keys.PLAN_DAY_START], null) ?: DEFAULTS.planning.dayStart,
                    dayEnd = minuteOrDefault(this[Keys.PLAN_DAY_END], null) ?: DEFAULTS.planning.dayEnd,
                    morningTime = minuteOrDefault(this[Keys.PLAN_MORNING], DEFAULTS.planning.morningTime),
                    autoPlan = this[Keys.PLAN_AUTO] ?: DEFAULTS.planning.autoPlan,
                    learningEnabled = this[Keys.LEARNING] ?: DEFAULTS.planning.learningEnabled,
                ),
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
            this[Keys.SHOW_GREGORIAN] = s.showGregorian
            this[Keys.SHOW_HIJRI] = s.showHijri
            this[Keys.HIJRI_OFFSET] = s.hijriOffset
            this[Keys.FOCUS_WORK] = s.focus.workMinutes
            this[Keys.FOCUS_SHORT] = s.focus.shortBreakMinutes
            this[Keys.FOCUS_LONG] = s.focus.longBreakMinutes
            this[Keys.FOCUS_CYCLES] = s.focus.cyclesBeforeLongBreak
            this[Keys.FOCUS_AUTO_BREAK] = s.focus.autoStartBreaks
            this[Keys.FOCUS_AUTO_WORK] = s.focus.autoStartWork
            this[Keys.FOCUS_SILENCE] = s.focus.silence
            this[Keys.DAILY_REVIEW] = s.dailyReviewTime.toMinute()
            this[Keys.WEEKLY_REVIEW] = s.weeklyReviewTime.toMinute()
            this[Keys.WEEKLY_REVIEW_DAY] = s.weeklyReviewDay.value
            this[Keys.THEME_MODE] = s.themeMode.name
            this[Keys.PALETTE] = s.palette.name
            this[Keys.BACKGROUND] = s.background.orEmpty()
            this[Keys.BACKGROUND_VEIL] = s.backgroundVeil
            this[Keys.START_SCREEN] = s.startScreen.name
            this[Keys.DATE_NOTIFICATION] = s.dateNotification
            this[Keys.PRAYER_CITY] = s.prayerCity.orEmpty()
            s.habitSound.let { if (it == null) remove(Keys.HABIT_SOUND) else this[Keys.HABIT_SOUND] = it }
            s.eventSound.let { if (it == null) remove(Keys.EVENT_SOUND) else this[Keys.EVENT_SOUND] = it }
            this[Keys.PLAN_DAY_START] = s.planning.dayStart.toMinute()
            this[Keys.PLAN_DAY_END] = s.planning.dayEnd.toMinute()
            this[Keys.PLAN_MORNING] = s.planning.morningTime.toMinute()
            this[Keys.PLAN_AUTO] = s.planning.autoPlan
            this[Keys.LEARNING] = s.planning.learningEnabled
        }

        inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

        fun minuteOrDefault(value: Int?, default: LocalTime?): LocalTime? = when (value) {
            null -> default
            -1 -> null
            else -> LocalTime.of(value / 60, value % 60)
        }

        fun LocalTime?.toMinute(): Int = this?.let { it.hour * 60 + it.minute } ?: -1
    }
}
