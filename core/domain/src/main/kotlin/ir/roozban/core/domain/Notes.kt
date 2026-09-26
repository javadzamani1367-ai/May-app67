package ir.roozban.core.domain

import ir.roozban.core.model.Note
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

interface NoteRepository {
    /** Most recently edited first. */
    fun observeNotes(): Flow<List<Note>>

    fun observeNote(id: String): Flow<Note?>

    suspend fun get(id: String): Note?

    suspend fun upsert(note: Note)

    suspend fun delete(id: String)
}

class NoteUseCases @Inject constructor(
    private val notes: NoteRepository,
    private val clock: Clock,
) {
    suspend fun create(): Note {
        val now = Instant.now(clock)
        return Note(UUID.randomUUID().toString(), "", "", now, now).also { notes.upsert(it) }
    }

    /** Saves edits; an unchanged note keeps its date. */
    suspend fun save(id: String, title: String, body: String) {
        val current = notes.get(id) ?: return
        if (current.title == title && current.body == body) return
        notes.upsert(current.copy(title = title, body = body, updatedAt = Instant.now(clock)))
    }

    suspend fun delete(note: Note): Undo {
        notes.delete(note.id)
        return Undo { notes.upsert(note) }
    }

    /** Drops a note that was opened and never written in. */
    suspend fun discardIfEmpty(id: String) {
        val note = notes.get(id) ?: return
        if (note.title.isBlank() && note.body.isBlank()) notes.delete(id)
    }

    companion object {
        /** The list's name for a note: its title, else the start of its text. */
        fun displayTitle(note: Note): String =
            note.title.ifBlank { note.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60).orEmpty() }
    }
}
