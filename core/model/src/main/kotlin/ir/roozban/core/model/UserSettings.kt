package ir.roozban.core.model

import java.time.DayOfWeek
import java.time.LocalTime

data class UserSettings(
    /** Default hours for «صبح»، «ظهر»، «بعدازظهر»، «عصر»، «شب». */
    val morningHour: Int = 9,
    val noonHour: Int = 12,
    val afternoonHour: Int = 15,
    val eveningHour: Int = 17,
    val nightHour: Int = 20,
    /** Reminder time for tasks without a time («آخر ماه»); null = no reminder. */
    val allDayReminderTime: LocalTime? = LocalTime.of(9, 0),
    /** Reminder applied to new tasks that have a due date. Null = none. */
    val defaultReminder: ReminderSetting? = ReminderSetting(ReminderKind.NOTIFICATION),
    val dynamicColor: Boolean = false,
    /** Calendar: show Gregorian / Hijri dates next to Jalali ones. */
    val showGregorian: Boolean = true,
    val showHijri: Boolean = true,
    /** Iran's official Hijri date can differ from Umm al-Qura by a day: -1, 0 or +1. */
    val hijriOffset: Int = 0,
    val focus: FocusSettings = FocusSettings(),
    /** Evening reminder for the daily review; null = off. */
    val dailyReviewTime: LocalTime? = null,
    /** Weekly review reminder; null time = off. */
    val weeklyReviewDay: DayOfWeek = DayOfWeek.FRIDAY,
    val weeklyReviewTime: LocalTime? = null,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val palette: ThemePalette = ThemePalette.INDIGO,
    /** Screen background: null = plain, `preset:<id>` or [BACKGROUND_IMAGE] (a picture from the gallery). */
    val background: String? = null,
    /** How strongly the background is veiled so text stays readable, 0.3..0.95. */
    val backgroundVeil: Float = 0.8f,
    val startScreen: StartScreen = StartScreen.TODAY,
    /** Ongoing notification with today's date and the day number in the status bar. */
    val dateNotification: Boolean = true,
    /** City for prayer times (see `IranCities`); null = not shown. */
    val prayerCity: String? = null,
    /** Notification sound for habit reminders: null = system default, "" = silent, else a sound URI. */
    val habitSound: String? = null,
    /** Notification sound for personal occasions (same encoding as [habitSound]). */
    val eventSound: String? = null,
    val planning: PlanningSettings = PlanningSettings(),
    val speech: SpeechSettings = SpeechSettings(),
) {
    companion object {
        const val BACKGROUND_IMAGE = "image"
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Accent color families; neutrals stay white/near-black so text is always black on white. */
enum class ThemePalette { INDIGO, OCEAN, VIOLET, ROSE, CORAL, AMBER, TEAL, SLATE, GREEN }

enum class StartScreen { TODAY, CALENDAR }

/** Day planner and learning (phase 6). */
data class PlanningSettings(
    /** Working window the planner fills. */
    val dayStart: LocalTime = LocalTime.of(8, 0),
    val dayEnd: LocalTime = LocalTime.of(22, 0),
    /** Morning rollover of overdue tasks and a proposed plan; null = off. */
    val morningTime: LocalTime? = null,
    /** Apply the morning plan without asking (it can still be undone). */
    val autoPlan: Boolean = false,
    /** Nightly statistics and inferred facts; off = nothing new is learned. */
    val learningEnabled: Boolean = true,
)

/** Reading aloud. */
data class SpeechSettings(
    /** `piper:<voice id>` or `system:<voice name>`; null = the first voice available. */
    val voice: String? = null,
    /** 0.6..1.6, 1 = normal. */
    val rate: Float = 1f,
    /** Read the assistant's replies aloud as they arrive. */
    val readReplies: Boolean = false,
) {
    companion object {
        const val MIN_RATE = 0.6f
        const val MAX_RATE = 1.6f
    }
}
