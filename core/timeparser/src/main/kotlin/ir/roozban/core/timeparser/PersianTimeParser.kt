package ir.roozban.core.timeparser

import java.time.Duration
import java.time.LocalDateTime

/**
 * Rule-based parser for Persian time expressions in free text, e.g.
 * «جلسه با علی پس‌فردا عصر» → title «جلسه با علی», time = the day after tomorrow at 17:00.
 *
 * Pipeline: [Tokenizer] (normalization with stable offsets) → [ComponentMatcher] (AST components)
 * → clustering of adjacent components → [Resolver] (deterministic date arithmetic).
 *
 * Thread-safe and allocation-light; safe to call on every keystroke.
 */
class PersianTimeParser(prefs: TimeParserPrefs = TimeParserPrefs()) {

    private val resolver = Resolver(prefs)

    fun parse(input: String, now: LocalDateTime): ParseResult {
        val tokens = Tokenizer.tokenize(input)
        val all = ComponentMatcher(tokens).matchAll()

        val durations = all.filterIsInstance<DurationComp>()
        val timeComps = filterWeakClocks(all.filter { it !is DurationComp }, tokens)
        val best = clusters(timeComps, tokens).maxByOrNull { it.size }

        val spans = ArrayList<MatchedSpan>()
        var time: ResolvedTime? = null
        var recurrence: RecurrenceSpec? = null
        var confidence = 0f

        if (best != null) {
            val resolution = resolver.resolve(best, now)
            time = resolution.time
            recurrence = resolution.recurrence
            confidence = resolution.confidence
            if (time != null || recurrence != null) {
                val from = extendOverPrefixFillers(best.first().from, tokens, durations)
                val kind = if (recurrence != null) SpanKind.RECURRENCE else SpanKind.TIME
                spans += MatchedSpan(tokens[from].start, tokens[best.last().to - 1].end, kind)
            }
        }

        val duration = durations.firstOrNull { d -> spans.none { overlaps(it, tokens, d) } }
        if (duration != null) {
            spans += MatchedSpan(tokens[duration.from].start, tokens[duration.to - 1].end, SpanKind.DURATION)
        }
        spans.sortBy { it.start }

        return ParseResult(
            input = input,
            time = time,
            recurrence = recurrence,
            estimate = duration?.let { Duration.ofMinutes(it.minutes) },
            spans = spans,
            confidence = confidence,
            title = titleWithout(input, spans),
        )
    }

    private fun overlaps(span: MatchedSpan, tokens: List<Token>, c: Component): Boolean =
        tokens[c.from].start < span.end && tokens[c.to - 1].end > span.start

    /** True when every token in [from, to) may sit inside a time expression. */
    private fun onlyFillers(tokens: List<Token>, from: Int, to: Int): Boolean =
        to - from <= 3 && (from until to).all { tokens[it].text in Lexicon.FILLERS }

    /**
     * A bare number («فردا ۵») is a time only when context makes it one: next to a part of day
     * («۵ عصر»), carrying minutes («۵ و نیم») next to a date, or ending the text right after a date
     * («جلسه فردا ۵»). Otherwise «فردا ۳ نان بخر» would schedule for 15:00.
     */
    private fun filterWeakClocks(comps: List<Component>, tokens: List<Token>): List<Component> =
        comps.filterIndexed { k, c ->
            if (c !is ClockComp || !c.weak) return@filterIndexed true
            val prev = comps.getOrNull(k - 1)?.takeIf { onlyFillers(tokens, it.to, c.from) }
            val next = comps.getOrNull(k + 1)?.takeIf { onlyFillers(tokens, c.to, it.from) }
            val atEnd = c.to == tokens.size || tokens[c.to].type == TokenType.PUNCT
            val prevAnchors = prev != null && (prev.isDateLike || prev is RecurrenceComp)
            when {
                prev is PartOfDayComp || next is PartOfDayComp -> true
                prevAnchors && (atEnd || c.minute != 0 || next != null) -> true
                else -> false
            }
        }

    private fun clusters(comps: List<Component>, tokens: List<Token>): List<List<Component>> {
        val result = ArrayList<MutableList<Component>>()
        for (c in comps) {
            val current = result.lastOrNull()
            if (current != null && onlyFillers(tokens, current.last().to, c.from)) current += c else result += mutableListOf(c)
        }
        return result
    }

    /** Includes «تا فردا»'s «تا» in the span, without eating into a preceding duration («۲ ساعت شنبه»). */
    private fun extendOverPrefixFillers(from: Int, tokens: List<Token>, durations: List<DurationComp>): Int {
        var i = from
        repeat(2) {
            val prev = i - 1
            val free = prev >= 0 && durations.none { prev in it.from until it.to }
            if (free && tokens[prev].text in Lexicon.PREFIX_FILLERS) i-- else return i
        }
        return i
    }

    private fun titleWithout(input: String, spans: List<MatchedSpan>): String {
        if (spans.isEmpty()) return input.trim()
        val sb = StringBuilder()
        var last = 0
        for (s in spans) {
            // «جلسه‌ی فردا» → «جلسه»: drop an ezafe left dangling right before the removed span.
            sb.append(input.substring(last, s.start).replace(TRAILING_EZAFE, "")).append(' ')
            last = s.end
        }
        sb.append(input, last, input.length)
        return sb.toString()
            .replace(WHITESPACE, " ")
            .trim { it.isWhitespace() || it in TRIM_CHARS }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val TRAILING_EZAFE = Regex("\u200C[یي]\\s*$")
        val TRIM_CHARS = charArrayOf('،', ',', '-', '.', ':', '؛', ';')
    }
}
