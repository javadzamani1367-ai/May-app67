package ir.ilam.inspection.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * The field app's items, as the manager app reads and moves them. Extensions
 * on [ServerApi] like every other family of calls; the server checks the
 * manager role on each one, and applies the same rules as its web panel.
 */
object FieldManage {
    const val STATUS_REGISTERED = 0
    const val STATUS_REVIEWING = 1
    const val STATUS_REFERRED = 2
    const val STATUS_RESULT = 3
    const val STATUS_CLOSED = 4
    const val STATUS_REJECTED = 5
    const val STATUS_REVISIT = 6
    val STATUSES = listOf(STATUS_REGISTERED, STATUS_REVIEWING, STATUS_REFERRED, STATUS_RESULT, STATUS_CLOSED,
        STATUS_REJECTED, STATUS_REVISIT)

    const val KIND_CRYPTO = 1
    const val KIND_ILLEGAL = 2
    /** Network inspections — thermal, panel feeders, pole by pole: they have a reading history. */
    val INSPECTIONS = listOf(3, 4, 6)

    /** Kinds as the server numbers them: crypto, illegal, thermal, feeder panel, burn, pole by pole. */
    val KINDS = listOf(1, 2, 5, 3, 4, 6)

    data class Row(
        val id: String,
        val kind: Int,
        val kindName: String,
        val trackingCode: String,
        val status: Int,
        val statusName: String,
        val priority: Int?,
        val createdAt: Long,
        val dueAt: Long?,
        val overdue: Boolean,
        val address: String?,
        val plate: String?,
        val reporter: String,
        val expert: String?,
        /** «Ilam — area 401», as the server names it. */
        val area: String = "",
        /** The crypto executor the item was handed to, if any. */
        val handler: String? = null
    )

    data class Page(val items: List<Row>, val more: Boolean, val counts: Map<Int, Int>, val overdue: Int)

    data class Step(val status: Int, val name: String, val needsNote: Boolean, val needsExpert: Boolean)
    data class Expert(val id: String, val name: String)
    data class Event(val at: Long, val who: String?, val what: String, val note: String?)
    data class Photo(val id: String, val capturedAt: Long?, val note: String)

    data class Detail(
        val row: Row,
        val reporterCode: String,
        val latitude: Double?,
        val longitude: Double?,
        val accuracy: Double?,
        val description: String?,
        /** When the position was recorded and how far the user stood from it, in words from the server. */
        val positionNote: String?,
        val mergedInto: String?,
        val details: List<Pair<String, String>>,
        val resultName: String?,
        val resultNote: String?,
        val photos: List<Photo>,
        val fileCount: Int,
        val events: List<Event>,
        val next: List<Step>,
        val experts: List<Expert>,
        /** Area executors this user may hand the item to; empty for an executor. */
        val executors: List<Expert> = emptyList(),
        val canHand: Boolean = false
    )

    data class Filter(val status: Int? = null, val kind: Int? = null, val query: String = "", val overdue: Boolean = false)

    internal fun row(o: JSONObject) = Row(
        id = o.optString("id"),
        kind = o.optInt("kind"),
        kindName = o.optString("kind_name"),
        trackingCode = o.optString("tracking_code"),
        status = o.optInt("status"),
        statusName = o.optString("status_name"),
        priority = o.optIntOrNull("priority"),
        createdAt = o.optLong("created_at"),
        dueAt = o.optLongOrNull("due_at"),
        overdue = o.optBoolean("overdue"),
        address = o.optStringOrNull("address"),
        plate = o.optStringOrNull("plate"),
        reporter = o.optString("reporter"),
        expert = o.optStringOrNull("expert"),
        area = o.optString("area"),
        handler = o.optStringOrNull("handler")
    )

    internal fun detail(o: JSONObject): Detail {
        val result = o.optJSONObject("result")
        return Detail(
            row = row(o),
            reporterCode = o.optString("reporter_code"),
            latitude = o.optDoubleOrNull("latitude"),
            longitude = o.optDoubleOrNull("longitude"),
            accuracy = o.optDoubleOrNull("accuracy"),
            description = o.optStringOrNull("description"),
            positionNote = o.optStringOrNull("position_note"),
            mergedInto = o.optStringOrNull("merged_into"),
            details = o.optJSONArray("details").objects().map { it.optString("label") to it.optString("value") },
            resultName = o.optStringOrNull("result_name"),
            resultNote = result?.optStringOrNull("note"),
            photos = o.optJSONArray("photos").objects().map {
                Photo(it.optString("id"), it.optLongOrNull("captured_at"), it.optString("note"))
            },
            fileCount = o.optInt("file_count"),
            events = o.optJSONArray("events").objects().map {
                Event(it.optLong("at"), it.optStringOrNull("who"), it.optString("what"), it.optStringOrNull("note"))
            },
            next = o.optJSONArray("next").objects().map {
                Step(it.optInt("status"), it.optString("name"), it.optBoolean("needs_note"), it.optBoolean("needs_expert"))
            },
            experts = o.optJSONArray("experts").objects().map { Expert(it.optString("id"), it.optString("name")) },
            executors = o.optJSONArray("executors").objects().map { Expert(it.optString("id"), it.optString("name")) },
            canHand = o.optBoolean("can_hand")
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
}

/** One page of the list, newest first, with the count of every status for the tabs. */
suspend fun ServerApi.fieldItems(token: String, filter: FieldManage.Filter, page: Int): ApiResult<FieldManage.Page> {
    val query = buildString {
        append("manage/field/items&page=").append(page)
        filter.status?.let { append("&status=").append(it) }
        filter.kind?.let { append("&kind=").append(it) }
        if (filter.overdue) append("&overdue=1")
        if (filter.query.isNotBlank()) append("&q=").append(java.net.URLEncoder.encode(filter.query.trim(), "UTF-8"))
    }
    return get(query, token) { data ->
        val items = data.optJSONArray("items")
        val counts = data.optJSONObject("counts")
        FieldManage.Page(
            items = (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it)?.let(FieldManage::row) },
            more = data.optBoolean("more"),
            counts = counts?.let { json ->
                json.keys().asSequence().mapNotNull { key -> key.toIntOrNull()?.let { it to json.optInt(key) } }.toMap()
            }.orEmpty(),
            overdue = data.optInt("overdue")
        )
    }
}

suspend fun ServerApi.fieldItem(token: String, id: String): ApiResult<FieldManage.Detail> =
    get("manage/field/item&id=$id", token) { data -> FieldManage.detail(data.optJSONObject("item") ?: JSONObject()) }

/** Moves an item on; a referral names the expert and, optionally, the end of the day it is due. */
suspend fun ServerApi.moveFieldItem(
    token: String,
    id: String,
    status: Int,
    note: String,
    expertId: String?,
    dueAt: Long?
): ApiResult<FieldManage.Detail> {
    val body = JSONObject().put("id", id).put("status", status).put("note", note.trim())
    expertId?.let { body.put("assigned_to", it) }
    dueAt?.let { body.put("due_at", it) }
    return post("manage/field/status", body, token) { data -> FieldManage.detail(data.optJSONObject("item") ?: JSONObject()) }
}

/** Hands an item to an area's crypto executor, who refers it on and follows it up. */
suspend fun ServerApi.handFieldItem(token: String, id: String, executorId: String, note: String): ApiResult<FieldManage.Detail> =
    post("manage/field/hand", JSONObject().put("id", id).put("executor_id", executorId).put("note", note.trim()), token) { data ->
        FieldManage.detail(data.optJSONObject("item") ?: JSONObject())
    }

/** A follow-up note on an item; for an executor it counts as acting on it. */
suspend fun ServerApi.followUpFieldItem(token: String, id: String, note: String): ApiResult<FieldManage.Detail> =
    post("manage/field/followup", JSONObject().put("id", id).put("note", note.trim()), token) { data ->
        FieldManage.detail(data.optJSONObject("item") ?: JSONObject())
    }
