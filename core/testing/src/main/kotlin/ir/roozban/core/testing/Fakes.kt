package ir.roozban.core.testing

import ir.roozban.core.domain.AlarmScheduler
import ir.roozban.core.domain.LabelRepository
import ir.roozban.core.domain.ProjectRepository
import ir.roozban.core.domain.SubtaskProgress
import ir.roozban.core.domain.ReminderRepository
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.model.Label
import ir.roozban.core.model.Project
import ir.roozban.core.model.Reminder
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderState
import ir.roozban.core.model.Task
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

/** A mutable clock for tests. */
class TestClock(var now: LocalDateTime) : Clock() {
    override fun getZone(): ZoneId = TEHRAN
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now.atZone(TEHRAN).toInstant()
}

fun jalali(date: String): LocalDate = JalaliDate.parse(date).toLocalDate()

class FakeTaskRepository : TaskRepository {
    val tasks = MutableStateFlow<Map<String, Task>>(emptyMap())
    val deleted = mutableSetOf<String>()
    val completions = mutableListOf<Pair<String, LocalDate>>()

    override fun observeOpenTasks(): Flow<List<Task>> =
        tasks.map { m -> m.values.filter { !it.isCompleted && it.id !in deleted && it.parentId == null } }

    override fun observeSubtasks(parentId: String): Flow<List<Task>> =
        tasks.map { m -> m.values.filter { it.parentId == parentId && it.id !in deleted }.sortedBy { it.createdAt } }

    override fun observeProjectTasks(projectId: String): Flow<List<Task>> =
        tasks.map { m -> m.values.filter { it.projectId == projectId && !it.isCompleted && it.id !in deleted && it.parentId == null } }

    override fun observeSubtaskProgress(): Flow<Map<String, SubtaskProgress>> = tasks.map { m ->
        m.values.filter { it.parentId != null && it.id !in deleted }.groupBy { it.parentId!! }
            .mapValues { (_, subs) -> SubtaskProgress(subs.size, subs.count { it.isCompleted }) }
    }

    override fun observeCompletedSince(since: Instant): Flow<List<Task>> =
        tasks.map { m -> m.values.filter { (it.completedAt ?: Instant.MIN) >= since } }

    override fun observeTask(id: String): Flow<Task?> = tasks.map { it[id] }

    override suspend fun get(id: String): Task? = tasks.value[id]

    override suspend fun upsert(task: Task) {
        tasks.value = tasks.value + (task.id to task)
    }

    override suspend fun delete(id: String) {
        deleted += id
    }

    override suspend fun restore(id: String) {
        deleted -= id
    }

    override suspend fun openTasksWithDue(): List<Task> =
        tasks.value.values.filter { !it.isCompleted && it.id !in deleted && it.due != null }

    override suspend fun recordCompletion(taskId: String, occurrence: LocalDate, at: Instant) {
        completions += taskId to occurrence
    }

    override suspend fun removeCompletion(taskId: String, occurrence: LocalDate) {
        completions -= taskId to occurrence
    }
}

class FakeProjectRepository : ProjectRepository {
    val projects = MutableStateFlow<Map<String, Project>>(emptyMap())
    var tasks: FakeTaskRepository? = null

    override fun observeProjects(): Flow<List<Project>> = projects.map { m -> m.values.sortedWith(compareBy({ it.archived }, { it.sortOrder })) }
    override fun observeOpenCounts(): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override suspend fun get(id: String) = projects.value[id]
    override suspend fun all() = projects.value.values.toList()
    override suspend fun upsert(project: Project) {
        projects.value = projects.value + (project.id to project)
    }
    override suspend fun delete(id: String) {
        projects.value = projects.value - id
    }
}

class FakeLabelRepository : LabelRepository {
    val labels = MutableStateFlow<Map<String, Label>>(emptyMap())
    override fun observeLabels(): Flow<List<Label>> = labels.map { it.values.sortedBy { l -> l.name } }
    override suspend fun all() = labels.value.values.toList()
    override suspend fun upsert(label: Label) {
        labels.value = labels.value + (label.id to label)
    }
    override suspend fun delete(id: String) {
        labels.value = labels.value - id
    }
}

class FakeReminderRepository : ReminderRepository {
    val reminders = mutableMapOf<String, Reminder>()
    override suspend fun get(taskId: String) = reminders[taskId]
    override suspend fun set(reminder: Reminder) {
        reminders[reminder.taskId] = reminder
    }
    override suspend fun clear(taskId: String) {
        reminders -= taskId
    }
    override suspend fun pendingUntil(until: LocalDateTime) =
        reminders.values.filter { it.state == ReminderState.PENDING && !it.triggerAt.isAfter(until) }.sortedBy { it.triggerAt }
    override suspend fun markFired(taskId: String) {
        reminders[taskId]?.let { reminders[taskId] = it.copy(state = ReminderState.FIRED) }
    }
}

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        state.value = transform(state.value)
    }
}

class FakeAlarmScheduler : AlarmScheduler {
    /** taskId → (kind, trigger as local date-time in Tehran). */
    val scheduled = mutableMapOf<String, Pair<ReminderKind, LocalDateTime>>()
    override fun schedule(taskId: String, kind: ReminderKind, triggerAtEpochMillis: Long) {
        scheduled[taskId] = kind to LocalDateTime.ofInstant(Instant.ofEpochMilli(triggerAtEpochMillis), TEHRAN)
    }
    override fun cancel(taskId: String) {
        scheduled -= taskId
    }
}

