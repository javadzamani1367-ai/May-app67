package ir.ilam.inspection.field.sync

import ir.ilam.inspection.field.data.History
import ir.ilam.inspection.field.data.HistoryRow
import ir.ilam.inspection.field.data.HistoryTarget
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi
import org.json.JSONArray
import java.net.URLEncoder

/** One key of the area's history, as a page of the offline bundle brings it; no rows means forget it. */
data class HistoryKey(val area: String?, val asset: Int, val plateKey: String, val plate: String, val part: String,
                      val rows: List<HistoryRow>) {
    val id: String get() = listOf(area.orEmpty(), asset.toString(), plateKey, part).joinToString("|")
}

data class HistoryPage(val keys: List<HistoryKey>, val next: String?, val serverTime: Long)

/** Earlier readings of one plate, newest first, without who recorded them. */
suspend fun ServerApi.fieldHistory(token: String, target: HistoryTarget): ApiResult<List<HistoryRow>> =
    get("field/history&asset=${target.asset}&part=${target.part}&plate=" + URLEncoder.encode(target.plate, "UTF-8"), token) {
        History.rows(it.optJSONArray("rows"))
    }

suspend fun ServerApi.fieldHistoryBundle(token: String, since: Long, after: String): ApiResult<HistoryPage> =
    get("field/history/bundle&since=$since&after=" + URLEncoder.encode(after, "UTF-8"), token) { data ->
        val keys = data.optJSONArray("keys") ?: JSONArray()
        HistoryPage(
            keys = (0 until keys.length()).mapNotNull { keys.optJSONObject(it) }.map { k ->
                HistoryKey(k.optString("area").takeIf { it.isNotBlank() && it != "null" }, k.optInt("asset"),
                    k.optString("plate_key"), k.optString("plate"), k.optString("part"), History.rows(k.optJSONArray("rows")))
            },
            next = data.optString("next").takeIf { it.isNotBlank() && it != "null" },
            serverTime = data.optLong("server_time")
        )
    }

/** The route that answers with a thermal picture of an earlier inspection. */
fun historyImageRoute(fileId: String) = "field/history/image&id=$fileId"
