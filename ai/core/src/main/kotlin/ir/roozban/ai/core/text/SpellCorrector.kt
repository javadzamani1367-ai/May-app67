package ir.roozban.ai.core.text

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.ln
import kotlin.math.max

/**
 * Persian word and word-pair frequencies (from `tools/spell/build.py`), kept compact for a phone:
 * words in a sorted array, their half-space-free spellings in a second sorted array, and pairs as
 * sorted packed keys with their counts (about 30 MB for 250k words and 1.2M pairs).
 */
class PersianLexicon private constructor(
    private val words: Array<String>,
    private val counts: IntArray,
    private val idBits: Int,
    private val pairKeys: LongArray,
    private val pairCounts: IntArray,
    /** Spellings without half-spaces, sorted, and the id of each one's most frequent written form. */
    private val bareWords: Array<String>,
    private val bareIds: IntArray,
) {
    private val total: Double = counts.sumOf { it.toDouble() }

    fun id(word: String): Int {
        val i = bareWords.binarySearch(bare(word))
        return if (i >= 0) bareIds[i] else -1
    }

    fun word(id: Int): String = words[id]

    fun count(id: Int): Int = if (id < 0) 0 else counts[id]

    fun pairCount(a: Int, b: Int): Int {
        if (a < 0 || b < 0) return 0
        val i = pairKeys.binarySearch((a.toLong() shl idBits) or b.toLong())
        return if (i >= 0) pairCounts[i] else 0
    }

    /** ln P(word), with add-one smoothing for unseen words. */
    fun logP(id: Int): Double = ln((count(id) + 1) / (total + words.size))

    /** ln P(b | a), backing off to P(b) when the pair was never seen. */
    fun logP(a: Int, b: Int): Double {
        val ab = pairCount(a, b)
        return if (ab > 0 && a >= 0) ln(ab.toDouble() / max(1, count(a))) else ln(BACKOFF) + logP(b)
    }

    val size: Int get() = words.size

    companion object {
        private const val BACKOFF = 0.4

        fun bare(word: String): String = if ('\u200C' in word) word.replace("\u200C", "") else word

        fun load(dir: File): PersianLexicon =
            read(File(dir, WORDS_FILE).inputStream(), File(dir, PAIRS_FILE).inputStream())

        const val WORDS_FILE = "fa_words.txt.gz"
        const val PAIRS_FILE = "fa_pairs.txt.gz"

        fun read(wordsIn: InputStream, pairsIn: InputStream): PersianLexicon {
            val list = ArrayList<Pair<String, Int>>(260_000)
            reader(wordsIn).useLines { lines ->
                lines.forEach { line ->
                    val sp = line.lastIndexOf(' ')
                    if (sp > 0) line.substring(sp + 1).toIntOrNull()?.let { list += line.substring(0, sp) to it }
                }
            }
            list.sortBy { it.first }
            // Keep one entry per spelling (the file should not repeat one, but be safe).
            val unique = ArrayList<Pair<String, Int>>(list.size)
            for (e in list) if (unique.isEmpty() || unique.last().first != e.first) unique += e
            val words = Array(unique.size) { unique[it].first }
            val counts = IntArray(unique.size) { unique[it].second }
            list.clear()
            // Half-space-free spelling → its most frequent form.
            val bareOrder = words.indices.sortedWith(compareBy<Int> { bare(words[it]) }.thenByDescending { counts[it] })
            val bw = ArrayList<String>(words.size)
            val bi = IntArray(words.size)
            var nb = 0
            for (i in bareOrder) {
                val b = bare(words[i])
                if (nb > 0 && bw[nb - 1] == b) continue
                bw += if (b == words[i]) words[i] else b
                bi[nb++] = i
            }
            val idBits = 32 - Integer.numberOfLeadingZeros(maxOf(1, words.size))
            // Pairs: parsed into packed (key, line number) so a primitive sort orders them.
            var keys = LongArray(1 shl 20)
            var values = IntArray(1 shl 20)
            var n = 0
            reader(pairsIn).useLines { lines ->
                lines.forEach { line ->
                    val a = line.indexOf(' ')
                    val b = line.lastIndexOf(' ')
                    if (a <= 0 || b <= a) return@forEach
                    val x = words.binarySearch(line.substring(0, a))
                    val y = words.binarySearch(line.substring(a + 1, b))
                    if (x < 0 || y < 0) return@forEach
                    val c = line.substring(b + 1).toIntOrNull() ?: return@forEach
                    if (n == keys.size) {
                        keys = keys.copyOf(n * 2)
                        values = values.copyOf(n * 2)
                    }
                    keys[n] = (x.toLong() shl idBits) or y.toLong()
                    values[n] = c
                    n++
                }
            }
            val rowBits = 32 - Integer.numberOfLeadingZeros(maxOf(1, n))
            val sortedKeys = LongArray(n)
            val sortedValues = IntArray(n)
            if (2 * idBits + rowBits <= 63) {
                val packed = LongArray(n) { (keys[it] shl rowBits) or it.toLong() }
                packed.sort()
                val mask = (1L shl rowBits) - 1
                for (i in 0 until n) {
                    val row = (packed[i] and mask).toInt()
                    sortedKeys[i] = keys[row]
                    sortedValues[i] = values[row]
                }
            } else {
                val order = (0 until n).sortedBy { keys[it] }
                for (i in 0 until n) {
                    sortedKeys[i] = keys[order[i]]
                    sortedValues[i] = values[order[i]]
                }
            }
            return PersianLexicon(words, counts, idBits, sortedKeys, sortedValues, bw.toTypedArray(), bi.copyOf(nb))
        }

        private fun reader(input: InputStream): BufferedReader = GZIPInputStream(input, 1 shl 16).bufferedReader()
    }
}

/**
 * Fixes misspelt words in dictated Persian: a word that is not Persian is replaced by the likeliest
 * nearby word, judged by how common it is and how well it fits the words around it; a real word is
 * swapped only for a same-sounding spelling (ز/ذ/ض/ظ، س/ص/ث، ت/ط، ق/غ، ه/ح، ا/ع) that the context
 * clearly prefers. Words the user wrote themselves ([personal]) are never changed, and [learned]
 * corrections the user made before are applied first.
 */
class SpellCorrector(
    private val lexicon: PersianLexicon,
    private val personal: Set<String> = emptySet(),
    private val learned: Map<String, String> = emptyMap(),
    private val params: Params = Params(),
) {
    data class Params(
        /** Cost of one letter edit, in log-probability units. */
        val editCost: Double = 3.5,
        /** Cost of swapping same-sounding letters. */
        val soundCost: Double = 1.5,
        /**
         * How likely an unknown word is to be right anyway (a name, a rare word): a candidate must
         * beat an unseen word's probability plus this.
         */
        val unknownBonus: Double = 5.0,
        /** How much better a candidate must score to replace a word that exists. */
        val knownMargin: Double = 4.0,
        /** Words seen fewer times than this count as unknown. */
        val minCount: Int = 3,
        /** Weight of the context (the neighbouring words). */
        val contextWeight: Double = 1.0,
    )

    fun correct(text: String): String {
        val tokens = TOKEN.findAll(text).map { it.value }.toMutableList()
        if (tokens.isEmpty()) return text
        val isWord = tokens.map { PERSIAN_WORD.matches(it) }
        val out = tokens.toMutableList()
        for (i in tokens.indices) {
            if (!isWord[i]) continue
            val original = tokens[i]
            learned[PersianLexicon.bare(original)]?.let {
                out[i] = it
                continue
            }
            if (PersianLexicon.bare(original) in personal || original.length < 2) continue
            val prev = previousWord(out, isWord, i)
            val next = nextWord(tokens, isWord, i)
            out[i] = best(original, prev, next)
        }
        return out.joinToString("")
    }

    private fun previousWord(tokens: List<String>, isWord: List<Boolean>, i: Int): Int {
        // Only the word right before (across a single space) is context.
        if (i >= 2 && isWord[i - 2] && tokens[i - 1] == " ") return lexicon.id(tokens[i - 2])
        return -1
    }

    private fun nextWord(tokens: List<String>, isWord: List<Boolean>, i: Int): Int {
        if (i + 2 < tokens.size && isWord[i + 2] && tokens[i + 1] == " ") return lexicon.id(tokens[i + 2])
        return -1
    }

    private fun score(id: Int, prev: Int, next: Int): Double {
        var s = lexicon.logP(id)
        if (prev >= 0) s += params.contextWeight * (lexicon.logP(prev, id) - lexicon.logP(id))
        if (next >= 0) s += params.contextWeight * (lexicon.logP(id, next) - lexicon.logP(next))
        return s
    }

    private fun best(original: String, prev: Int, next: Int): String {
        val self = lexicon.id(original)
        val known = lexicon.count(self) >= params.minCount
        val selfScore = if (known) score(self, prev, next) else score(-1, prev, next) + params.unknownBonus
        var bestId = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for ((candidate, cost) in candidates(PersianLexicon.bare(original), onlySound = known)) {
            val id = lexicon.id(candidate)
            if (id < 0 || id == self || lexicon.count(id) < params.minCount) continue
            val s = score(id, prev, next) - cost
            if (s > bestScore) {
                bestScore = s
                bestId = id
            }
        }
        // A known word gets its usual half-space («میروم» → «می‌روم»), but a half-space already
        // there stays: colloquial text often drops it, which does not make it wrong.
        val usual = if (known && '\u200C' !in original) lexicon.word(self) else original
        return when {
            bestId < 0 -> usual
            !known -> if (bestScore > selfScore) lexicon.word(bestId) else original
            bestScore > selfScore + params.knownMargin -> lexicon.word(bestId)
            else -> usual
        }
    }

    /** Spellings near [w] with their cost; [onlySound] keeps to same-sounding letter swaps. */
    private fun candidates(w: String, onlySound: Boolean): Sequence<Pair<String, Double>> = sequence {
        // Same-sounding letters, one or two swaps.
        val sound = soundSwaps(w)
        sound.forEach { yield(it to params.soundCost) }
        if (onlySound) return@sequence
        sound.forEach { s -> soundSwaps(s).forEach { yield(it to 2 * params.soundCost) } }
        // One letter edit (dropped, added, swapped or wrong letter), possibly with a same-sounding swap.
        val c = params.editCost
        val edits = sequence {
            for (i in w.indices) {
                yield(w.removeRange(i, i + 1))
                if (i + 1 < w.length) yield(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2))
                for (a in ALPHABET) if (a != w[i]) yield(w.substring(0, i) + a + w.substring(i + 1))
            }
            for (i in 0..w.length) for (a in ALPHABET) yield(w.substring(0, i) + a + w.substring(i))
        }
        for (e in edits) {
            yield(e to c)
            soundSwaps(e).forEach { yield(it to c + params.soundCost) }
        }
        // Two wrong letters («فبس» for «قبض»): only for short words, where it stays cheap.
        if (w.length <= MAX_DOUBLE_LENGTH) {
            for (i in w.indices) for (j in i + 1 until w.length) for (a in ALPHABET) {
                if (a == w[i]) continue
                val once = w.substring(0, i) + a + w.substring(i + 1)
                for (b in ALPHABET) if (b != w[j]) yield(once.substring(0, j) + b + once.substring(j + 1) to 2 * c)
            }
        }
    }

    private fun soundSwaps(w: String): List<String> {
        val out = ArrayList<String>()
        for (i in w.indices) {
            val group = SOUND[w[i]] ?: continue
            for (a in group) if (a != w[i]) out += w.substring(0, i) + a + w.substring(i + 1)
        }
        return out
    }

    companion object {
        private val TOKEN = Regex("[\\u0621-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC\\u0622\\u200C]+|\\s+|[^\\s\\u0621-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC\\u0622\\u200C]+")
        private val PERSIAN_WORD = Regex("[\\u0621-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC\\u0622\\u200C]+")
        private const val MAX_DOUBLE_LENGTH = 7
        private const val ALPHABET = "آابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهیئءأؤ"
        private val SOUND: Map<Char, String> = buildMap {
            for (g in listOf("زذضظ", "سصث", "تط", "قغ", "هح", "اعآ", "ئی", "ءئ")) for (ch in g) put(ch, (get(ch) ?: "") + g)
        }
    }
}
