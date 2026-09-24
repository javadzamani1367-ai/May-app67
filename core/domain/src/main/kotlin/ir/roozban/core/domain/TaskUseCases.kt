package ir.roozban.core.domain

import ir.roozban.core.model.Label
import ir.roozban.core.model.Project
import ir.roozban.core.model.Task
import ir.roozban.core.model.normalizeName
import ir.roozban.core.recurrence.RecurrenceSpec
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val reminders: ReminderSync,
    private val tags: TagResolver,
    private val clock: Clock,
) {
    /**
     * Creates a task from quick-add input; `#project` and `@label` names are matched to existing
     * ones (ignoring ی/ک and spacing differences) or created. [defaultProjectId] applies when the
     * text names no project, e.g. when adding from inside a project. Returns null without a title.
     */
    suspend operator fun invoke(input: QuickAddResult, defaultProjectId: String? = null): Task? {
        if (input.title.isBlank()) return null
        val now = Instant.now(clock)
        val projectId = input.projectName?.let { tags.projectId(it) } ?: defaultProjectId
        val labelIds = input.labelNames.map { tags.labelId(it) }.toSet()
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = input.title,
            due = input.due,
            important = input.important,
            urgent = input.urgent,
            estimateMinutes = input.estimate?.toMinutes()?.toInt(),
            recurrence = input.recurrence?.toRRule(),
            recurrenceStart = input.recurrence?.let { input.due?.date },
            reminder = if (input.due != null) settings.current().defaultReminder else null,
            projectId = projectId,
            labelIds = labelIds,
            createdAt = now,
            updatedAt = now,
        )
        tasks.upsert(task)
        reminders.sync(task)
        return task
    }
}

/** Finds projects and labels by name, creating them when missing. */
class TagResolver @Inject constructor(
    private val projects: ProjectRepository,
    private val labels: LabelRepository,
    private val clock: Clock,
) {
    suspend fun projectId(name: String): String {
        val key = normalizeName(name)
        projects.all().firstOrNull { normalizeName(it.name) == key }?.let { return it.id }
        val now = Instant.now(clock)
        val project = Project(UUID.randomUUID().toString(), name.trim(), color = Math.floorMod(key.hashCode(), PALETTE_SIZE), createdAt = now, updatedAt = now)
        projects.upsert(project)
        return project.id
    }

    suspend fun labelId(name: String): String {
        val key = normalizeName(name)
        labels.all().firstOrNull { normalizeName(it.name) == key }?.let { return it.id }
        val now = Instant.now(clock)
        val label = Label(UUID.randomUUID().toString(), name.trim(), color = Math.floorMod(key.hashCode(), PALETTE_SIZE), createdAt = now, updatedAt = now)
        labels.upsert(label)
        return label.id
    }

    companion object {
        /** Number of entries in the UI color palette for projects and labels. */
        const val PALETTE_SIZE = 8
    }
}

class AddSubtaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val clock: Clock,
) {
    /** Subtasks inherit the parent's project; they have no date or reminder of their own. */
    suspend operator fun invoke(parent: Task, title: String): Task? {
        if (title.isBlank()) return null
        val now = Instant.now(clock)
        val subtask = Task(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            parentId = parent.id,
            projectId = parent.projectId,
            createdAt = now,
            updatedAt = now,
        )
        tasks.upsert(subtask)
        return subtask
    }
}

class ProjectUseCases @Inject constructor(
    private val projects: ProjectRepository,
    private val clock: Clock,
) {
    suspend fun create(name: String, color: Int): Project? {
        if (name.isBlank()) return null
        val now = Instant.now(clock)
        val sortOrder = (projects.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return Project(UUID.randomUUID().toString(), name.trim(), color, sortOrder = sortOrder, createdAt = now, updatedAt = now)
            .also { projects.upsert(it) }
    }

    suspend fun update(project: Project) = projects.upsert(project.copy(updatedAt = Instant.now(clock)))

    suspend fun setArchived(project: Project, archived: Boolean) = update(project.copy(archived = archived))

    suspend fun delete(project: Project) = projects.delete(project.id)
}

class UpdateTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    suspend operator fun invoke(task: Task) {
        // A recurring task keeps its series anchored to its (possibly new) first date.
        val anchored = if (task.recurrence != null && task.recurrenceStart == null) task.copy(recurrenceStart = task.due?.date) else task
        val updated = anchored.copy(updatedAt = Instant.now(clock))
        tasks.upsert(updated)
        reminders.sync(updated)
    }
}

class CompleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    /**
     * Completes a task. A recurring task instead advances to its next occurrence after today
     * (missed occurrences are skipped) and the completion is recorded.
     */
    suspend operator fun invoke(task: Task): Undo {
        val now = Instant.now(clock)
        val due = task.due
        val spec = task.recurrence?.let(RecurrenceSpec::parse)
        if (spec != null && due != null) {
            val today = LocalDate.now(clock)
            val after = maxOf(due.date, today.minusDays(1))
            val next = spec.nextAfter(after, task.recurrenceStart ?: due.date)
            val advanced = task.copy(due = due.withDate(next), updatedAt = now)
            tasks.upsert(advanced)
            tasks.recordCompletion(task.id, due.date, now)
            reminders.sync(advanced)
            return Undo {
                tasks.upsert(task)
                tasks.removeCompletion(task.id, due.date)
                reminders.sync(task)
            }
        }
        val done = task.copy(completedAt = now, updatedAt = now)
        tasks.upsert(done)
        reminders.sync(done)
        return Undo {
            tasks.upsert(task)
            reminders.sync(task)
        }
    }
}

class ReopenTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    suspend operator fun invoke(task: Task) {
        val reopened = task.copy(completedAt = null, updatedAt = Instant.now(clock))
        tasks.upsert(reopened)
        reminders.sync(reopened)
    }
}

class DeleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(task: Task): Undo {
        tasks.delete(task.id)
        reminders.cancel(task.id)
        return Undo {
            tasks.restore(task.id)
            reminders.sync(task)
        }
    }
}
