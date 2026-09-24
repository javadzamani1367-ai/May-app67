package ir.roozban.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dates are stored as epoch days, times as minutes of the day, date-times as *floating* local
 * epoch seconds (`LocalDateTime.toEpochSecond(UTC)`), instants as epoch millis.
 */
@Entity(
    tableName = "task",
    indices = [Index("due_date"), Index("completed_at"), Index("deleted_at")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    @ColumnInfo(name = "due_date") val dueDate: Long?,
    @ColumnInfo(name = "due_minute") val dueMinute: Int?,
    val important: Boolean,
    val urgent: Boolean,
    @ColumnInfo(name = "estimate_min") val estimateMinutes: Int?,
    val rrule: String?,
    @ColumnInfo(name = "rrule_start") val rruleStart: Long?,
    @ColumnInfo(name = "reminder_kind") val reminderKind: String?,
    @ColumnInfo(name = "reminder_offset") val reminderOffset: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "reminder",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trigger_at")],
)
data class ReminderEntity(
    @PrimaryKey @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "trigger_at") val triggerAt: Long,
    val kind: String,
    val state: String,
)

@Entity(
    tableName = "task_completion",
    primaryKeys = ["task_id", "occurrence"],
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE)],
)
data class CompletionEntity(
    @ColumnInfo(name = "task_id") val taskId: String,
    /** Epoch day of the completed occurrence. */
    val occurrence: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
)
