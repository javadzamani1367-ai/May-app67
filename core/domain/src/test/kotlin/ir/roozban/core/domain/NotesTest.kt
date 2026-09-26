package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.testing.FakeNoteRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NotesTest {
    private val h = Harness()
    private val repo = FakeNoteRepository()
    private val notes = NoteUseCases(repo, h.clock)

    @Test
    fun `notes are saved, titled from their text, and empty ones are dropped`() = runTest {
        val note = notes.create()
        notes.save(note.id, "", "\n  جلسه با تیم فروش\nموارد: قیمت‌ها")
        val saved = repo.get(note.id)!!
        assertThat(NoteUseCases.displayTitle(saved)).isEqualTo("جلسه با تیم فروش")

        val empty = notes.create()
        notes.discardIfEmpty(empty.id)
        notes.discardIfEmpty(note.id)
        assertThat(repo.notes.value.keys).containsExactly(note.id)

        val undo = notes.delete(saved)
        assertThat(repo.notes.value).isEmpty()
        undo()
        assertThat(repo.get(note.id)?.body).contains("قیمت‌ها")
    }
}
