package ir.ilam.inspection.field.sync

import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi
import org.json.JSONArray
import org.json.JSONObject

/** The field app's endpoints, added to the shared client the way each app adds its own. */

data class FieldProfile(
    val fullName: String,
    val permissions: Int,
    val ampTolerancePct: Int,
    val ampToleranceMinA: Int,
    val purgeAfterSync: Boolean
)

data class PushResult(val trackingCode: String?, val status: Int, val missingFiles: Set<String>)

data class RemoteItem(val id: String, val trackingCode: String?, val status: Int)

data class ItemsPage(val items: List<RemoteItem>, val serverTime: Long)

/** How much of a file the server holds; [hashMismatch] means it threw the file away. */
data class UploadState(val received: Long, val complete: Boolean, val hashMismatch: Boolean)

suspend fun ServerApi.fieldMe(token: String): ApiResult<FieldProfile> = get("field/me", token) { data ->
    val user = data.optJSONObject("user") ?: JSONObject()
    val settings = data.optJSONObject("settings") ?: JSONObject()
    FieldProfile(
        fullName = user.optString("full_name"),
        permissions = user.optInt("permissions"),
        ampTolerancePct = settings.optInt("amp_tolerance_pct", 10),
        ampToleranceMinA = settings.optInt("amp_tolerance_min_a", 5),
        purgeAfterSync = settings.optInt("purge_after_sync", 0) == 1
    )
}

suspend fun ServerApi.pushFieldItem(token: String, item: JSONObject, files: JSONArray): ApiResult<PushResult> =
    post("field/item", JSONObject().put("item", item).put("files", files), token) { data ->
        val missing = data.optJSONArray("missing_files") ?: JSONArray()
        PushResult(
            trackingCode = data.optString("tracking_code").ifBlank { null },
            status = data.optInt("status"),
            missingFiles = (0 until missing.length()).map { missing.optString(it) }.toSet()
        )
    }

suspend fun ServerApi.pullFieldItems(token: String, since: Long): ApiResult<ItemsPage> =
    get("field/items&since=$since", token) { data ->
        val items = data.optJSONArray("items") ?: JSONArray()
        ItemsPage(
            items = (0 until items.length()).mapNotNull { items.optJSONObject(it) }.map {
                RemoteItem(it.optString("id"), it.optString("tracking_code").ifBlank { null }, it.optInt("status"))
            },
            serverTime = data.optLong("server_time")
        )
    }

/** Sends one chunk; an empty [bytes] asks only how far the server has got. */
suspend fun ServerApi.uploadFieldChunk(token: String, fileId: String, offset: Long, bytes: ByteArray): ApiResult<UploadState> {
    val path = if (bytes.isEmpty()) "field/upload&file_id=$fileId" else "field/upload&file_id=$fileId&offset=$offset"
    return postBytes(path, bytes, token) { data ->
        UploadState(
            received = data.optLong("received"),
            complete = data.optBoolean("complete"),
            hashMismatch = data.optString("error") == "hash_mismatch"
        )
    }
}
