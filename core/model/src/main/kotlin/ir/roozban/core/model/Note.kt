package ir.roozban.core.model

import java.time.Instant

/** A free-form note (typed or dictated) from the voice-notes tool. */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
