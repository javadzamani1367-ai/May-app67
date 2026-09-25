package ir.roozban.ai.eval

import ir.roozban.ai.tools.AssistantContext
import ir.roozban.ai.tools.Operation
import ir.roozban.ai.tools.Plan
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** One eval case: a user message, what the assistant should do, and a reference answer. */
data class EvalCase(val id: String, val input: String, val expect: List<Map<String, JsonPrimitive>>, val golden: String?)

object EvalCases {
    fun load(file: File): List<EvalCase> = file.readLines().filter { it.isNotBlank() }.map { line ->
        val o = Json.parseToJsonElement(line).jsonObject
        EvalCase(
            id = (o["id"] as JsonPrimitive).content,
            input = (o["input"] as JsonPrimitive).content,
            expect = (o["expect"] as JsonArray).map { e -> e.jsonObject.mapValues { it.value as JsonPrimitive } },
            golden = (o["golden"] as? JsonPrimitive)?.contentOrNull,
        )
    }
}

/**
 * The fixed world every case runs in: پنجشنبه ۲ مهر ۱۴۰۵، ۱۰:۰۰, with a few tasks and habits.
 * Task numbers in the prompt follow this order.
 */
object Scenario {
    val today: LocalDate = JalaliDate.of(1405, 7, 2).toLocalDate()
    private val now = Instant.parse("2026-09-24T06:30:00Z")

    private fun task(n: Int, title: String, due: TaskDue?, important: Boolean = false) =
        Task(id = "t$n", title = title, due = due, important = important, createdAt = now, updatedAt = now)

    private fun habit(n: Int, name: String, perDay: Int = 1) =
        Habit(id = "h$n", name = name, schedule = HabitSchedule.Daily, targetPerDay = perDay, startDate = today.minusDays(30), createdAt = now, updatedAt = now)

    fun context() = AssistantContext(
        now = today.atTime(10, 0),
        tasks = listOf(
            task(1, "خرید نان", TaskDue.At(today, LocalTime.of(18, 0))),
            task(2, "جلسه با علی", TaskDue.At(today, LocalTime.of(14, 30))),
            task(3, "تمدید بیمه ماشین", TaskDue.AllDay(today.plusDays(1))),
            task(4, "گزارش ماهانه", TaskDue.AllDay(today.minusDays(2)), important = true),
            task(5, "زنگ به دندانپزشک", null),
            task(6, "خرید هدیه تولد سارا", TaskDue.AllDay(today.plusDays(3))),
        ),
        habits = listOf(habit(1, "ورزش"), habit(2, "نوشیدن آب", 8), habit(3, "کتاب خواندن")),
    )
}

/** Compares a plan with a case's expectations; returns what differs (empty = pass). */
object Judge {
    fun check(case: EvalCase, plan: Plan): List<String> {
        val actions = plan.actions
        val problems = mutableListOf<String>()
        actions.filter { !it.runnable }.forEach { problems += "not runnable: ${it.call.tool}: ${it.problem}" }
        val runnable = actions.filter { it.runnable }
        if (runnable.size != case.expect.size) problems += "expected ${case.expect.size} actions, got ${runnable.size}: ${runnable.map { it.call.tool }}"
        case.expect.zip(runnable).forEachIndexed { i, (exp, act) ->
            val tool = exp.getValue("tool").content
            if (act.call.tool != tool) {
                problems += "#$i tool: expected $tool, got ${act.call.tool}"
                return@forEachIndexed
            }
            exp.filterKeys { it != "tool" }.forEach { (key, want) -> checkField(i, key, want, act.operation!!)?.let(problems::add) }
        }
        return problems
    }

    private fun checkField(i: Int, key: String, want: JsonPrimitive, op: Operation): String? {
        fun fail(got: Any?) = "#$i $key: expected ${want.content}, got $got"
        return when (key) {
            "title" -> {
                val got = when (op) {
                    is Operation.CreateTask -> op.title
                    is Operation.UpdateTask -> op.updated.title
                    is Operation.CreateHabit -> op.name
                    else -> null
                }
                if (got != null && normalize(got).contains(normalize(want.content))) null else fail(got)
            }
            "due" -> {
                val got = when (op) {
                    is Operation.CreateTask -> op.due
                    is Operation.Reschedule -> op.due
                    is Operation.RescheduleMany -> TaskDue.AllDay(op.date)
                    else -> null
                }
                if (dueMatches(want.content, got, op is Operation.RescheduleMany)) null else fail(got)
            }
            "task" -> {
                val got = when (op) {
                    is Operation.CompleteTask -> op.task.title
                    is Operation.DeleteTask -> op.task.title
                    is Operation.Reschedule -> op.task.title
                    is Operation.UpdateTask -> op.task.title
                    is Operation.StartFocus -> op.task?.title ?: "none"
                    else -> null
                }
                if (got == want.content) null else fail(got)
            }
            "habit" -> ((op as? Operation.LogHabit)?.habit?.name).let { if (it == want.content) null else fail(it) }
            "range" -> ((op as? Operation.ListTasks)?.range).let { if (it == want.content) null else fail(it) }
            "day" -> ((op as? Operation.FindFreeSlot)?.date).let { if (it == offset(want.content)) null else fail(it) }
            "minutes" -> when (op) {
                is Operation.FindFreeSlot -> op.minutes
                is Operation.CreateTask -> op.minutes
                else -> null
            }.let { if (it == want.intOrNull) null else fail(it) }
            "important" -> when (op) {
                is Operation.CreateTask -> op.important
                is Operation.UpdateTask -> op.updated.important
                else -> null
            }.let { if (it == want.booleanOrNull) null else fail(it) }
            "urgent" -> when (op) {
                is Operation.CreateTask -> op.urgent
                is Operation.UpdateTask -> op.updated.urgent
                else -> null
            }.let { if (it == want.booleanOrNull) null else fail(it) }
            "repeat" -> ((op as? Operation.CreateTask)?.recurrence != null).let { if (it == want.booleanOrNull) null else fail(it) }
            "project" -> ((op as? Operation.CreateTask)?.project).let { if (it != null && normalize(it).contains(normalize(want.content))) null else fail(it) }
            "per_week" -> ((op as? Operation.CreateHabit)?.schedule).let { s ->
                val got = when (s) {
                    HabitSchedule.Daily -> "daily"
                    is HabitSchedule.TimesPerWeek -> s.times.toString()
                    else -> s.toString()
                }
                if (got == want.content) null else fail(got)
            }
            "reminder" -> ((op as? Operation.CreateHabit)?.reminder).let { if (it == LocalTime.parse(want.content)) null else fail(it) }
            else -> "#$i unknown expectation $key"
        }
    }

    /** "none", "+N" (a day, any time for a bulk move) or "+N HH:MM". */
    private fun dueMatches(spec: String, due: TaskDue?, dayOnly: Boolean): Boolean {
        if (spec == "none") return due == null
        due ?: return false
        val parts = spec.split(' ')
        if (due.date != offset(parts[0])) return false
        return when {
            parts.size == 1 -> dayOnly || due is TaskDue.AllDay
            else -> due is TaskDue.At && due.time == LocalTime.parse(parts[1])
        }
    }

    private fun offset(s: String): LocalDate = Scenario.today.plusDays(s.removePrefix("+").toLong())

    private fun normalize(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("\u200C", "").replace(" ", "")
}
