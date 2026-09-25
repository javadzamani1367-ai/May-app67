package ir.roozban.core.data

import ir.roozban.core.backup.BackupCodec
import ir.roozban.core.backup.BackupCompletion
import ir.roozban.core.backup.BackupData
import ir.roozban.core.backup.BackupEvent
import ir.roozban.core.backup.BackupFocusSession
import ir.roozban.core.backup.BackupHabit
import ir.roozban.core.backup.BackupHabitLog
import ir.roozban.core.backup.BackupLabel
import ir.roozban.core.backup.BackupMerger
import ir.roozban.core.backup.BackupProject
import ir.roozban.core.backup.BackupSettings
import ir.roozban.core.backup.BackupTask
import ir.roozban.core.backup.BackupTimeEntry
import ir.roozban.core.backup.InvalidBackupException
import ir.roozban.core.backup.WrongPasswordException
import ir.roozban.core.database.BackupDao
import ir.roozban.core.database.CompletionEntity
import ir.roozban.core.database.FocusSessionEntity
import ir.roozban.core.database.HabitEntity
import ir.roozban.core.database.PersonalEventEntity
import ir.roozban.core.database.HabitLogEntity
import ir.roozban.core.database.LabelEntity
import ir.roozban.core.database.ProjectEntity
import ir.roozban.core.database.TaskEntity
import ir.roozban.core.database.TaskLabelEntity
import ir.roozban.core.database.TimeEntryEntity
import ir.roozban.core.domain.BackupService
import ir.roozban.core.domain.EventReminders
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RestoreMode
import ir.roozban.core.domain.RestoreResult
import ir.roozban.core.domain.RoutineReminders
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.FocusSettings
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.StartScreen
import ir.roozban.core.model.ThemeMode
import ir.roozban.core.model.ThemePalette
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

class RoomBackupService @Inject constructor(
    private val dao: BackupDao,
    private val settings: SettingsRepository,
    private val reminders: ReminderSync,
    private val routines: RoutineReminders,
    private val eventReminders: EventReminders,
    private val clock: Clock,
) : BackupService {

    private val codec = BackupCodec()

    override suspend fun createBackup(password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        codec.encode(snapshot(), password)
    }

    override suspend fun restore(file: ByteArray, password: CharArray, mode: RestoreMode): RestoreResult =
        withContext(Dispatchers.Default) {
            val incoming = try {
                codec.decode(file, password)
            } catch (e: WrongPasswordException) {
                return@withContext RestoreResult.WrongPassword
            } catch (e: InvalidBackupException) {
                return@withContext RestoreResult.Invalid(e.message.orEmpty())
            }
            val result = when (mode) {
                RestoreMode.REPLACE -> incoming
                RestoreMode.MERGE -> BackupMerger.merge(snapshot(), incoming)
            }
            write(result)
            if (mode == RestoreMode.REPLACE) result.settings?.let { s -> settings.update { s.toModel() } }
            reminders.syncAll()
            routines.syncAll()
            eventReminders.syncAll()
            RestoreResult.Success(tasks = result.tasks.count { it.parentId == null }, projects = result.projects.size)
        }

    private suspend fun snapshot(): BackupData {
        val labelsByTask = dao.taskLabels().groupBy({ it.taskId }, { it.labelId })
        return BackupData(
            createdAt = clock.millis(),
            tasks = dao.tasks().map { it.toBackup(labelsByTask[it.id].orEmpty()) },
            projects = dao.projects().map { BackupProject(it.id, it.name, it.color, it.archived, it.sortOrder, it.createdAt, it.updatedAt) },
            labels = dao.labels().map { BackupLabel(it.id, it.name, it.color, it.createdAt, it.updatedAt) },
            completions = dao.completions().map { BackupCompletion(it.taskId, it.occurrence, it.completedAt) },
            settings = settings.current().toBackup(),
            focusSessions = dao.focusSessions().map {
                BackupFocusSession(it.id, it.taskId, it.startedAt, it.endedAt, it.plannedMinutes, it.focusedSeconds, it.completed)
            },
            timeEntries = dao.timeEntries().map { BackupTimeEntry(it.id, it.taskId, it.startAt, it.endAt, it.source) },
            habits = dao.habits().map {
                BackupHabit(
                    it.id, it.name, it.color, it.schedule, it.target, it.reminderMinute, it.startDate,
                    it.archived, it.sortOrder, it.createdAt, it.updatedAt,
                )
            },
            habitLogs = dao.habitLogs().map { BackupHabitLog(it.habitId, it.date, it.count, it.updatedAt) },
            events = dao.events().map {
                BackupEvent(
                    it.id, it.title, it.kind, it.color, it.calendar, it.month, it.day, it.year, it.yearly,
                    it.remindDays, it.reminderMinute, it.notes, it.createdAt, it.updatedAt,
                )
            },
        )
    }

    private suspend fun write(data: BackupData) {
        dao.replaceAll(
            tasks = data.tasks.map { it.toEntity() },
            projects = data.projects.map { ProjectEntity(it.id, it.name, it.color, it.archived, it.sortOrder, it.createdAt, it.updatedAt) },
            labels = data.labels.map { LabelEntity(it.id, it.name, it.color, it.createdAt, it.updatedAt) },
            taskLabels = data.tasks.flatMap { t -> t.labelIds.map { TaskLabelEntity(t.id, it) } },
            completions = data.completions.map { CompletionEntity(it.taskId, it.occurrence, it.completedAt) },
            focusSessions = data.focusSessions.map {
                FocusSessionEntity(it.id, it.taskId, it.startedAt, it.endedAt, it.plannedMinutes, it.focusedSeconds, it.completed)
            },
            timeEntries = data.timeEntries.map { TimeEntryEntity(it.id, it.taskId, it.start, it.end, it.source) },
            habits = data.habits.map {
                HabitEntity(
                    it.id, it.name, it.color, it.schedule, it.target.coerceAtLeast(1), it.reminderMinute, it.startDate,
                    it.archived, it.sortOrder, it.createdAt, it.updatedAt,
                )
            },
            habitLogs = data.habitLogs.map { HabitLogEntity(it.habitId, it.date, it.count, it.updatedAt) },
            events = data.events.map {
                PersonalEventEntity(
                    it.id, it.title, it.kind, it.color, it.calendar, it.month.coerceIn(1, 12), it.day.coerceIn(1, 31), it.year, it.yearly,
                    it.remindDays, it.reminderMinute.coerceIn(0, 24 * 60 - 1), it.notes, it.createdAt, it.updatedAt,
                )
            },
        )
    }

    private fun TaskEntity.toBackup(labelIds: List<String>) = BackupTask(
        id = id, title = title, notes = notes, dueDate = dueDate, dueMinute = dueMinute,
        important = important, urgent = urgent, estimateMinutes = estimateMinutes,
        rrule = rrule, rruleStart = rruleStart, reminderKind = reminderKind, reminderOffset = reminderOffset,
        projectId = projectId, parentId = parentId, labelIds = labelIds,
        completedAt = completedAt, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    )

    private fun BackupTask.toEntity() = TaskEntity(
        id = id, title = title, notes = notes, dueDate = dueDate, dueMinute = dueMinute,
        important = important, urgent = urgent, estimateMinutes = estimateMinutes,
        rrule = rrule, rruleStart = rruleStart, reminderKind = reminderKind, reminderOffset = reminderOffset,
        completedAt = completedAt, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
        projectId = projectId, parentId = parentId,
    )

    private fun UserSettings.toBackup() = BackupSettings(
        morningHour = morningHour,
        noonHour = noonHour,
        afternoonHour = afternoonHour,
        eveningHour = eveningHour,
        nightHour = nightHour,
        allDayReminderMinute = allDayReminderTime?.let { it.hour * 60 + it.minute },
        defaultReminderKind = defaultReminder?.kind?.name,
        defaultReminderOffset = defaultReminder?.offsetMinutes ?: 0,
        dynamicColor = dynamicColor,
        showGregorian = showGregorian,
        showHijri = showHijri,
        hijriOffset = hijriOffset,
        focusWorkMinutes = focus.workMinutes,
        focusShortBreakMinutes = focus.shortBreakMinutes,
        focusLongBreakMinutes = focus.longBreakMinutes,
        focusCycles = focus.cyclesBeforeLongBreak,
        focusAutoStartBreaks = focus.autoStartBreaks,
        focusAutoStartWork = focus.autoStartWork,
        focusSilence = focus.silence,
        dailyReviewMinute = dailyReviewTime?.let { it.hour * 60 + it.minute },
        weeklyReviewDay = weeklyReviewDay.value,
        weeklyReviewMinute = weeklyReviewTime?.let { it.hour * 60 + it.minute },
        themeMode = themeMode.name,
        palette = palette.name,
        background = background?.takeIf { it.startsWith("preset:") },
        backgroundVeil = backgroundVeil,
        startScreen = startScreen.name,
        dateNotification = dateNotification,
        prayerCity = prayerCity,
    )

    private fun BackupSettings.toModel() = UserSettings(
        morningHour = morningHour,
        noonHour = noonHour,
        afternoonHour = afternoonHour,
        eveningHour = eveningHour,
        nightHour = nightHour,
        allDayReminderTime = allDayReminderMinute?.let { LocalTime.of(it / 60, it % 60) },
        defaultReminder = defaultReminderKind
            ?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() }
            ?.let { ReminderSetting(it, defaultReminderOffset) },
        dynamicColor = dynamicColor,
        showGregorian = showGregorian,
        showHijri = showHijri,
        hijriOffset = hijriOffset,
        focus = FocusSettings(
            workMinutes = focusWorkMinutes.coerceIn(FocusSettings.WORK_RANGE),
            shortBreakMinutes = focusShortBreakMinutes.coerceIn(FocusSettings.BREAK_RANGE),
            longBreakMinutes = focusLongBreakMinutes.coerceIn(FocusSettings.BREAK_RANGE),
            cyclesBeforeLongBreak = focusCycles.coerceIn(FocusSettings.CYCLES_RANGE),
            autoStartBreaks = focusAutoStartBreaks,
            autoStartWork = focusAutoStartWork,
            silence = focusSilence,
        ),
        dailyReviewTime = dailyReviewMinute?.let { LocalTime.of(it / 60, it % 60) },
        weeklyReviewDay = DayOfWeek.of(weeklyReviewDay.coerceIn(1, 7)),
        weeklyReviewTime = weeklyReviewMinute?.let { LocalTime.of(it / 60, it % 60) },
        themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: ThemeMode.LIGHT,
        palette = ThemePalette.entries.firstOrNull { it.name == palette } ?: ThemePalette.INDIGO,
        background = background,
        backgroundVeil = backgroundVeil.coerceIn(0.3f, 0.95f),
        startScreen = StartScreen.entries.firstOrNull { it.name == startScreen } ?: StartScreen.TODAY,
        dateNotification = dateNotification,
        prayerCity = prayerCity,
    )
}
