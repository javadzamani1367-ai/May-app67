package ir.ilam.inspection.sync

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Inspection history for the manager and the executors: the readings of every
 * panel's main breaker and feeders, every pole and every thermal plate, with who
 * recorded them. Same rules as the web panel's history page.
 */
object Readings {
    const val PANEL = 1
    const val POLE = 0
    const val THERMAL = "thermal"

    data class Row(
        val itemId: String, val at: Long, val r: Double?, val s: Double?, val t: Double?, val n: Double?,
        val tempMax: Double?, val tempBoxMax: Double?, val tempBoxAvg: Double?, val fileId: String?,
        val anomaly: Boolean, val anomalyNote: String?, val trackingCode: String?, val who: String?
    ) {
        fun phase(index: Int): Double? = when (index) { 0 -> r; 1 -> s; else -> t }
    }

    /** One panel or pole and part: what a series is of. */
    data class Key(val area: String?, val asset: Int, val plateKey: String, val part: String, val plate: String = plateKey)

    data class Summary(val key: Key, val areaLabel: String, val label: String, val count: Int, val firstAt: Long,
                       val lastAt: Long, val anomalies: Int, val last: Row?)

    data class Search(val keys: List<Summary>, val canExport: Boolean, val canRefer: Boolean)

    data class Group(val label: String, val part: String, val rows: List<Row>)

    data class Filter(val area: String? = null, val asset: Int? = null, val plate: String = "", val anomaly: Boolean = false) {
        fun query(): String = buildString {
            area?.let { append("&area=$it") }
            asset?.let { append("&asset=$it") }
            if (plate.isNotBlank()) append("&plate=" + URLEncoder.encode(plate.trim(), "UTF-8"))
            if (anomaly) append("&anomaly=1")
        }
    }

    internal fun row(o: JSONObject) = Row(
        o.optString("item_id"), o.optLong("at"), o.num("r"), o.num("s"), o.num("t"), o.num("n"), o.num("temp_max"),
        o.num("temp_box_max"), o.num("temp_box_avg"), o.str("file_id"), o.optBoolean("anomaly"), o.str("anomaly_note"),
        o.str("tracking_code"), o.str("who")
    )

    private fun JSONObject.num(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    internal fun keyQuery(key: Key): String = "&asset=${key.asset}&part=${key.part}" + (key.area?.let { "&area=$it" } ?: "") +
        "&plate_key=" + URLEncoder.encode(key.plateKey, "UTF-8")
}

suspend fun ServerApi.historySearch(token: String, filter: Readings.Filter): ApiResult<Readings.Search> =
    get("manage/history" + filter.query(), token) { data ->
        Readings.Search(
            keys = data.optJSONArray("keys").objects().map { k ->
                Readings.Summary(
                    Readings.Key(k.str("area"), k.optInt("asset"), k.optString("plate_key"), k.optString("part"), k.optString("plate")),
                    k.optString("area_label"), k.optString("label"), k.optInt("count"), k.optLong("first_at"), k.optLong("last_at"),
                    k.optInt("anomalies"), k.optJSONObject("last")?.let(Readings::row)
                )
            },
            canExport = data.optBoolean("can_export"),
            canRefer = data.optBoolean("can_refer")
        )
    }

suspend fun ServerApi.historySeries(token: String, key: Readings.Key): ApiResult<List<Readings.Row>> =
    get("manage/history/series" + Readings.keyQuery(key), token) { it.optJSONArray("rows").objects().map(Readings::row) }

/** Every panel and pole this inspection read, each with its history up to this visit. */
suspend fun ServerApi.itemHistory(token: String, itemId: String): ApiResult<List<Readings.Group>> =
    get("manage/history/item&id=$itemId", token) { data ->
        data.optJSONArray("groups").objects().map { g ->
            Readings.Group(g.optString("label"), g.optString("part"), g.optJSONArray("rows").objects().map(Readings::row))
        }
    }

/** A crypto case from a plate's history, straight to an expert of its area; returns the new case's id. */
suspend fun ServerApi.historyRefer(token: String, key: Readings.Key, expertId: String, note: String): ApiResult<String> =
    post("manage/history/refer", JSONObject().put("area", key.area).put("asset", key.asset).put("plate_key", key.plateKey)
        .put("part", key.part).put("expert_id", expertId).put("note", note.trim()), token) { it.optString("report_id") }

/** The search as Excel or Word, for [ServerFiles.downloadRoute]; the server allows it to the manager and headquarters. */
fun historyExportRoute(filter: Readings.Filter, format: String) = "manage/history/export&format=$format" + filter.query()

fun historyImageRoute(fileId: String) = "manage/history/image&id=$fileId"
