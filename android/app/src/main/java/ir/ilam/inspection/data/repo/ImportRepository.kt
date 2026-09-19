package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.util.FileStore
import java.io.File

/**
 * Writing a case that came from somewhere else into this phone's database.
 *
 * Only a manager's phone does this, pulling what every expert has sent to the
 * server. It is deliberately not part of [ReportRepository]: that class is
 * about a case being *worked on* — codes generated, steps completed, status
 * gates enforced — and none of those rules apply to a case arriving finished
 * from another device. Mixing them would mean every gate had to grow an
 * exception for imports.
 */
class ImportRepository(private val db: AppDatabase, private val files: FileStore) {

    /**
     * Writes the case, replacing its children wholesale.
     *
     * Wholesale because a device or an attendee deleted on the expert's phone
     * has to disappear here too, and merging row by row would keep it for
     * ever. The children are keyed by the same UUIDs on both sides, so nothing
     * is duplicated by the round trip.
     *
     * Returns false when the copy already here is newer, which happens when the
     * manager has just decided on a case that the server has not caught up
     * with: overwriting would throw their own decision away.
     */
    suspend fun save(detail: ReportDetail): Boolean {
        val incoming = detail.report
        val existing = db.reportDao().byId(incoming.id)
        if (existing != null && existing.updatedAt > incoming.updatedAt) return false

        // `synced_at` belongs to whichever phone sent the case, not to this
        // one. Keeping what is already here means a case this manager pushed
        // is not suddenly considered unsent.
        db.reportDao().upsertAll(listOf(incoming.copy(syncedAt = existing?.syncedAt)))

        db.deviceDao().deleteFor(incoming.id)
        db.attendeeDao().deleteFor(incoming.id)
        db.mediaDao().deleteFor(incoming.id)
        db.attachmentDao().deleteFor(incoming.id)

        if (detail.devices.isNotEmpty()) db.deviceDao().upsertAll(detail.devices)
        if (detail.attendees.isNotEmpty()) db.attendeeDao().upsertAll(detail.attendees)
        if (detail.media.isNotEmpty()) db.mediaDao().upsertAll(detail.media)
        if (detail.attachments.isNotEmpty()) db.attachmentDao().upsertAll(detail.attachments)
        return true
    }

    /**
     * The files of an imported case that are not on this phone yet.
     *
     * A row whose file is missing is not an error — the case arrives as rows
     * first and its photos follow, and on a bad connection the photos may
     * follow tomorrow. This is the list to fetch, in the order a reader would
     * want them: photos before documents.
     */
    fun missingFiles(detail: ReportDetail): List<PendingFile> {
        val media = detail.media.map { PendingFile("media", it.id, it.filePath) }
        val attachments = detail.attachments.map { PendingFile("attachment", it.id, it.filePath) }
        return (media + attachments).filterNot { present(it.relativePath) }
    }

    fun target(relativePath: String): File = files.resolve(relativePath)

    private fun present(relativePath: String): Boolean =
        relativePath.isNotBlank() && files.resolve(relativePath).let { it.exists() && it.length() > 0 }

    /** One file to fetch: which table it belongs to, its row, and where it goes. */
    data class PendingFile(val kind: String, val id: String, val relativePath: String)
}
