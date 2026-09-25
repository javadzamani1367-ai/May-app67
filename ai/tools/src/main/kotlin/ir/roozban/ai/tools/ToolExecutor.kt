package ir.roozban.ai.tools

import ir.roozban.core.domain.AddTaskUseCase
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.DeleteTaskUseCase
import ir.roozban.core.domain.FocusService
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.HabitUseCases
import ir.roozban.core.domain.QuickAddResult
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.Undo
import ir.roozban.core.domain.UpdateTaskUseCase
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

sealed interface ActionOutput {
    data class TaskList(val tasks: List<Task>) : ActionOutput

    data class FreeSlots(val date: LocalDate, val minutes: Int, val starts: List<LocalTime>) : ActionOutput
}

data class ActionResult(
    val action: PlannedAction,
    val ok: Boolean,
    /** Persian, for the action card. */
    val message: String,
    val output: ActionOutput? = null,
    val undo: Undo? = null,
)

/** Runs planned actions through the app's use cases; every write comes back with its undo. */
class ToolExecutor @Inject constructor(
    private val tasks: TaskRepository,
    private val addTask: AddTaskUseCase,
    private val updateTask: UpdateTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val habits: HabitRepository,
    private val habitUseCases: HabitUseCases,
    private val focus: FocusService,
    private val clock: Clock,
) {
    suspend fun execute(plan: Plan): List<ActionResult> = plan.actions.map { execute(it) }

    suspend fun execute(action: PlannedAction): ActionResult {
        val op = action.operation ?: return ActionResult(action, ok = false, message = action.problem ?: action.summary)
        return try {
            run(action, op)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ActionResult(action, ok = false, message = "انجام نشد: ${action.summary}")
        }
    }

    private suspend fun run(action: PlannedAction, op: Operation): ActionResult = when (op) {
        is Operation.CreateTask -> {
            val input = QuickAddResult(
                title = op.title,
                due = op.due,
                recurrence = op.recurrence,
                estimate = op.minutes?.let { Duration.ofMinutes(it.toLong()) },
                important = op.important,
                urgent = op.urgent,
                projectName = op.project,
                highlights = emptyList(),
                confidence = 1f,
            )
            val task = addTask(input)
            if (task == null) {
                ActionResult(action, false, "عنوان کار خالی بود.")
            } else {
                ActionResult(action, true, action.summary, undo = Undo { deleteTask(task) })
            }
        }
        is Operation.UpdateTask -> {
            updateTask(op.updated)
            ActionResult(action, true, action.summary, undo = Undo { updateTask(op.task) })
        }
        is Operation.Reschedule -> {
            updateTask(op.task.copy(due = op.due))
            ActionResult(action, true, action.summary, undo = Undo { updateTask(op.task) })
        }
        is Operation.RescheduleMany -> {
            op.tasks.forEach { updateTask(it.copy(due = it.due?.withDate(op.date) ?: TaskDue.AllDay(op.date))) }
            ActionResult(action, true, action.summary, undo = Undo { op.tasks.forEach { updateTask(it) } })
        }
        is Operation.CompleteTask -> {
            val current = tasks.get(op.task.id) ?: op.task
            val undo = completeTask(current)
            ActionResult(action, true, action.summary, undo = undo)
        }
        is Operation.DeleteTask -> {
            val undo = deleteTask(op.task)
            ActionResult(action, true, action.summary, undo = undo)
        }
        is Operation.ListTasks -> {
            val list = listTasks(op.range)
            val message = if (list.isEmpty()) "کاری پیدا نشد." else action.summary
            ActionResult(action, true, message, ActionOutput.TaskList(list))
        }
        is Operation.FindFreeSlot -> {
            val open = tasks.observeOpenTasks().first()
            val starts = FreeSlots.find(op.date, op.minutes, open, LocalDateTime.now(clock))
            val message = if (starts.isEmpty()) "وقت آزادی به این اندازه پیدا نکردم." else action.summary
            ActionResult(action, true, message, ActionOutput.FreeSlots(op.date, op.minutes, starts))
        }
        is Operation.StartFocus -> {
            focus.start(op.task?.id)
            ActionResult(action, true, action.summary, undo = Undo { focus.stop() })
        }
        is Operation.CreateHabit -> {
            val habit = habitUseCases.create(op.name, color = Math.floorMod(op.name.hashCode(), 8), op.schedule, op.perDay, op.reminder)
            if (habit == null) {
                ActionResult(action, false, "نام عادت خالی بود.")
            } else {
                ActionResult(action, true, action.summary, undo = Undo { habitUseCases.delete(habit) })
            }
        }
        is Operation.LogHabit -> {
            val today = LocalDate.now(clock)
            val habit = habits.get(op.habit.id) ?: op.habit
            val before = habits.log(habit.id, today)?.count ?: 0
            val target = op.count ?: (before + 1)
            val stored = habitUseCases.setCount(habit, today, target)
            ActionResult(action, true, "${action.summary} (${stored}/${habit.targetPerDay})".toPersianDigits(), undo = Undo { habitUseCases.setCount(habit, today, before) })
        }
    }

    private suspend fun listTasks(range: String): List<Task> {
        val today = LocalDate.now(clock)
        val open = tasks.observeOpenTasks().first()
        return when (range) {
            Tools.RANGE_TODAY -> open.filter { t -> t.due?.let { it.date <= today } == true }
            Tools.RANGE_TOMORROW -> open.filter { it.due?.date == today.plusDays(1) }
            Tools.RANGE_WEEK -> open.filter { t -> t.due?.let { it.date <= today.plusDays(6) } == true }
            Tools.RANGE_OVERDUE -> open.filter { t -> t.due?.let { it.date < today } == true }
            else -> open
        }.sortedWith(compareBy<Task>({ it.due?.date ?: LocalDate.MAX }, { (it.due as? TaskDue.At)?.time ?: LocalTime.MAX }))
    }

    private fun String.toPersianDigits() = ir.roozban.core.calendar.PersianDigits.toPersian(this)
}

/** Gaps between timed tasks, within the waking day. */
object FreeSlots {
    val DAY_START: LocalTime = LocalTime.of(8, 0)
    val DAY_END: LocalTime = LocalTime.of(22, 0)
    private const val DEFAULT_TASK_MINUTES = 30L
    private const val STEP_MINUTES = 5L

    fun find(date: LocalDate, minutes: Int, open: List<Task>, now: LocalDateTime, limit: Int = 3): List<LocalTime> {
        var from = date.atTime(DAY_START)
        if (date == now.toLocalDate() && now > from) from = roundUp(now)
        val end = date.atTime(DAY_END)
        val busy = open.mapNotNull { task ->
            val due = task.due as? TaskDue.At ?: return@mapNotNull null
            if (due.date != date) return@mapNotNull null
            due.dateTime to due.dateTime.plusMinutes(task.estimateMinutes?.toLong() ?: DEFAULT_TASK_MINUTES)
        }.sortedBy { it.first }
        val slots = mutableListOf<LocalTime>()
        var cursor = from
        for ((s, e) in busy + (end to end)) {
            if (Duration.between(cursor, s).toMinutes() >= minutes && slots.size < limit) slots += cursor.toLocalTime()
            if (e > cursor) cursor = e
            if (cursor >= end) break
        }
        return slots
    }

    private fun roundUp(t: LocalDateTime): LocalDateTime {
        val base = t.withSecond(0).withNano(0)
        val extra = (STEP_MINUTES - base.minute % STEP_MINUTES) % STEP_MINUTES
        return base.plusMinutes(if (extra == 0L && t.second > 0) STEP_MINUTES else extra)
    }
}
