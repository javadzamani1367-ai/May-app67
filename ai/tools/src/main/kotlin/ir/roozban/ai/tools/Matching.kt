package ir.roozban.ai.tools

import ir.roozban.core.calendar.PersianDigits

sealed interface Match<out T> {
    data class Found<T>(val item: T) : Match<T>

    data class Ambiguous<T>(val candidates: List<T>) : Match<T>

    data object None : Match<Nothing>
}

/**
 * Finds the item a model's reference means: `#3`/`3` picks the numbered item, otherwise titles
 * are compared ignoring ی/ک variants, half-spaces and word order.
 */
object Matcher {
    fun <T> find(ref: String, items: List<T>, title: (T) -> String): Match<T> {
        val r = PersianDigits.toAscii(ref.trim())
        NUMBER.matchEntire(r)?.let { m ->
            val n = m.groupValues[1].toInt()
            return items.getOrNull(n - 1)?.let { Match.Found(it) } ?: Match.None
        }
        val key = normalize(r)
        if (key.isEmpty()) return Match.None
        items.filter { normalize(title(it)) == key }.let { exact ->
            if (exact.size == 1) return Match.Found(exact[0])
            if (exact.size > 1) return Match.Ambiguous(exact)
        }
        val words = words(r)
        val scored = items.map { it to score(words, key, title(it)) }.filter { it.second >= THRESHOLD }.sortedByDescending { it.second }
        return when {
            scored.isEmpty() -> Match.None
            scored.size == 1 || scored[0].second - scored[1].second >= MARGIN -> Match.Found(scored[0].first)
            else -> Match.Ambiguous(scored.takeWhile { scored[0].second - it.second < MARGIN }.map { it.first }.take(4))
        }
    }

    private fun score(refWords: Set<String>, refKey: String, title: String): Double {
        val key = normalize(title)
        if (key.contains(refKey) || refKey.contains(key)) return 0.9 + 0.1 * minOf(key.length, refKey.length) / maxOf(key.length, refKey.length)
        val tw = words(title)
        if (tw.isEmpty() || refWords.isEmpty()) return 0.0
        val common = refWords.count { w -> tw.any { it == w || (w.length >= 3 && (it.startsWith(w) || w.startsWith(it))) } }
        return common.toDouble() / maxOf(refWords.size, tw.size).coerceAtLeast(1)
    }

    fun normalize(s: String): String = unify(s).replace(" ", "")

    /** How much of [title] the free text [message] mentions: the share of its words found there. */
    fun mentions(message: String, title: String): Double {
        val tw = words(title)
        if (tw.isEmpty()) return 0.0
        val mw = words(message)
        val hit = tw.count { w -> mw.any { it == w || (minOf(it.length, w.length) >= 3 && (it.startsWith(w) || w.startsWith(it))) } }
        return hit.toDouble() / tw.size
    }

    private fun words(s: String): Set<String> = unify(s).split(' ').filter { it.length > 1 && it !in STOP }.toSet()

    private fun unify(s: String): String = PersianDigits.toAscii(s).lowercase()
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').replace('ة', 'ه').replace('أ', 'ا').replace('إ', 'ا')
        .replace("‌", "").replace(PUNCT, " ").replace(SPACES, " ").trim()

    private val NUMBER = Regex("#?\\s*(\\d{1,4})")
    private val PUNCT = Regex("[\\p{P}\\p{S}]")
    private val SPACES = Regex("\\s+")
    private val STOP = setOf("را", "رو", "به", "از", "با", "و", "که", "در", "برای", "کار", "تسک")
    private const val THRESHOLD = 0.5
    private const val MARGIN = 0.15
}
