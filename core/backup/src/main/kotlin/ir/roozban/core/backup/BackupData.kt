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
)
