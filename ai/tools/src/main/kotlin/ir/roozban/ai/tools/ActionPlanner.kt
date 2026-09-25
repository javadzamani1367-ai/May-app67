package ir.roozban.ai.tools

import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.recurrence.RecurrenceSpec
import java.time.LocalDate
import java.time.LocalTime

/** A validated action, ready to run. */
sealed interface Operation {
    data class CreateTask(
        val title: String,
        val due: TaskDue?,
        val recurrence: RecurrenceSpec?,
        val important: Boolean,
        val urgent: Boolean,
        val minutes: Int?,
        val project: String?,
    ) : Operation

    data class UpdateTask(val task: Task, val updated: Task) : Operation

    data class Reschedule(val task: Task, val due: TaskDue) : Operation

    data class CompleteTask(val task: Task) : Operation

    data class DeleteTask(val task: Task) : Operation

    data class RescheduleMany(val tasks: List<Task>, val date: LocalDate) : Operation

    data class ListTasks(val range: String) : Operation

    data class FindFreeSlot(val date: LocalDate, val minutes: Int) : Operation

    data class StartFocus(val task: Task?) : Operation

    data class CreateHabit(val name: String, val schedule: HabitSchedule, val perDay: Int, val reminder: LocalTime?) : Operation

    data class LogHabit(val habit: Habit, val count: Int?) : Operation
}

/**
 * One action of an answer. Either [operation] is set, or [problem] says in Persian why it
 * cannot run (unknown task, unclear time, …) and [candidates] may list tasks to choose from.
 */
data class PlannedAction(
    val call: ToolCall,
    val risk: Risk,
    val summary: String,
    val operation: Operation?,
    val problem: String? = null,
    val candidates: List<Task> = emptyList(),
) {
    val runnable: Boolean get() = operation != null
}

data class Plan(val actions: List<PlannedAction>, val reply: String) {
    /** Removals, bulk changes, or many writes at once are shown for approval before running. */
    val needsConfirmation: Boolean
        get() {
            val runnable = actions.filter { it.runnable }
            return runnable.any { it.risk == Risk.DESTRUCTIVE || it.risk == Risk.BULK } ||
                runnable.count { it.risk != Risk.READ } > Tools.MAX_UNCONFIRMED_WRITES
        }
}

/** Checks the model's tool calls against the app's data and resolves references and times. */
class ActionPlanner(private val context: AssistantContext, private val message: String? = null) {
    private val times = WhenResolver(context.settings)
    private val now = context.now
    private val hints = message?.let { Hints.find(context, it) }

    /**
     * Time words must come from the user's message. The model's text is kept when the message
     * contains it; otherwise (a date copied from elsewhere, or invented) the time words the app
     * found in the message replace it, or nothing when there were none.
     */
    private fun fromMessage(text: String?, detected: String?): String? {
        val msg = message ?: return text
        if (text != null && Matcher.normalize(msg).contains(Matcher.normalize(text))) return text
        return detected
    }

    private fun ToolCall.time(name: String) = fromMessage(text(name), hints?.time)

    fun plan(response: AssistantResponse): Plan =
        Plan(response.actions.take(Tools.MAX_ACTIONS).map(::planOne), response.reply)

    fun planOne(call: ToolCall): PlannedAction {
        val spec = Tools.get(call.tool) ?: return PlannedAction(call, Risk.READ, call.tool, null, "این کار را بلد نیستم.")
        return try {
            when (spec) {
                Tools.createTask -> createTask(call)
                Tools.updateTask -> withTask(call, spec) { updateTask(call, it) }
                Tools.reschedule -> withTask(call, spec) { task ->
                    val due = resolveDue(call.time("when"), task.due) ?: return@withTask fail(call, spec, "«${task.title}» — زمان تازه‌اش را نفهمیدم.")
                    PlannedAction(call, spec.risk, "جابه‌جایی «${task.title}» به ${Describe.due(due, context.today)}", Operation.Reschedule(task, due))
                }
                Tools.completeTask -> withTask(call, spec) { PlannedAction(call, spec.risk, "انجام شد: «${it.title}»", Operation.CompleteTask(it)) }
                Tools.deleteTask -> withTask(call, spec) { PlannedAction(call, spec.risk, "حذف «${it.title}»", Operation.DeleteTask(it)) }
                Tools.rescheduleOverdue -> rescheduleOverdue(call, spec)
                Tools.listTasks -> {
                    val range = call.text("range")?.takeIf { it in Tools.RANGES } ?: Tools.RANGE_TODAY
                    PlannedAction(call, spec.risk, "نمایش کارها: ${rangeName(range)}", Operation.ListTasks(range))
                }
                Tools.findFreeSlot -> {
                    val day = call.time("day")?.let { times.day(it, now) } ?: context.today
                    val minutes = (call.number("minutes") ?: 30).coerceIn(5, 600)
                    PlannedAction(call, spec.risk, "وقت آزاد ${PersianDigits.format(minutes)} دقیقه‌ای، ${Describe.day(day, context.today)}", Operation.FindFreeSlot(day, minutes))
                }
                Tools.startFocus -> {
                    val ref = call.text("task")
                    if (ref == null) {
                        PlannedAction(call, spec.risk, "شروع تمرکز", Operation.StartFocus(null))
                    } else {
                        withTask(call, spec) { PlannedAction(call, spec.risk, "شروع تمرکز روی «${it.title}»", Operation.StartFocus(it)) }
                    }
                }
                Tools.createHabit -> createHabit(call, spec)
                Tools.logHabit -> logHabit(call, spec)
                else -> fail(call, spec, "این کار را بلد نیستم.")
            }
        } catch (e: IllegalArgumentException) {
            fail(call, spec, "اطلاعات این کار کامل نبود.")
        }
    }

    private fun createTask(call: ToolCall): PlannedAction {
        val spec = Tools.createTask
        val title = call.text("title") ?: return fail(call, spec, "عنوان کار مشخص نبود.")
        val whenText = call.time("when")
        val due = whenText?.let { times.due(it, now) }
        if (whenText != null && due == null) return fail(call, spec, "زمان «$whenText» را نفهمیدم.")
        val recurrence = fromMessage(call.text("repeat"), hints?.repeat)?.let { times.recurrence(it, now) }
        val repeatDue = due ?: recurrence?.let { TaskDue.AllDay(context.today) }
        val summary = buildString {
            append("کار جدید: «").append(title).append('»')
            repeatDue?.let { append(" — ").append(Describe.due(it, context.today)) }
            if (recurrence != null) append(" (تکرارشونده)")
        }
        return PlannedAction(
            call, spec.risk, summary,
            Operation.CreateTask(
                title = title,
                due = repeatDue,
                recurrence = recurrence,
                important = call.flag("important") ?: false,
                urgent = call.flag("urgent") ?: false,
                minutes = call.number("minutes")?.takeIf { it in 1..1440 },
                project = call.text("project"),
            ),
        )
    }

    private fun updateTask(call: ToolCall, task: Task): PlannedAction {
        val updated = task.copy(
            title = call.text("title") ?: task.title,
            important = call.flag("important") ?: task.important,
            urgent = call.flag("urgent") ?: task.urgent,
            notes = call.text("notes") ?: task.notes,
        )
        if (updated == task) return fail(call, Tools.updateTask, "برای «${task.title}» تغییری مشخص نشده بود.")
        val what = buildList {
            if (updated.title != task.title) add("عنوان «${updated.title}»")
            if (updated.important != task.important) add(if (updated.important) "مهم" else "غیرمهم")
            if (updated.urgent != task.urgent) add(if (updated.urgent) "فوری" else "غیرفوری")
            if (updated.notes != task.notes) add("یادداشت")
        }.joinToString("، ")
        return PlannedAction(call, Tools.updateTask.risk, "ویرایش «${task.title}»: $what", Operation.UpdateTask(task, updated))
    }

    private fun rescheduleOverdue(call: ToolCall, spec: ToolSpec): PlannedAction {
        val date = call.time("when")?.let { times.day(it, now) } ?: return fail(call, spec, "روز مقصد را نفهمیدم.")
        val overdue = context.tasks.filter { t -> t.due?.let { it.date < context.today } == true }
        if (overdue.isEmpty()) return fail(call, spec, "کار عقب‌افتاده‌ای نداری.")
        return PlannedAction(
            call, spec.risk,
            "انتقال ${PersianDigits.format(overdue.size)} کار عقب‌افتاده به ${Describe.day(date, context.today)}",
            Operation.RescheduleMany(overdue, date),
        )
    }

    private fun createHabit(call: ToolCall, spec: ToolSpec): PlannedAction {
        val name = call.text("name") ?: return fail(call, spec, "نام عادت مشخص نبود.")
        val perWeek = call.number("per_week")?.takeIf { it in 1..6 }
        val schedule = if (perWeek == null) HabitSchedule.Daily else HabitSchedule.TimesPerWeek(perWeek)
        val perDay = (call.number("per_day") ?: 1).coerceIn(1, 20)
        val reminderText = call.time("reminder")
        val reminder = reminderText?.let { times.time(it, now) }
        if (reminderText != null && reminder == null) return fail(call, spec, "ساعت یادآوری «$reminderText» را نفهمیدم.")
        val summary = buildString {
            append("عادت جدید: «").append(name).append("» — ")
            append(if (perWeek == null) "هر روز" else "${PersianDigits.format(perWeek)} روز در هفته")
            if (perDay > 1) append("، ").append(PersianDigits.format(perDay)).append(" بار")
        }
        return PlannedAction(call, spec.risk, summary, Operation.CreateHabit(name, schedule, perDay, reminder))
    }

    private fun logHabit(call: ToolCall, spec: ToolSpec): PlannedAction {
        val ref = call.text("habit") ?: return fail(call, spec, "کدام عادت؟")
        val active = context.habits.filter { !it.archived }
        return when (val m = Matcher.find(ref, active) { it.name }) {
            is Match.Found -> PlannedAction(call, spec.risk, "ثبت عادت «${m.item.name}» برای امروز", Operation.LogHabit(m.item, call.number("count")))
            is Match.Ambiguous -> fail(call, spec, "کدام عادت؟ " + m.candidates.joinToString("، ") { "«${it.name}»" })
            Match.None -> fail(call, spec, "عادتی به اسم «$ref» پیدا نکردم.")
        }
    }

    private inline fun withTask(call: ToolCall, spec: ToolSpec, block: (Task) -> PlannedAction): PlannedAction {
        val ref = call.text("task") ?: return fail(call, spec, "کدام کار؟")
        return when (val m = Matcher.find(ref, context.tasks) { it.title }) {
            is Match.Found -> block(m.item)
            is Match.Ambiguous -> fail(call, spec, "منظورت کدام کار است؟", m.candidates)
            Match.None -> fail(call, spec, "کاری با عنوان «$ref» پیدا نکردم.")
        }
    }

    /** A new time for a task: a time-only phrase keeps the day, a day-only phrase keeps the time. */
    private fun resolveDue(text: String?, current: TaskDue?): TaskDue? {
        text ?: return null
        val due = times.due(text, now) ?: return null
        return if (due is TaskDue.AllDay && current is TaskDue.At && !mentionsTime(text)) TaskDue.At(due.date, current.time) else due
    }

    private fun mentionsTime(text: String) = TIME_WORDS.any { it in text }

    private fun fail(call: ToolCall, spec: ToolSpec, problem: String, candidates: List<Task> = emptyList()) =
        PlannedAction(call, spec.risk, problem, null, problem, candidates)

    private companion object {
        val TIME_WORDS = listOf("ساعت", "صبح", "ظهر", "عصر", "شب", ":")

        fun rangeName(range: String) = when (range) {
            Tools.RANGE_TODAY -> "امروز"
            Tools.RANGE_TOMORROW -> "فردا"
            Tools.RANGE_WEEK -> "این هفته"
            Tools.RANGE_OVERDUE -> "عقب‌افتاده"
            else -> "همه"
        }
    }
}
