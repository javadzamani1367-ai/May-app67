package ir.roozban.core.database

import ir.roozban.core.model.Label
import ir.roozban.core.model.Project
import ir.roozban.core.model.Reminder
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.ReminderState
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

fun LocalDateTime.toFloatingSeconds(): Long = toEpochSecond(ZoneOffset.UTC)

fun floatingSecondsToLocal(seconds: Long): LocalDateTime = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC)

fun TaskEntity.toModel(labelIds: Collection<String> = emptyList()): Task = Task(
    id = id,
    title = title,
    notes = notes,
    due = dueDate?.let { day ->
        val date = LocalDate.ofEpochDay(day)
        if (dueMinute == null) TaskDue.AllDay(date) else TaskDue.At(date, LocalTime.ofSecondOfDay(dueMinute * 60L))
    },
    important = important,
    urgent = urgent,
    estimateMinutes = estimateMinutes,
    recurrence = rrule,
    recurrenceStart = rruleStart?.let(LocalDate::ofEpochDay),
    reminder = reminderKind?.let { kind ->
        runCatching { ReminderKind.valueOf(kind) }.getOrNull()?.let { ReminderSetting(it, reminderOffset) }
    },
    projectId = projectId,
    parentId = parentId,
    labelIds = labelIds.toSet(),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun TaskWithLabels.toModel(): Task = task.toModel(labelIds)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    dueDate = due?.date?.toEpochDay(),
    dueMinute = (due as? TaskDue.At)?.time?.let { it.hour * 60 + it.minute },
    important = important,
    urgent = urgent,
    estimateMinutes = estimateMinutes,
    rrule = recurrence,
    rruleStart = recurrenceStart?.toEpochDay(),
    reminderKind = reminder?.kind?.name,
    reminderOffset = reminder?.offsetMinutes ?: 0,
    completedAt = completedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    projectId = projectId,
    parentId = parentId,
)

fun ReminderEntity.toModel(): Reminder = Reminder(
    taskId = taskId,
    triggerAt = floatingSecondsToLocal(triggerAt),
    kind = ReminderKind.valueOf(kind),
    state = ReminderState.valueOf(state),
)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    taskId = taskId,
    triggerAt = triggerAt.toFloatingSeconds(),
    kind = kind.name,
    state = state.name,
)

fun ProjectEntity.toModel() = Project(id, name, color, archived, sortOrder, Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(updatedAt))

fun Project.toEntity() = ProjectEntity(id, name, color, archived, sortOrder, createdAt.toEpochMilli(), updatedAt.toEpochMilli())

fun LabelEntity.toModel() = Label(id, name, color, Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(updatedAt))

fun Label.toEntity() = LabelEntity(id, name, color, createdAt.toEpochMilli(), updatedAt.toEpochMilli())
