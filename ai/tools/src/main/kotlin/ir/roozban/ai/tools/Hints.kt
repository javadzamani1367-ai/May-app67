package ir.roozban.ai.tools

import ir.roozban.core.domain.toParserPrefs
import ir.roozban.core.timeparser.PersianTimeParser
import ir.roozban.core.timeparser.SpanKind

/**
 * What the app itself finds in a message, given to the model as a guide: the listed tasks and
 * habits the message mentions and its time words. Small models pick tools far better with it.
 */
data class Hints(
    /** 1-based numbers in the prompt's task list. */
    val tasks: List<Int>,
    val habits: List<String>,
    /** The time words exactly as written, or null. */
    val time: String?,
    val repeat: String?,
) {
    companion object {
        private const val MIN_SHARE = 0.6
        private const val MAX = 3

        fun find(context: AssistantContext, message: String): Hints {
            val tasks = context.tasks.mapIndexed { i, t -> i + 1 to Matcher.mentions(message, t.title) }
                .filter { it.second >= MIN_SHARE }.sortedByDescending { it.second }.take(MAX).map { it.first }.sorted()
            val habits = context.habits.filter { !it.archived && Matcher.mentions(message, it.name) >= MIN_SHARE }.map { it.name }.take(MAX)
            val parsed = PersianTimeParser(context.settings.toParserPrefs()).parse(message, context.now)
            fun spans(kind: SpanKind) = parsed.spans.filter { it.kind == kind }.sortedBy { it.start }
                .joinToString(" ") { message.substring(it.start, it.end).trim() }.ifBlank { null }
            return Hints(tasks, habits, spans(SpanKind.TIME), spans(SpanKind.RECURRENCE))
        }
    }
}
