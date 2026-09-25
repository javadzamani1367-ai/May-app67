package ir.roozban.core.domain

import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.learning.DayPlan
import ir.roozban.learning.DayPlanner
import ir.roozban.learning.FixedBlock
import ir.roozban.learning.PlanCandidate
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Proposes a day: open tasks without a time (due that day or earlier, plus important undated
 * ones) are placed around the timed tasks already there. The result is a proposal the user
 * approves item by item; [apply] gives each accepted task its time and can be undone.
 */
class PlanDayUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val store: LearningStore,
    private val update: UpdateTaskUseCase,
    private val clock: Clock,
) {
    suspend fun propose(date: LocalDate = LocalDate.now(clock)): DayPlan {
        val planning = settings.current().planning
        val learned = store.load() ?: LearningSnapshot.EMPTY
        val open = tasks.observeOpenTasks().first().filter { it.parentId == null }
        val fixed = open.mapNotNull { t ->
            val due = t.due as? TaskDue.At ?: return@mapNotNull null
            if (due.date != date) return@mapNotNull null
            FixedBlock(t.id, t.title, due.time, minutesFor(t, learned))
        }
        val candidates = open.filter { isCandidate(it, date) }.map { t ->
            PlanCandidate(
                taskId = t.id,
                title = t.title,
                minutes = minutesFor(t, learned),
                important = t.important,
                urgent = t.urgent,
                due = t.due?.date,
                risk = learned.risk(t, clock.zone),
            )
        }
        val today = LocalDate.now(clock)
        return DayPlanner.plan(
            date = date,
            dayStart = planning.dayStart,
            dayEnd = planning.dayEnd,
            notBefore = if (date == today) LocalTime.now(clock) else null,
            fixed = fixed,
            candidates = candidates,
            hours = learned.hours,
        )
    }

    /** Gives each accepted placement its time; tasks changed or finished meanwhile are skipped. */
    suspend fun apply(plan: DayPlan, accepted: Set<String> = plan.placements.mapTo(HashSet()) { it.candidate.taskId }): PlanResult {
        val before = ArrayList<Task>()
        for (p in plan.placements) {
            if (p.candidate.taskId !in accepted) continue
            val task = tasks.get(p.candidate.taskId) ?: continue
            if (task.isCompleted || !isCandidate(task, plan.date)) continue
            before += task
            update(task.copy(due = TaskDue.At(plan.date, p.start)))
        }
        return PlanResult(before.size, Undo { before.forEach { update(it) } })
    }

    private fun isCandidate(t: Task, date: LocalDate): Boolean {
        // A recurring task's time belongs to the whole series, so the planner leaves it alone.
        if (t.recurrence != null || t.isCompleted || t.parentId != null) return false
        return when (val due = t.due) {
            null -> t.important
            is TaskDue.AllDay -> !due.date.isAfter(date)
            is TaskDue.At -> due.date.isBefore(date) && due.date.isBefore(LocalDate.now(clock))
        }
    }

    private fun minutesFor(t: Task, learned: LearningSnapshot): Int =
        t.estimateMinutes?.let { learned.factors.correct(it, t.projectId) } ?: DEFAULT_MINUTES

    companion object {
        const val DEFAULT_MINUTES = 30
    }
}

data class PlanResult(val applied: Int, val undo: Undo)

/** Moves one-off tasks whose date has passed to [today], as all-day tasks the planner can place. */
class RolloverUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val update: UpdateTaskUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock)): PlanResult {
        val overdue = tasks.observeOpenTasks().first().filter {
            it.recurrence == null && it.parentId == null && !it.isCompleted && it.due != null && it.due!!.date.isBefore(today)
        }
        overdue.forEach { update(it.copy(due = TaskDue.AllDay(today))) }
        return PlanResult(overdue.size, Undo { overdue.forEach { update(it) } })
    }
}

/** The morning routine: roll overdue tasks over, then propose (or, in auto mode, apply) the day. */
class MorningPlanUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val rollover: RolloverUseCase,
    private val planDay: PlanDayUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(): MorningResult {
        val today = LocalDate.now(clock)
        val rolled = rollover(today)
        val plan = planDay.propose(today)
        val applied = if (settings.current().planning.autoPlan && plan.placements.isNotEmpty()) planDay.apply(plan) else null
        return MorningResult(rolled.applied, plan, applied?.applied ?: 0)
    }
}

data class MorningResult(val rolledOver: Int, val plan: DayPlan, val autoApplied: Int)
