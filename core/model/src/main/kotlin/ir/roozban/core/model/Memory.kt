package ir.roozban.core.model

import java.time.Instant

/**
 * Something Roozban knows about the user: said explicitly ([FactSource.USER]),
 * inferred from their history ([FactSource.INFERRED]) or taken from settings.
 *
 * [key] identifies the fact so a newer statement or inference replaces the old one
 * (e.g. `hours`, `estimate:<projectId>`, `pref:<slug>`).
 */
data class MemoryFact(
    val id: String,
    val key: String,
    val text: String,
    val source: FactSource,
    /** 0..1; user statements are 1. */
    val confidence: Float = 1f,
    /** Pinned facts are never overwritten by inference and always reach the assistant. */
    val pinned: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class FactSource { USER, INFERRED, SETTINGS }
