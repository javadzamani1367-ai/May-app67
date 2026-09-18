package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.db.SnippetDao
import ir.ilam.inspection.data.db.SnippetEntity
import kotlinx.coroutines.flow.Flow

/** The saved phrases behind the star on a text field. */
class SnippetRepository(private val dao: SnippetDao) {

    fun observe(fieldKey: String): Flow<List<SnippetEntity>> = dao.observeFor(fieldKey)

    /**
     * Saving the same phrase twice is not an error, it is what happens when the
     * star is tapped again — the unique index absorbs it and the entry simply
     * moves back to the top of the list.
     */
    suspend fun save(fieldKey: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dao.insert(SnippetEntity(fieldKey = fieldKey, text = trimmed))
    }

    suspend fun markUsed(snippet: SnippetEntity) =
        dao.touch(snippet.id, System.currentTimeMillis())

    suspend fun delete(snippet: SnippetEntity) = dao.delete(snippet.id)
}

/** The field a saved phrase belongs to. Keys are stored, so never rename one. */
object SnippetFields {
    const val DISPATCH_NOTE = "dispatch_note"
    const val VISIT_DESCRIPTION = "visit_description"
    const val VISIT_ACTIONS = "visit_actions"
    const val ATTENDEE_NAME = "attendee_name"
    const val ATTENDEE_POSITION = "attendee_position"
    const val DEVICE_NOTE = "device_note"
    const val ATTACHMENT_TITLE = "attachment_title"
}
