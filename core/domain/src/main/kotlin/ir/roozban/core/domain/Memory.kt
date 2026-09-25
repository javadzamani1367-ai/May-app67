package ir.roozban.core.domain

import ir.roozban.core.model.FactSource
import ir.roozban.core.model.MemoryFact
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

interface MemoryRepository {
    /** Pinned first, then most recently updated. */
    fun observeFacts(): Flow<List<MemoryFact>>

    suspend fun all(): List<MemoryFact>

    suspend fun get(id: String): MemoryFact?

    suspend fun byKey(key: String): MemoryFact?

    suspend fun upsert(fact: MemoryFact)

    suspend fun delete(id: String)

    suspend fun clear()

    /** Removes unpinned facts of [source] whose key is not in [keep]. */
    suspend fun deleteSourceExcept(source: FactSource, keep: List<String>)
}

/** A fact the app worked out (from statistics or settings). */
data class DerivedFact(val key: String, val text: String, val confidence: Float)

/** What the user and the app put into memory; user statements and pins always win over inference. */
class MemoryUseCases @Inject constructor(
    private val memory: MemoryRepository,
    private val clock: Clock,
) {
    /** Stores something the user said about themselves; the same [key] replaces the earlier statement. */
    suspend fun remember(text: String, key: String? = null): MemoryFact? {
        val clean = text.trim().trimEnd('.', '،', '!').trim().take(MAX_TEXT)
        if (clean.isEmpty()) return null
        val now = Instant.now(clock)
        val factKey = key ?: "user:" + normalize(clean)
        val existing = memory.byKey(factKey)
        val fact = MemoryFact(
            id = existing?.id ?: UUID.randomUUID().toString(),
            key = factKey,
            text = clean,
            source = FactSource.USER,
            confidence = 1f,
            pinned = existing?.pinned ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        memory.upsert(fact)
        return fact
    }

    /** Facts whose text contains every word of [query] (for «فراموش کن که …»). */
    suspend fun find(query: String): List<MemoryFact> {
        val words = normalize(query).split(' ').filter { it.length > 1 }
        if (words.isEmpty()) return emptyList()
        return memory.all().filter { f -> val t = normalize(f.text); words.all { it in t } }
    }

    suspend fun edit(fact: MemoryFact, text: String) {
        val clean = text.trim().trimEnd('.', '،', '!').trim().take(MAX_TEXT)
        if (clean.isEmpty()) return
        // An edited inference becomes the user's own statement, so the next night does not overwrite it.
        memory.upsert(fact.copy(text = clean, source = FactSource.USER, confidence = 1f, updatedAt = Instant.now(clock)))
    }

    suspend fun setPinned(fact: MemoryFact, pinned: Boolean) =
        memory.upsert(fact.copy(pinned = pinned, updatedAt = Instant.now(clock)))

    suspend fun delete(fact: MemoryFact): Undo {
        memory.delete(fact.id)
        return Undo { memory.upsert(fact) }
    }

    suspend fun clearAll(): Undo {
        val before = memory.all()
        memory.clear()
        return Undo { before.forEach { memory.upsert(it) } }
    }

    /**
     * Replaces the facts of [source] (inferred each night, or derived from settings) by key.
     * A key the user stated or pinned is left alone.
     */
    suspend fun replace(source: FactSource, facts: List<DerivedFact>) {
        val now = Instant.now(clock)
        facts.forEach { (key, text, confidence) ->
            val existing = memory.byKey(key)
            if (existing != null && (existing.pinned || existing.source == FactSource.USER)) return@forEach
            if (existing != null && existing.text == text && existing.source == source) return@forEach
            memory.upsert(
                MemoryFact(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    key = key,
                    text = text,
                    source = source,
                    confidence = confidence.coerceIn(0f, 1f),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
        memory.deleteSourceExcept(source, facts.map { it.key })
    }

    companion object {
        const val MAX_TEXT = 200

        /** Arabic letters to Persian, spaces collapsed, zero-width non-joiners as spaces. */
        fun normalize(s: String): String = s.replace('ي', 'ی').replace('ك', 'ک').replace('‌', ' ')
            .replace(Regex("[.،,!؟?]"), " ").trim().replace(Regex("\\s+"), " ")
    }
}
