package ir.roozban.ai.tools

import ir.roozban.core.calendar.PersianDigits
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
    /** A duration in the message («یک ساعت»), in minutes. */
    val minutes: Int? = null,
) {
    companion object {
        private const val MIN_SHARE = 0.6
        private const val MAX = 3
        private const val MIN_HABIT_SHARE = 0.5
        private val NUMBER_REF = Regex("(?:#|کار|شماره|تسک)\\s*(?:شماره\\s*)?(\\d{1,3})")

        fun find(context: AssistantContext, message: String): Hints {
            val byTitle = context.tasks.mapIndexed { i, t -> i + 1 to Matcher.mentions(message, t.title) }
                .filter { it.second >= MIN_SHARE }.sortedByDescending { it.second }.take(MAX).map { it.first }
            // «کار ۳», «شماره ۳», «#3»
            val byNumber = NUMBER_REF.findAll(PersianDigits.toAscii(message)).mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 1..context.tasks.size }.toList()
            val tasks = (byNumber + byTitle).distinct().sorted()
            val habits = context.habits.filter { !it.archived && Matcher.mentions(message, it.name) >= MIN_HABIT_SHARE }.map { it.name }.take(MAX)
            val parsed = PersianTimeParser(context.settings.toParserPrefs()).parse(message, context.now)
            // «ماهانه» in «گزارش ماهانه» names the task; it is not a time.
            val names = tasks.mapNotNull { context.tasks.getOrNull(it - 1)?.title } + habits
            fun inName(text: String) = names.any { Matcher.normalize(it).contains(Matcher.normalize(text)) }
            fun spans(kind: SpanKind) = parsed.spans.filter { it.kind == kind && !inName(message.substring(it.start, it.end)) }.sortedBy { it.start }
                .joinToString(" ") { message.substring(it.start, it.end).trim() }.ifBlank { null }
            return Hints(tasks, habits, spans(SpanKind.TIME), spans(SpanKind.RECURRENCE), parsed.estimate?.toMinutes()?.toInt())
        }
    }
}
