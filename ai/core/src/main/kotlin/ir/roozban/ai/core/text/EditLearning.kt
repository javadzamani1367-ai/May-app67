package ir.roozban.ai.core.text

/**
 * What the user's own edits of dictated text teach the spell corrector: a dictated word they
 * replaced by a similar one («پاتشاه» → «پادشاه») is a correction to apply next time, and a
 * Persian word they typed in is one of their own words, never to be "corrected".
 */
object EditLearning {
    data class Lesson(val corrections: Map<String, String>, val ownWords: Set<String>)

    /** Compares the text as dictated ([before]) with the text after the user's edits ([after]). */
    fun learn(before: String, after: String): Lesson {
        val a = words(before)
        val b = words(after)
        if (a.isEmpty() || b.isEmpty() || a.size * b.size > MAX_CELLS) return Lesson(emptyMap(), emptySet())
        // Longest common subsequence of words; what lies between two matches was edited.
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val corrections = HashMap<String, String>()
        val own = HashSet<String>()
        var i = 0
        var j = 0
        val removed = ArrayList<String>()
        val added = ArrayList<String>()
        fun flush() {
            // One word swapped for one similar word: a correction. Anything else: new words typed.
            if (removed.size == 1 && added.size == 1 && similar(removed[0], added[0])) {
                corrections[PersianLexicon.bare(removed[0])] = added[0]
            }
            if (removed.size != added.size || removed.isEmpty()) added.forEach { own += PersianLexicon.bare(it) }
            else added.forEachIndexed { k, w -> if (!similar(removed[k], w)) own += PersianLexicon.bare(w) }
            removed.clear()
            added.clear()
        }
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> {
                    flush()
                    i++
                    j++
                }
                j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> added += b[j++]
                else -> removed += a[i++]
            }
        }
        flush()
        own.removeAll { it.length < 2 }
        return Lesson(corrections, own)
    }

    private fun words(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

    /** Close enough to be a misspelling of it: at most a third of the letters differ. */
    private fun similar(x: String, y: String): Boolean {
        val a = PersianLexicon.bare(x)
        val b = PersianLexicon.bare(y)
        if (a == b) return true
        val d = distance(a, b)
        return d <= maxOf(1, minOf(a.length, b.length) / 3)
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    private val WORD = Regex("[\\u0621-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC\\u0622\\u200C]+")

    /** Longer texts are skipped (the table would be too large); edits are usually local anyway. */
    private const val MAX_CELLS = 4_000_000
}
