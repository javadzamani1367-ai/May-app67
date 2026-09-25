package ir.roozban.core.database

import ir.roozban.core.model.EventCalendar
import ir.roozban.core.model.EventKind
import ir.roozban.core.model.FactSource
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitLog
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.model.Label
import ir.roozban.core.model.MemoryFact
import ir.roozban.core.model.PersonalEvent
import ir.roozban.core.model.Project
import ir.roozban.core.model.Reminder
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.ReminderState
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.TimeEntry
import ir.roozban.core.model.TimeSource
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

fun FocusSessionEntity.toModel() = FocusSession(
    id, taskId, Instant.ofEpochMilli(startedAt), Instant.ofEpochMilli(endedAt), plannedMinutes, focusedSeconds, completed,
)

fun FocusSession.toEntity() = FocusSessionEntity(
    id, taskId, startedAt.toEpochMilli(), endedAt.toEpochMilli(), plannedMinutes, focusedSeconds, completed,
)

fun TimeEntryEntity.toModel() = TimeEntry(
    id, taskId, Instant.ofEpochMilli(startAt), Instant.ofEpochMilli(endAt),
    runCatching { TimeSource.valueOf(source) }.getOrDefault(TimeSource.MANUAL),
)

fun TimeEntry.toEntity() = TimeEntryEntity(id, taskId, start.toEpochMilli(), end.toEpochMilli(), source.name)

fun HabitEntity.toModel() = Habit(
    id = id,
    name = name,
    color = color,
    schedule = HabitSchedule.decode(schedule),
    targetPerDay = target,
    reminderTime = reminderMinute?.let { LocalTime.of(it / 60, it % 60) },
    startDate = LocalDate.ofEpochDay(startDate),
    archived = archived,
    sortOrder = sortOrder,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Habit.toEntity() = HabitEntity(
    id = id,
    name = name,
    color = color,
    schedule = schedule.encode(),
    target = targetPerDay,
    reminderMinute = reminderTime?.let { it.hour * 60 + it.minute },
    startDate = startDate.toEpochDay(),
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun HabitLogEntity.toModel() = HabitLog(habitId, LocalDate.ofEpochDay(date), count, Instant.ofEpochMilli(updatedAt))

fun HabitLog.toEntity() = HabitLogEntity(habitId, date.toEpochDay(), count, updatedAt.toEpochMilli())

fun PersonalEventEntity.toModel() = PersonalEvent(
    id = id,
    title = title,
    kind = EventKind.entries.firstOrNull { it.name == kind } ?: EventKind.OTHER,
    color = color,
    calendar = EventCalendar.entries.firstOrNull { it.name == calendar } ?: EventCalendar.JALALI,
    month = month,
    day = day,
    year = year,
    yearly = yearly,
    remindDaysBefore = remindDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
    reminderTime = LocalTime.of(reminderMinute / 60, reminderMinute % 60),
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun PersonalEvent.toEntity() = PersonalEventEntity(
    id = id,
    title = title,
    kind = kind.name,
    color = color,
    calendar = calendar.name,
    month = month,
    day = day,
    year = year,
    yearly = yearly,
    remindDays = remindDaysBefore.sorted().joinToString(","),
    reminderMinute = reminderTime.hour * 60 + reminderTime.minute,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun MemoryFactEntity.toModel() = MemoryFact(
    id = id,
    key = key,
    text = text,
    source = FactSource.entries.firstOrNull { it.name == source } ?: FactSource.USER,
    confidence = confidence,
    pinned = pinned,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun MemoryFact.toEntity() = MemoryFactEntity(
    id = id,
    key = key,
    text = text,
    source = source.name,
    confidence = confidence,
    pinned = pinned,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)
