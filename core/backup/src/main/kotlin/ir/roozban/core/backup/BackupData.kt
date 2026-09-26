package ir.roozban.core.backup

import kotlinx.serialization.Serializable

/**
 * Everything a backup contains. Plain DTOs, independent of the database schema, so old backups
 * stay readable after migrations. Dates are epoch days, times minutes of day, instants epoch millis.
 */
@Serializable
data class BackupData(
    val formatVersion: Int = FORMAT_VERSION,
    val createdAt: Long,
    val appVersion: String = "",
    val tasks: List<BackupTask> = emptyList(),
    val projects: List<BackupProject> = emptyList(),
    val labels: List<BackupLabel> = emptyList(),
    val completions: List<BackupCompletion> = emptyList(),
    val settings: BackupSettings? = null,
    val focusSessions: List<BackupFocusSession> = emptyList(),
    val timeEntries: List<BackupTimeEntry> = emptyList(),
    val habits: List<BackupHabit> = emptyList(),
    val habitLogs: List<BackupHabitLog> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
    /** Null in backups made before personal memory existed: restoring keeps what the phone learned. */
    val memory: List<BackupFact>? = null,
    /** Null in backups made before the notes tool: restoring keeps the phone's notes. */
    val notes: List<BackupNote>? = null,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class BackupTask(
    val id: String,
    val title: String,
    val notes: String = "",
    val dueDate: Long? = null,
    val dueMinute: Int? = null,
    val important: Boolean = false,
    val urgent: Boolean = false,
    val estimateMinutes: Int? = null,
    val rrule: String? = null,
    val rruleStart: Long? = null,
    val reminderKind: String? = null,
    val reminderOffset: Int = 0,
    val projectId: String? = null,
    val parentId: String? = null,
    val labelIds: List<String> = emptyList(),
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class BackupProject(
    val id: String,
    val name: String,
    val color: Int = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupLabel(
    val id: String,
    val name: String,
    val color: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupCompletion(val taskId: String, val occurrence: Long, val completedAt: Long)

@Serializable
data class BackupSettings(
    val morningHour: Int,
    val noonHour: Int,
    val afternoonHour: Int,
    val eveningHour: Int,
    val nightHour: Int,
    val allDayReminderMinute: Int? = null,
    val defaultReminderKind: String? = null,
    val defaultReminderOffset: Int = 0,
    val dynamicColor: Boolean = false,
    val showGregorian: Boolean = true,
    val showHijri: Boolean = true,
    val hijriOffset: Int = 0,
    val focusWorkMinutes: Int = 25,
    val focusShortBreakMinutes: Int = 5,
    val focusLongBreakMinutes: Int = 15,
    val focusCycles: Int = 4,
    val focusAutoStartBreaks: Boolean = true,
    val focusAutoStartWork: Boolean = false,
    val focusSilence: Boolean = true,
    val dailyReviewMinute: Int? = null,
    val weeklyReviewDay: Int = 5,
    val weeklyReviewMinute: Int? = null,
    val themeMode: String? = null,
    val palette: String? = null,
    /** Only presets travel in backups; a gallery picture stays on the device. */
    val background: String? = null,
    val backgroundVeil: Float = 0.8f,
    val startScreen: String? = null,
    val dateNotification: Boolean = true,
    val prayerCity: String? = null,
    val planDayStartMinute: Int = 8 * 60,
    val planDayEndMinute: Int = 22 * 60,
    val planMorningMinute: Int? = null,
    val planAuto: Boolean = false,
    val learningEnabled: Boolean = true,
)

@Serializable
data class BackupFocusSession(
    val id: String,
    val taskId: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val focusedSeconds: Long,
    val completed: Boolean,
)

@Serializable
data class BackupTimeEntry(val id: String, val taskId: String? = null, val start: Long, val end: Long, val source: String)

@Serializable
data class BackupHabit(
    val id: String,
    val name: String,
    val color: Int = 0,
    val schedule: String = "D",
    val target: Int = 1,
    val reminderMinute: Int? = null,
    val startDate: Long,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupHabitLog(val habitId: String, val date: Long, val count: Int, val updatedAt: Long)

@Serializable
data class BackupEvent(
    val id: String,
    val title: String,
    val kind: String = "OTHER",
    val color: Int = 0,
    val calendar: String = "JALALI",
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val yearly: Boolean = true,
    val remindDays: String = "0,1",
    val reminderMinute: Int = 540,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupNote(val id: String, val title: String = "", val body: String = "", val createdAt: Long, val updatedAt: Long)

@Serializable
data class BackupFact(
    val id: String,
    val key: String,
    val text: String,
    val source: String = "USER",
    val confidence: Float = 1f,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
