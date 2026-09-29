package ir.roozban.ai.runtime

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.ai.core.text.EditLearning
import ir.roozban.ai.core.text.PersianLexicon
import ir.roozban.ai.core.text.SpellCorrector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spelling correction for dictated text, on the phone: the Persian word list ([ModelManager.spellDir],
 * downloaded once) plus what the user taught it by editing dictated text (their corrections and
 * their own words, kept in the app's private storage). The word list takes about 35 MB of memory,
 * so it is loaded when dictation starts and let go when it ends.
 */
@Singleton
class Spelling @Inject constructor(
    @ApplicationContext context: Context,
    private val models: ModelManager,
) {
    private val prefs = context.getSharedPreferences("spelling", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var lexicon: PersianLexicon? = null
    private var users = 0

    val installed: Boolean get() = models.spellInstalled()

    /** Loads the word list for a dictation; pair with [release]. */
    suspend fun acquire() {
        mutex.withLock {
            users++
            if (lexicon == null && installed) {
                lexicon = withContext(Dispatchers.IO) { runCatching { PersianLexicon.load(models.spellDir) }.getOrNull() }
            }
        }
    }

    suspend fun release() {
        mutex.withLock {
            users = maxOf(0, users - 1)
            if (users == 0) lexicon = null
        }
    }

    /** [text] with its spelling fixed; unchanged when the word list is missing. */
    suspend fun correct(text: String): String {
        val lex = mutex.withLock { lexicon }
        val learned = learned()
        if (lex == null) return applyLearned(text, learned)
        return withContext(Dispatchers.Default) { SpellCorrector(lex, ownWords(), learned).correct(text) }
    }

    /** Remembers what the user's edit of dictated text ([dictated] → [edited]) teaches. */
    fun learn(dictated: String, edited: String) {
        if (dictated == edited) return
        val lesson = EditLearning.learn(dictated, edited)
        if (lesson.corrections.isEmpty() && lesson.ownWords.isEmpty()) return
        val corrections = (learned() + lesson.corrections).entries.toList().takeLast(MAX_CORRECTIONS)
        val own = (ownWords() + lesson.ownWords).toList().takeLast(MAX_OWN_WORDS)
        prefs.edit {
            putString(KEY_CORRECTIONS, corrections.joinToString("\n") { "${it.key}\t${it.value}" })
            putString(KEY_OWN, own.joinToString("\n"))
        }
    }

    private fun learned(): Map<String, String> =
        prefs.getString(KEY_CORRECTIONS, null).orEmpty().lineSequence().mapNotNull { l ->
            val tab = l.indexOf('\t')
            if (tab > 0) l.substring(0, tab) to l.substring(tab + 1) else null
        }.toMap(LinkedHashMap())

    private fun ownWords(): Set<String> =
        prefs.getString(KEY_OWN, null).orEmpty().lineSequence().filter { it.isNotBlank() }.toCollection(LinkedHashSet())

    /** Without the word list, the user's own corrections still apply. */
    private fun applyLearned(text: String, learned: Map<String, String>): String {
        if (learned.isEmpty()) return text
        return WORD.replace(text) { m -> learned[PersianLexicon.bare(m.value)] ?: m.value }
    }

    private companion object {
        const val KEY_CORRECTIONS = "corrections"
        const val KEY_OWN = "own_words"
        const val MAX_CORRECTIONS = 2000
        const val MAX_OWN_WORDS = 5000
        val WORD = Regex("[\\u0621-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC\\u0622\\u200C]+")
    }
}
