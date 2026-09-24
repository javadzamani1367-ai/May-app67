package ir.roozban.core.data

import ir.roozban.core.database.CompletionEntity
import ir.roozban.core.database.LabelDao
import ir.roozban.core.database.ProjectDao
import ir.roozban.core.database.ReminderDao
import ir.roozban.core.database.TaskDao
import ir.roozban.core.database.toEntity
import ir.roozban.core.database.toFloatingSeconds
import ir.roozban.core.database.toModel
import ir.roozban.core.domain.CompletionEvent
import ir.roozban.core.domain.LabelRepository
import ir.roozban.core.domain.ProjectRepository
import ir.roozban.core.domain.ReminderRepository
import ir.roozban.core.domain.SubtaskProgress
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.model.Label
import ir.roozban.core.model.Project
import ir.roozban.core.model.Reminder
import ir.roozban.core.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class RoomTaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val clock: Clock,
) : TaskRepository {
    override fun observeOpenTasks(): Flow<List<Task>> = dao.observeOpen().map { list -> list.map { it.toModel() } }

    override fun observeCompletedSince(since: Instant): Flow<List<Task>> =
        dao.observeCompletedSince(since.toEpochMilli()).map { list -> list.map { it.toModel() } }

    override fun observeCompletionEvents(from: Instant, until: Instant): Flow<List<CompletionEvent>> =
        dao.observeCompletionEvents(from.toEpochMilli(), until.toEpochMilli()).map { rows ->
            rows.map { CompletionEvent(it.taskId, it.title, it.projectId, Instant.ofEpochMilli(it.at), it.estimateMinutes) }
        }

    override fun observeSubtasks(parentId: String): Flow<List<Task>> =
        dao.observeSubtasks(parentId).map { list -> list.map { it.toModel() } }

    override fun observeProjectTasks(projectId: String): Flow<List<Task>> =
        dao.observeProjectTasks(projectId).map { list -> list.map { it.toModel() } }

    override fun observeSubtaskProgress(): Flow<Map<String, SubtaskProgress>> =
        dao.observeSubtaskProgress().map { rows -> rows.associate { it.parentId to SubtaskProgress(it.total, it.done) } }

    override fun observeTask(id: String): Flow<Task?> = dao.observe(id).map { it?.toModel() }

    override suspend fun get(id: String): Task? = dao.get(id)?.toModel()

    override suspend fun upsert(task: Task) = dao.upsert(task.toEntity(), task.labelIds)

    override suspend fun delete(id: String) = dao.softDelete(id, clock.millis())

    override suspend fun restore(id: String) = dao.restore(id)

    override suspend fun openTasksWithDue(): List<Task> = dao.openWithDue().map { it.toModel() }

    override suspend fun recordCompletion(taskId: String, occurrence: LocalDate, at: Instant) =
        dao.insertCompletion(CompletionEntity(taskId, occurrence.toEpochDay(), at.toEpochMilli()))

    override suspend fun removeCompletion(taskId: String, occurrence: LocalDate) =
        dao.deleteCompletion(taskId, occurrence.toEpochDay())

    /** Deleted tasks stay restorable for a while, then are removed for good. */
    suspend fun purgeDeletedOlderThanDays(days: Long) {
        dao.purgeDeleted(clock.millis() - days * 24 * 60 * 60 * 1000)
    }
}

class RoomReminderRepository @Inject constructor(
    private val dao: ReminderDao,
) : ReminderRepository {
    override suspend fun get(taskId: String): Reminder? = dao.get(taskId)?.toModel()

    override suspend fun set(reminder: Reminder) = dao.upsert(reminder.toEntity())

    override suspend fun clear(taskId: String) = dao.delete(taskId)

    override suspend fun pendingUntil(until: LocalDateTime): List<Reminder> =
        dao.pendingUntil(until.toFloatingSeconds()).map { it.toModel() }

    override suspend fun markFired(taskId: String) = dao.markFired(taskId)
}

class RoomProjectRepository @Inject constructor(
    private val dao: ProjectDao,
    private val taskDao: TaskDao,
) : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeOpenCounts(): Flow<Map<String, Int>> =
        dao.observeOpenCounts().map { rows -> rows.associate { it.projectId to it.count } }

    override suspend fun get(id: String): Project? = dao.get(id)?.toModel()

    override suspend fun all(): List<Project> = dao.all().map { it.toModel() }

    override suspend fun upsert(project: Project) = dao.upsert(project.toEntity())

    override suspend fun delete(id: String) {
        taskDao.detachProject(id)
        dao.delete(id)
    }
}

class RoomLabelRepository @Inject constructor(
    private val dao: LabelDao,
) : LabelRepository {
    override fun observeLabels(): Flow<List<Label>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun all(): List<Label> = dao.all().map { it.toModel() }

    override suspend fun upsert(label: Label) = dao.upsert(label.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}
