package ir.ilam.inspection.sync

import ir.ilam.inspection.data.model.ReportDetail
import org.json.JSONObject

/**
 * The case itself travelling to and from the server.
 *
 * Extensions on [ServerApi], like the manager's calls, so the client class
 * stays one screen long and each family of calls can be read on its own.
 *
 * The wire format is [SyncPayload] — the same JSON the Windows archive
 * receives. One format for both receivers means a schema change cannot reach
 * one and miss the other.
 */

/** One id the server holds, and when it last changed there. */
data class RemoteCase(val id: String, val updatedAt: Long, val trackingCode: String)

/** A file the server has a row for but no bytes. */
data class MissingFile(val kind: String, val id: String)

/**
 * Sends one case up. The answer says which of its files the server still
 * lacks, so a sync uploads each photo once instead of all of them every time.
 */
suspend fun ServerApi.pushReport(
    token: String,
    detail: ReportDetail,
    deviceCode: String
): ApiResult<List<MissingFile>> = post(
    path = "sync/report",
    token = token,
    body = JSONObject()
        .put("report", SyncPayload.report(detail))
        .put("device_code", deviceCode)
) { data ->
    val array = data.optJSONArray("missing_files")
    (0 until (array?.length() ?: 0)).mapNotNull { index ->
        array?.optJSONObject(index)?.let { row ->
            MissingFile(kind = row.optString("kind"), id = row.optString("id"))
        }
    }
}

/**
 * What changed on the server since [since]. An expert is given their own cases,
 * a manager everyone's — the server decides that from the token, not from
 * anything the phone asks for.
 */
suspend fun ServerApi.remoteManifest(token: String, since: Long): ApiResult<List<RemoteCase>> =
    get("sync/manifest&since=$since", token) { data ->
        val array = data.optJSONArray("reports")
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.let { row ->
                RemoteCase(
                    id = row.optString("id"),
                    updatedAt = row.optLong("updated_at"),
                    trackingCode = row.optString("tracking_code")
                )
            }
        }
    }

/** One whole case down, children included. */
suspend fun ServerApi.pullReport(token: String, id: String): ApiResult<ReportDetail?> =
    get("sync/report&id=$id", token) { data ->
        data.optJSONObject("report")?.let(SyncPayloadReader::report)
    }
