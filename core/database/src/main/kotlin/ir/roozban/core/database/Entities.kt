package ir.roozban.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Dates are stored as epoch days, times as minutes of the day, date-times as *floating* local
 * epoch seconds (`LocalDateTime.toEpochSecond(UTC)`), instants as epoch millis.
 */
@Entity(
    tableName = "task",
    indices = [
        Index("due_date"),
        Index("completed_at"),
        Index("deleted_at"),
        Index("project_id"),
        Index("parent_id"),
    ],
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
    /** No foreign key (added by migration to an existing table); cleared in code when a project is deleted. */
    @ColumnInfo(name = "project_id") val projectId: String? = null,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
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

@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val archived: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "label")
data class LabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "task_label",
    primaryKeys = ["task_id", "label_id"],
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(LabelEntity::class, ["id"], ["label_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("label_id")],
)
data class TaskLabelEntity(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "label_id") val labelId: String,
)

/** A task with its label ids (Room fills [labels] through the junction table). */
data class TaskWithLabels(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "task_id",
        entity = TaskLabelEntity::class,
        projection = ["label_id"],
    )
    val labelIds: List<String>,
)

data class SubtaskProgressRow(
    @ColumnInfo(name = "parent_id") val parentId: String,
    val total: Int,
    val done: Int,
)

data class ProjectCountRow(
    @ColumnInfo(name = "project_id") val projectId: String,
    val count: Int,
)

/** A finished or stopped focus period. No foreign key: the history outlives deleted tasks. */
@Entity(tableName = "focus_session", indices = [Index("ended_at")])
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "planned_min") val plannedMinutes: Int,
    @ColumnInfo(name = "focused_sec") val focusedSeconds: Long,
    val completed: Boolean,
)

@Entity(tableName = "time_entry", indices = [Index("task_id"), Index("end_at")])
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String?,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
    val source: String,
)

@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    /** See `HabitSchedule.encode`. */
    val schedule: String,
    val target: Int,
    @ColumnInfo(name = "reminder_minute") val reminderMinute: Int?,
    @ColumnInfo(name = "start_date") val startDate: Long,
    val archived: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "habit_log",
    primaryKeys = ["habit_id", "date"],
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date")],
)
data class HabitLogEntity(
    @ColumnInfo(name = "habit_id") val habitId: String,
    /** Epoch day. */
    val date: Long,
    val count: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class CompletionEventRow(
    @ColumnInfo(name = "task_id") val taskId: String,
    val title: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    val at: Long,
    @ColumnInfo(name = "estimate_min") val estimateMinutes: Int?,
)

data class TrackedRow(
    @Embedded val entry: TimeEntryEntity,
    @ColumnInfo(name = "task_title") val taskTitle: String?,
    @ColumnInfo(name = "task_project_id") val projectId: String?,
)

@Entity(tableName = "personal_event")
data class PersonalEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val color: Int,
    val calendar: String,
    val month: Int,
    val day: Int,
    val year: Int?,
    val yearly: Boolean,
    /** Comma-separated days before, e.g. "0,1,7". */
    @ColumnInfo(name = "remind_days") val remindDays: String,
    @ColumnInfo(name = "reminder_minute") val reminderMinute: Int,
    val notes: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "note", indices = [Index("updated_at")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "memory_fact", indices = [Index(value = ["key"], unique = true)])
data class MemoryFactEntity(
    @PrimaryKey val id: String,
    val key: String,
    val text: String,
    val source: String,
    val confidence: Float,
    val pinned: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
