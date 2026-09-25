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
) {
    companion object {
        const val BACKGROUND_IMAGE = "image"
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Accent color families; neutrals stay white/near-black so text is always black on white. */
enum class ThemePalette { INDIGO, OCEAN, VIOLET, ROSE, CORAL, AMBER, TEAL, SLATE, GREEN }

enum class StartScreen { TODAY, CALENDAR }
