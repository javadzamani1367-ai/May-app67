package ir.ilam.inspection.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * The manager app's live tables: scores out of 100, the miner device registry
 * with its duplicate serials (the manager's alone), and the account's
 * notifications. Read from the server each time; nothing kept on the phone.
 */
object Manage {
    data class ScoreRow(val name: String, val area: String, val unit: String?, val score: Int?, val rank: Int?,
                        val parts: Map<String, Double?>)

    data class ScorePart(val key: String, val weight: Int, val label: String)

    data class ScoreTable(val key: String, val title: String, val parts: List<ScorePart>, val rows: List<ScoreRow>)

    data class Device(
        val reportId: String, val code: String, val model: String?, val serial: String?, val serialKey: String?, val powerWatt: Double?,
        val foundAt: Long?, val area: String, val subscription: String?, val subscriber: String?, val owner: String?, val seenIn: Int
    )

    data class DevicePage(val devices: List<Device>, val more: Boolean, val unseenAlerts: Int)

    data class Alert(val serial: String, val reportId: String, val otherReportId: String, val at: Long, val seen: Boolean,
                     val now: String, val before: String)

    data class Note(val id: String, val kind: String, val title: String, val body: String?, val reportId: String?, val at: Long,
                    val read: Boolean)

    val TABLES = listOf("areas", "executors", "units", "experts")

    internal fun device(o: JSONObject) = Device(o.optString("report_id"), o.optString("code"), o.str("model"), o.str("serial"),
        o.str("serial_key"), if (o.isNull("power_watt")) null else o.optDouble("power_watt"), o.long("found_at"), o.optString("area"),
        o.str("subscription"), o.str("subscriber"), o.str("owner"), o.optInt("seen_in"))
}

/** Every table of scores for the period, ranked, with the formula of each. */
suspend fun ServerApi.scores(token: String, from: Long, to: Long): ApiResult<List<Manage.ScoreTable>> =
    get("manage/scores&from=$from&to=$to", token) { data ->
        val spec = data.optJSONObject("spec") ?: JSONObject()
        Manage.TABLES.map { key ->
            val table = spec.optJSONObject(key) ?: JSONObject()
            Manage.ScoreTable(
                key = key,
                title = table.optString("title"),
                parts = table.optJSONArray("parts").objects().map { Manage.ScorePart(it.optString("key"), it.optInt("weight"), it.optString("label")) },
                rows = data.optJSONArray(key).objects().map { row ->
                    val parts = row.optJSONObject("parts") ?: JSONObject()
                    Manage.ScoreRow(row.optString("name"), row.optString("area"), row.str("unit"), row.int("score"), row.int("rank"),
                        parts.keys().asSequence().associateWith { k -> if (parts.isNull(k)) null else parts.optDouble(k) })
                }
            )
        }
    }

suspend fun ServerApi.devices(token: String, query: String, duplicatesOnly: Boolean, page: Int): ApiResult<Manage.DevicePage> {
    val path = buildString {
        append("manage/devices&page=").append(page)
        if (query.isNotBlank()) append("&q=").append(Flow.encode(query.trim()))
        if (duplicatesOnly) append("&dup=1")
    }
    return get(path, token) { data ->
        Manage.DevicePage(data.optJSONArray("devices").objects().map(Manage::device), data.optBoolean("more"), data.optInt("unseen_alerts"))
    }
}

/** Every case a serial has been found in, oldest first: where the device was, and with whom. */
suspend fun ServerApi.deviceHistory(token: String, serial: String): ApiResult<List<Manage.Device>> =
    get("manage/devices/history&serial=" + Flow.encode(serial), token) { data ->
        data.optJSONArray("history").objects().map(Manage::device)
    }

suspend fun ServerApi.deviceAlerts(token: String, markSeen: Boolean): ApiResult<List<Manage.Alert>> =
    get("manage/devices/alerts" + if (markSeen) "&mark_seen=1" else "", token) { data ->
        data.optJSONArray("alerts").objects().map {
            Manage.Alert(it.optString("serial"), it.optString("report_id"), it.optString("other_report_id"), it.optLong("at"),
                it.optBoolean("seen"), it.optString("now"), it.optString("before"))
        }
    }

suspend fun ServerApi.notifications(token: String): ApiResult<List<Manage.Note>> = get("notifications&since=0", token) { data ->
    data.optJSONArray("notifications").objects().map {
        Manage.Note(it.optString("id"), it.optString("kind"), it.optString("title"), it.str("body"), it.str("report_id"),
            it.optLong("created_at"), !it.isNull("read_at"))
    }
}

suspend fun ServerApi.markNotificationsRead(token: String, ids: List<String>): ApiResult<Boolean> =
    post("notifications/read", JSONObject().put("ids", JSONArray(ids)), token) { true }
