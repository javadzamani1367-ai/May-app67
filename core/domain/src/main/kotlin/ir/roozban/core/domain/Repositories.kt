package ir.roozban.core.domain

import ir.roozban.core.model.Reminder
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.Task
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

interface TaskRepository {
    /** Open (not completed, not deleted) tasks, ordered by due date then creation. */
    fun observeOpenTasks(): Flow<List<Task>>

    fun observeCompletedSince(since: Instant): Flow<List<Task>>

    fun observeTask(id: String): Flow<Task?>

    suspend fun get(id: String): Task?

    suspend fun upsert(task: Task)

    /** Soft delete; see [restore]. */
    suspend fun delete(id: String)

    suspend fun restore(id: String)

    suspend fun openTasksWithDue(): List<Task>

    suspend fun recordCompletion(taskId: String, occurrence: LocalDate, at: Instant)

    suspend fun removeCompletion(taskId: String, occurrence: LocalDate)
}

interface ReminderRepository {
    suspend fun get(taskId: String): Reminder?

    suspend fun set(reminder: Reminder)

    suspend fun clear(taskId: String)

    /** Pending reminders due at or before [until], oldest first. */
    suspend fun pendingUntil(until: LocalDateTime): List<Reminder>

    suspend fun markFired(taskId: String)
}

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun current(): UserSettings

    suspend fun update(transform: (UserSettings) -> UserSettings)
}

/** Platform alarm scheduling (AlarmManager on Android). Idempotent per task. */
interface AlarmScheduler {
    fun schedule(taskId: String, kind: ReminderKind, triggerAtEpochMillis: Long)

    fun cancel(taskId: String)
}

/** Reverts a completed action. */
fun interface Undo {
    suspend operator fun invoke()
}
