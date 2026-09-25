package ir.roozban.ai.tools

import ir.roozban.ai.core.ChatMessage
import ir.roozban.ai.core.ChatTemplate
import ir.roozban.ai.core.Role
import ir.roozban.ai.core.TokenEstimate
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.model.HabitSchedule

/** One earlier exchange: what the user wrote and the model's raw JSON answer. */
data class Turn(val user: String, val assistant: String)

/**
 * Renders the prompt in two parts:
 * - a fixed prefix (instructions, tools, worked examples as chat turns) that never changes, so
 *   the engine computes it once ([prefix]) and reuses it for every message;
 * - recent turns and the final user turn, which carries the current state (time, numbered open
 *   tasks, habits) followed by the message. It is trimmed to fit the context with room left
 *   for the answer.
 */
class PromptBuilder(
    private val template: ChatTemplate,
    private val contextTokens: Int,
    private val answerTokens: Int = ANSWER_TOKENS,
    private val count: suspend (String) -> Int = { TokenEstimate.of(it) },
) {
    suspend fun build(context: AssistantContext, history: List<Turn>, message: String): String {
        val budget = contextTokens - answerTokens - SAFETY_TOKENS
        var turns = history.takeLast(MAX_TURNS)
        var taskCount = minOf(context.tasks.size, MAX_TASKS)
        val userText = message.trim().take(MAX_MESSAGE_CHARS)
        while (true) {
            val prompt = render(context, taskCount, turns, userText)
            if (count(prompt) <= budget) return prompt
            when {
                turns.isNotEmpty() -> turns = turns.drop(1)
                taskCount > MIN_TASKS -> taskCount = maxOf(MIN_TASKS, taskCount / 2)
                taskCount > 0 -> taskCount = 0
                else -> return prompt // Fixed part alone; the engine refuses if it must.
            }
        }
    }

    /** The part every prompt starts with, for warming the engine up before the first message. */
    fun prefix(): String = template.render(fixedMessages(), openAnswer = false)

    fun render(context: AssistantContext, taskCount: Int, turns: List<Turn>, message: String): String {
        val messages = fixedMessages() + turns.flatMap { listOf(ChatMessage(Role.USER, MESSAGE_LABEL + it.user), ChatMessage(Role.ASSISTANT, it.assistant)) } +
            ChatMessage(Role.USER, state(context, taskCount) + hints(context, message, taskCount) + MESSAGE_LABEL + message)
        return template.render(messages)
    }

    private fun fixedMessages(): List<ChatMessage> = buildList {
        add(ChatMessage(Role.SYSTEM, SYSTEM))
        EXAMPLES.forEach {
            add(ChatMessage(Role.USER, it.user))
            add(ChatMessage(Role.ASSISTANT, it.answer))
        }
    }

    internal fun exampleTurns(): List<Example> {
        var world: AssistantContext? = null
        return Examples.all.map { (ctx, message, answer) ->
            val state = if (ctx !== world) stateOf(ctx, Int.MAX_VALUE) else ""
            world = ctx
            Example(state + hintLines(Hints.find(ctx, message), message, ctx) + MESSAGE_LABEL + message, answer)
        }
    }

    private fun hints(context: AssistantContext, message: String, taskCount: Int): String = hintLines(Hints.find(context, message), message, context, taskCount)

    private fun state(context: AssistantContext, taskCount: Int): String = stateOf(context, taskCount)

    companion object {
        const val ANSWER_TOKENS = 384
        private const val SAFETY_TOKENS = 32
        private const val MAX_TURNS = 4
        private const val MAX_TASKS = 40
        private const val MIN_TASKS = 8
        private const val MAX_MESSAGE_CHARS = 1000

        const val MESSAGE_LABEL = "پیام: "

        fun stateOf(context: AssistantContext, taskCount: Int): String = buildString {
            val today = context.today
            appendLine("امروز: ${PersianDateFormatter.fullDate(today.toJalali())}، ساعت ${PersianDateFormatter.time(context.now.toLocalTime())}")
            val shown = context.tasks.take(taskCount)
            if (shown.isEmpty()) {
                appendLine(if (context.tasks.isEmpty()) "کارها: ندارد" else "کارها: (فهرست جا نشد؛ با نام بگو)")
            } else {
                appendLine("کارها:")
                shown.forEachIndexed { i, t ->
                    // No dates: small models copy them into their answers. Times come from the message.
                    append('#').append(i + 1).append(' ').append(t.title)
                    if (t.due?.let { it.date < today } == true) append(" [عقب‌افتاده]")
                    if (t.important) append(" [مهم]")
                    if (t.urgent) append(" [فوری]")
                    if (t.recurrence != null) append(" [تکراری]")
                    appendLine()
                }
                if (context.tasks.size > shown.size) appendLine("(و ${PersianDigits.format(context.tasks.size - shown.size)} کار دیگر)")
            }
            val habits = context.habits.filter { !it.archived }
            append("عادت‌ها: ")
            appendLine(
                if (habits.isEmpty()) "ندارد" else habits.joinToString("، ") { h ->
                    h.name + when {
                        h.targetPerDay > 1 -> " (${PersianDigits.format(h.targetPerDay)} بار در روز)"
                        h.schedule is HabitSchedule.TimesPerWeek -> " (${PersianDigits.format((h.schedule as HabitSchedule.TimesPerWeek).times)} روز در هفته)"
                        else -> ""
                    }
                },
            )
        }

        /** The guide lines before a message; the examples use the same format. */
        fun hintLines(h: Hints, message: String, context: AssistantContext? = null, taskCount: Int = Int.MAX_VALUE): String = buildString {
            val tasks = h.tasks.filter { it <= taskCount }
            append("کار مرتبط در فهرست: ")
            appendLine(if (tasks.isEmpty()) "ندارد" else tasks.joinToString("، ") { n -> "#$n" + (context?.tasks?.getOrNull(n - 1)?.let { " ${it.title}" } ?: "") })
            if (h.habits.isNotEmpty()) appendLine("عادت مرتبط: " + h.habits.joinToString("، "))
            appendLine("زمان در پیام: " + (listOfNotNull(h.repeat, h.time).joinToString(" ").ifEmpty { "ندارد" }))

        }

        private val TOOLS_TEXT = Tools.all.joinToString("\n") { toolLine(it) }

        val SYSTEM: String = """
            You are Roozban (روزبان), the offline assistant of a Persian to-do, habit and calendar app. You act on the user's data by calling tools.
            Each user turn starts with the current state (date, numbered open tasks, habits) and ends with "پیام:" and the user's message.
            Answer only with compact JSON: {"actions":[...],"reply":"..."}
            Rules:
            - actions: the tool calls that do what the message asks, in order. Several requests mean several actions. Use [] only for greetings, thanks and questions no tool answers.
            - A new thing to do or remember is create_task. Something already in the task list is referenced by its number, like "#2".
            - Something done: an existing task → complete_task; a habit from the habit list → log_habit.
            - Before the message come guide lines from the app: "کار مرتبط در فهرست" (listed tasks the message mentions; "ندارد" means it is about something new), "عادت مرتبط" and "زمان در پیام" (the time words; copy them into "when"/"day", "ندارد" → null). Never calculate dates.
            - Write every argument; use null when unknown.
            - reply: short friendly Persian saying what you did or asking what is missing.

            Tools (arguments in this exact order):
        """.trimIndent() + "\n" + TOOLS_TEXT

        private fun toolLine(spec: ToolSpec): String {
            val args = spec.args.joinToString(",") { a ->
                val type = when (val t = a.type) {
                    is ArgType.Text -> "text"
                    is ArgType.Number -> "number"
                    is ArgType.Flag -> "true|false"
                    is ArgType.Choice -> t.values.joinToString("|") { "\"$it\"" }
                } + if (a.type.nullable) "|null" else ""
                "\"${a.name}\":$type"
            }
            val hints = spec.args.filter { it.hint.length > it.name.length + 3 }.joinToString("; ") { "${it.name}: ${it.hint}" }
            return "- ${spec.name} {$args} — ${spec.description}" + if (hints.isNotEmpty()) " ($hints)" else ""
        }

        val EXAMPLES: List<Example> by lazy { PromptBuilder(ChatTemplate.CHATML, 0).exampleTurns() }
    }
}

/** A worked example: a user turn (state + message) and the exact answer. */
data class Example(val user: String, val answer: String)
