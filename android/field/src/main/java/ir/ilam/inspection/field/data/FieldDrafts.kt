package ir.ilam.inspection.field.data

import ir.ilam.inspection.field.data.db.FieldDao
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.util.FileStore
import java.io.File
import java.security.MessageDigest

/**
 * Items being filled in on this phone. Every edit is saved at once, so a call,
 * a dead battery or a closed app never loses what was typed; an item only
 * joins the send queue when the user finishes it.
 */
class FieldDrafts(private val dao: FieldDao, private val files: FileStore) {

    suspend fun create(kind: FieldKind): FieldItemEntity {
        val now = System.currentTimeMillis()
        val item = FieldItemEntity(kind = kind.code, createdAt = now, updatedAt = now, syncState = SyncState.DRAFT.code)
        dao.saveItem(item)
        return item
    }

    suspend fun item(id: String): FieldItemEntity? = dao.item(id)

    /** Saves an edit. Only a draft or an item not yet sent may change on the phone. */
    suspend fun save(item: FieldItemEntity): FieldItemEntity {
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        dao.saveItem(updated)
        return updated
    }

    suspend fun files(itemId: String): List<FieldFileEntity> = dao.files(itemId)

    /** A new file for this item, under the app's private storage: `field/<item>/<id>.<ext>`. */
    fun newFile(itemId: String, extension: String): File {
        val folder = File(files.resolve(FOLDER), itemId).apply { mkdirs() }
        return File(folder, "${java.util.UUID.randomUUID()}.$extension")
    }

    /**
     * Records a finished file against its item, with the fingerprint the
     * server will check it against. The path is stored relative, never absolute.
     */
    suspend fun attach(
        itemId: String,
        file: File,
        role: Int,
        mime: String,
        capturedAt: Long,
        latitude: Double?,
        longitude: Double?,
        accuracy: Double?
    ): FieldFileEntity? {
        if (!file.exists() || file.length() == 0L) return null
        val entity = FieldFileEntity(
            id = file.nameWithoutExtension,
            itemId = itemId,
            role = role,
            mime = mime,
            size = file.length(),
            sha256 = sha256(file),
            path = files.relativize(file),
            capturedAt = capturedAt,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy
        )
        dao.saveFile(entity)
        return entity
    }

    suspend fun removeFile(file: FieldFileEntity) {
        file.path?.let { files.deleteQuietly(it) }
        dao.deleteFile(file.id)
    }

    /** Hands a finished item to the send queue. */
    suspend fun finish(item: FieldItemEntity) {
        save(item.copy(syncState = SyncState.PENDING.code, syncError = null))
    }

    /** Drops a draft and everything it owned. Sent items are never deleted here. */
    suspend fun discard(item: FieldItemEntity) {
        if (SyncState.of(item.syncState) != SyncState.DRAFT) return
        dao.files(item.id).forEach { file -> file.path?.let { files.deleteQuietly(it) } }
        dao.deleteItem(item.id)
    }

    companion object {
        const val FOLDER = "field"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
