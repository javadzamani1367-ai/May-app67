package ir.roozban.core.model

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
)
