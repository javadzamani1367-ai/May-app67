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
 * Renders the prompt: instructions, tools, the app's current state and recent turns, trimmed to
 * fit the model's context with room left for the answer.
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
                else -> return prompt // Instructions alone; the engine truncates if it must.
            }
        }
    }

    fun render(context: AssistantContext, taskCount: Int, turns: List<Turn>, message: String): String {
        val messages = buildList {
            add(ChatMessage(Role.SYSTEM, system(context, taskCount)))
            turns.forEach {
                add(ChatMessage(Role.USER, it.user))
                add(ChatMessage(Role.ASSISTANT, it.assistant))
            }
            add(ChatMessage(Role.USER, message))
        }
        return template.render(messages)
    }

    private fun system(context: AssistantContext, taskCount: Int): String = buildString {
        appendLine(INSTRUCTIONS)
        appendLine()
        appendLine("Tools (args always in this order):")
        Tools.all.forEach { appendLine(toolLine(it)) }
        appendLine()
        val today = context.today
        val j = today.toJalali()
        appendLine("Now: ${PersianDateFormatter.fullDate(j)}، ساعت ${PersianDateFormatter.time(context.now.toLocalTime())} ($today)")
        val shown = context.tasks.take(taskCount)
        if (shown.isEmpty()) {
            appendLine(if (context.tasks.isEmpty()) "Open tasks: none" else "Open tasks: (not shown; refer by title)")
        } else {
            appendLine("Open tasks:")
            shown.forEachIndexed { i, t ->
                append('#').append(i + 1).append(' ').append(t.title)
                t.due?.let { append(" — ").append(Describe.due(it, today)) }
                if (t.important) append(" [مهم]")
                if (t.urgent) append(" [فوری]")
                if (t.recurrence != null) append(" [تکراری]")
                appendLine()
            }
            if (context.tasks.size > shown.size) appendLine("(+${context.tasks.size - shown.size} more)")
        }
        val habits = context.habits.filter { !it.archived }
        if (habits.isNotEmpty()) {
            appendLine("Habits: " + habits.joinToString("، ") { h ->
                h.name + when {
                    h.targetPerDay > 1 -> " (${PersianDigits.format(h.targetPerDay)} بار در روز)"
                    h.schedule is HabitSchedule.TimesPerWeek -> " (${PersianDigits.format((h.schedule as HabitSchedule.TimesPerWeek).times)} روز در هفته)"
                    else -> ""
                }
            })
        }
        appendLine()
        append(EXAMPLES)
    }

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

    companion object {
        const val ANSWER_TOKENS = 384
        private const val SAFETY_TOKENS = 32
        private const val MAX_TURNS = 4
        private const val MAX_TASKS = 40
        private const val MIN_TASKS = 8
        private const val MAX_MESSAGE_CHARS = 1000

        val INSTRUCTIONS = """
            You are Roozban (روزبان), the offline assistant of a Persian to-do, habit and calendar app. You act on the user's data with tools.
            Answer only with compact JSON: {"actions":[...],"reply":"..."}
            Rules:
            - actions: the tool calls that do exactly what the user asked, in order. Use [] for greetings, questions or unclear requests.
            - reply: short friendly Persian (one or two sentences) saying what you did, or asking what is missing. Never claim something you did not do.
            - Copy the user's own Persian time words into "when"/"day" (e.g. "فردا ساعت ۹", "شنبه عصر"). Never calculate dates yourself.
            - Refer to an existing task by its number, like "#2". Never invent tasks.
            - Write every argument; use null when unknown.
            - Several requests in one message mean several actions.
        """.trimIndent()

        val EXAMPLES = """
            Examples:
            User: فردا ساعت ۵ عصر به مامان زنگ بزنم
            {"actions":[{"tool":"create_task","args":{"title":"زنگ به مامان","when":"فردا ساعت ۵ عصر","repeat":null,"important":false,"urgent":false,"minutes":null,"project":null}}],"reply":"تماس با مامان برای فردا ساعت ۵ عصر ثبت شد."}
            User: کار ۲ رو انجام دادم، کار ۳ رو هم بذار برای پنجشنبه
            {"actions":[{"tool":"complete_task","args":{"task":"#2"}},{"tool":"reschedule","args":{"task":"#3","when":"پنجشنبه"}}],"reply":"کار دوم تیک خورد و کار سوم رفت برای پنجشنبه."}
            User: سلام
            {"actions":[],"reply":"سلام! بگو چه کاری برات ثبت یا مرتب کنم."}
        """.trimIndent()
    }
}
