package ir.ilam.inspection.sync

import fi.iki.elonen.NanoHTTPD
import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.repo.ReportRepository
import ir.ilam.inspection.util.FileStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

const val SYNC_PORT = 8765

/**
 * The phone serves; the Windows archive pulls. The app never talks to "Windows"
 * — it answers on a port, and whatever pulls from it can be a laptop today over
 * Wi-Fi, the same laptop over `adb forward` tomorrow, or a server later.
 *
 * Endpoints:
 *   GET  /ping                 device id, expert code, schema version
 *   GET  /manifest?since=<ts>  ids and updated_at that changed
 *   GET  /report/<id>          the whole case as JSON
 *   GET  /media/<id>           the raw file
 *   POST /ack                  ids received; synced_at moves forward
 */
class SyncServer(
    private val database: AppDatabase,
    private val reports: ReportRepository,
    private val files: FileStore,
    private val deviceId: String,
    private val expertCode: String,
    private val pairingCode: String
) : NanoHTTPD(SYNC_PORT) {

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.trimEnd('/')
        if (path != "/ping" && !isAuthorised(session)) {
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "pairing_required"))
        }
        return runCatching {
            when {
                path == "/ping" -> ping()
                path == "/manifest" -> manifest(session)
                path.startsWith("/report/") -> report(path.removePrefix("/report/"))
                path.startsWith("/media/") -> media(path.removePrefix("/media/"))
                path == "/ack" && session.method == Method.POST -> ack(session)
                else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "not_found"))
            }
        }.getOrElse { error ->
            json(
                Response.Status.INTERNAL_ERROR,
                JSONObject().put("error", error.message ?: "internal_error")
            )
        }
    }

    /** The six digit code shown on the phone and typed into the archive. */
    private fun isAuthorised(session: IHTTPSession): Boolean {
        val header = session.headers[PAIRING_HEADER]
        val parameter = session.parameters[PAIRING_PARAMETER]?.firstOrNull()
        return header == pairingCode || parameter == pairingCode
    }

    private fun ping(): Response = json(
        Response.Status.OK,
        JSONObject()
            .put("device_id", deviceId)
            .put("expert_code", expertCode)
            .put("schema_version", AppDatabase.SCHEMA_VERSION)
            .put("app", "crypto-inspection")
    )

    private fun manifest(session: IHTTPSession): Response {
        val since = session.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val rows = runBlocking { database.reportDao().manifestSince(since) }
        val array = JSONArray()
        rows.forEach {
            array.put(JSONObject().put("id", it.id).put("updated_at", it.updatedAt))
        }
        return json(Response.Status.OK, JSONObject().put("reports", array))
    }

    private fun report(id: String): Response {
        val detail = runBlocking { reports.detail(id) }
            ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "unknown_report"))
        return json(Response.Status.OK, SyncPayload.report(detail))
    }

    private fun media(id: String): Response {
        runBlocking { database.mediaDao().byId(id) }?.let {
            return streamFile(it.filePath, null)
        }
        runBlocking { database.attachmentDao().byId(id) }?.let {
            return streamFile(it.filePath, it.mimeType)
        }
        return json(Response.Status.NOT_FOUND, JSONObject().put("error", "unknown_media"))
    }

    private fun streamFile(relativePath: String, mimeType: String?): Response {
        val file = files.resolve(relativePath)
        if (!file.exists()) {
            return json(Response.Status.NOT_FOUND, JSONObject().put("error", "missing_file"))
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            mimeType ?: guessMime(file.extension),
            file.inputStream(),
            file.length()
        )
    }

    private fun ack(session: IHTTPSession): Response {
        val body = HashMap<String, String>()
        session.parseBody(body)
        val payload = JSONObject(body["postData"] ?: "{}")
        val array = payload.optJSONArray("ids") ?: JSONArray()
        val ids = (0 until array.length()).map { array.getString(it) }
        val timestamp = System.currentTimeMillis()
        runBlocking { database.reportDao().markSynced(ids, timestamp) }
        return json(
            Response.Status.OK,
            JSONObject().put("acknowledged", ids.size).put("synced_at", timestamp)
        )
    }

    private fun json(status: Response.Status, payload: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", payload.toString())

    private fun guessMime(extension: String): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    private companion object {
        const val PAIRING_HEADER = "x-pair-code"
        const val PAIRING_PARAMETER = "code"
    }
}
