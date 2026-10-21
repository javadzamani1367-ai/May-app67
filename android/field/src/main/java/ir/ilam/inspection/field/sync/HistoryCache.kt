package ir.ilam.inspection.field.sync

import ir.ilam.inspection.field.data.FieldAccount
import ir.ilam.inspection.field.data.FieldPrefs
import ir.ilam.inspection.field.data.History
import ir.ilam.inspection.field.data.HistoryRow
import ir.ilam.inspection.field.data.HistoryTarget
import ir.ilam.inspection.field.data.db.HistoryDao
import ir.ilam.inspection.field.data.db.HistoryKeyEntity
import ir.ilam.inspection.field.data.db.HistoryMetaEntity
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.RouteDownload
import ir.ilam.inspection.sync.ServerApi
import java.io.File

/** Earlier readings for the comparison box, and whether they came live or from the phone's copy. */
data class HistoryLookup(val rows: List<HistoryRow>, val live: Boolean, val copiedAt: Long?)

/**
 * The area's inspection history on the phone. Every sync — on any connection,
 * mobile data included, as the user asked — brings what changed since the last
 * time; the comparison box asks the server first and falls back to this copy.
 * Thermal pictures are kept whole: the last [History.IMAGES_PER_PLATE] of each plate.
 */
class HistoryCache(
    private val dao: HistoryDao,
    private val prefs: FieldPrefs,
    private val account: FieldAccount,
    /** This account's own folder: another account on the phone never sees these pictures. */
    private val imageDir: File
) {

    /** Brings the copy up to date; false if the server could not be reached. */
    suspend fun refresh(api: ServerApi, token: String): Boolean {
        val since = dao.meta(SINCE) ?: 0L
        var after = ""
        var serverTime: Long
        while (true) {
            val page = when (val result = api.fieldHistoryBundle(token, since, after)) {
                is ApiResult.Ok -> result.value
                else -> return false
            }
            val now = System.currentTimeMillis()
            page.keys.forEach { key ->
                if (key.rows.isEmpty()) dao.delete(key.id)
                else dao.save(HistoryKeyEntity(key.id, key.area, key.asset, key.plateKey, key.plate, key.part, History.toJson(key.rows), now))
            }
            serverTime = page.serverTime
            after = page.next ?: break
        }
        dao.setMeta(HistoryMetaEntity(SINCE, serverTime))
        dao.setMeta(HistoryMetaEntity(COPIED_AT, System.currentTimeMillis()))
        keepImages(token)
        return true
    }

    /** Live when the server answers; the phone's copy otherwise. */
    suspend fun lookup(target: HistoryTarget): HistoryLookup {
        val token = account.token()
        if (token != null && target.plateKey.isNotEmpty()) {
            val result = ServerApi(prefs.serverAddress).fieldHistory(token, target)
            if (result is ApiResult.Ok) return HistoryLookup(result.value, live = true, copiedAt = null)
        }
        val copy = dao.find(target.asset, target.plateKey, target.part)
        val rows = copy?.let { History.rows(org.json.JSONArray(it.rowsJson)) }.orEmpty()
        return HistoryLookup(rows, live = false, copiedAt = copy?.fetchedAt ?: dao.meta(COPIED_AT))
    }

    /** The picture of an earlier thermal reading: the kept copy, or fetched now if there is signal. */
    suspend fun image(fileId: String): File? {
        val file = imageFile(fileId)
        if (file.exists()) return file
        val token = account.token() ?: return null
        return file.takeIf { RouteDownload.to(prefs.serverAddress, token, historyImageRoute(fileId), it) }
    }

    private suspend fun keepImages(token: String) {
        val wanted = dao.thermal().flatMap { key ->
            History.rows(org.json.JSONArray(key.rowsJson)).mapNotNull { it.fileId }.take(History.IMAGES_PER_PLATE)
        }.toSet()
        imageDir.mkdirs()
        imageDir.listFiles()?.filter { it.nameWithoutExtension !in wanted }?.forEach { it.delete() }
        for (id in wanted) {
            val file = imageFile(id)
            if (!file.exists() && !RouteDownload.to(prefs.serverAddress, token, historyImageRoute(id), file)) break
        }
    }

    private fun imageFile(fileId: String) = File(imageDir, fileId.filter { it.isLetterOrDigit() || it == '-' } + ".jpg")

    private companion object {
        const val SINCE = "since"
        const val COPIED_AT = "copied_at"
    }
}
