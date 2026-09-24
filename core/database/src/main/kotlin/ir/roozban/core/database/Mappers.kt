package ir.roozban.core.database

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

fun TaskEntity.toModel(): Task = Task(
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
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

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
