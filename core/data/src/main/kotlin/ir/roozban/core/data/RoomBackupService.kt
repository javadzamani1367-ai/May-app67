package ir.roozban.core.data

import ir.roozban.core.backup.BackupCodec
import ir.roozban.core.backup.BackupCompletion
import ir.roozban.core.backup.BackupData
import ir.roozban.core.backup.BackupLabel
import ir.roozban.core.backup.BackupMerger
import ir.roozban.core.backup.BackupProject
import ir.roozban.core.backup.BackupSettings
import ir.roozban.core.backup.BackupTask
import ir.roozban.core.backup.InvalidBackupException
import ir.roozban.core.backup.WrongPasswordException
import ir.roozban.core.database.BackupDao
import ir.roozban.core.database.CompletionEntity
import ir.roozban.core.database.LabelEntity
import ir.roozban.core.database.ProjectEntity
import ir.roozban.core.database.TaskEntity
import ir.roozban.core.database.TaskLabelEntity
import ir.roozban.core.domain.BackupService
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RestoreMode
import ir.roozban.core.domain.RestoreResult
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

class RoomBackupService @Inject constructor(
    private val dao: BackupDao,
    private val settings: SettingsRepository,
    private val reminders: ReminderSync,
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
        )
    }

    private suspend fun write(data: BackupData) {
        dao.replaceAll(
            tasks = data.tasks.map { it.toEntity() },
            projects = data.projects.map { ProjectEntity(it.id, it.name, it.color, it.archived, it.sortOrder, it.createdAt, it.updatedAt) },
            labels = data.labels.map { LabelEntity(it.id, it.name, it.color, it.createdAt, it.updatedAt) },
            taskLabels = data.tasks.flatMap { t -> t.labelIds.map { TaskLabelEntity(t.id, it) } },
            completions = data.completions.map { CompletionEntity(it.taskId, it.occurrence, it.completedAt) },
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
    )
}
