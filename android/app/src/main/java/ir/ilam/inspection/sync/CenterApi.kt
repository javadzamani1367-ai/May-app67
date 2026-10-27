package ir.ilam.inspection.sync

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * «آمار جامع مراکز استخراج و اقدامات», live from the server: the rows of a range,
 * one row with its system / file / own values, saving and choosing between
 * conflicting values, the Excel import in two steps, and the reward reports.
 * The rules (which cases, who sees which columns) are the server's; the web
 * panel's pages are the same thing in a browser.
 */
object Centers {
    data class Column(val key: String, val label: String, val type: String, val src: String, val template: Boolean,
                      val total: Boolean, val choices: List<String>, val section: String)

    data class Warning(val key: String, val text: String)

    data class Row(
        val id: String, val reportId: String?, val year: Int, val area: String?, val source: Int, val rowDate: Long?,
        val locked: Boolean, val approved: Boolean, val values: Map<String, Any?>, val conflicts: List<String>,
        val warnings: List<Warning>, val hint: String?, val suggest: String?
    )

    data class Year(val year: Int, val rows: Int, val locked: Boolean)
    data class Bar(val label: String, val value: Double)
    data class Charts(val centers: Int, val miners: Double, val kw: Double, val conflicts: Int, val months: List<Bar>,
                      val areas: List<Bar>, val status: List<Bar>, val money: List<Bar>)

    data class Page(val from: Long, val to: Long, val rows: List<Row>, val totals: Map<String, Any?>, val columns: List<Column>,
                    val writable: Set<String>, val manage: Boolean, val years: List<Year>, val charts: Charts?,
                    /** Default ticked columns of each export: «xlsx» and «docx». */
                    val exportColumns: Map<String, List<String>> = emptyMap())

    data class Change(val label: String, val old: String?, val new: String?, val how: String, val at: Long, val who: String?)
    /** One of the centre's four rewards: its amount, the instruction, and whether its form is final. */
    data class RewardLine(val kind: String, val name: String, val amount: Double?, val rule: String, val final: Boolean,
                          val number: String?, val people: Int)

    data class Detail(
        val row: Row, val from: Map<String, String>, val system: Map<String, Any?>, val file: Map<String, Any?>,
        val changes: List<Change>, val rewards: List<RewardLine>
    )

    data class Sheet(val name: String, val kind: String, val rows: Int, val selected: Boolean, val columns: Int,
                     val missing: List<String>, val unknown: List<String>)
    data class PlanRow(val sheet: String, val line: Int, val year: Int, val status: String, val subscription: String?,
                       val subscriber: String?, val conflicts: List<String>)
    data class Plan(val token: String, val sheets: List<Sheet>, val summary: Map<String, Int>, val rows: List<PlanRow>)
    data class Applied(val updated: Int, val created: Int, val supplement: Int)

    /** A table of a reward report, every cell already as it reads. */
    data class Table(val caption: String?, val header: List<String>, val rows: List<List<String>>, val total: List<String>?)
    /** One of the four reward reports: the explanation of the instructions used, then its tables. */
    data class Rewards(val title: String, val subtitle: String, val paragraphs: List<String>, val tables: List<Table>)

    internal fun row(o: JSONObject) = Row(
        o.optString("id"), o.str("report_id"), o.optInt("year"), o.str("area_code"), o.optInt("source"),
        if (o.isNull("row_date")) null else o.optLong("row_date"), o.optBoolean("locked"), o.optBoolean("approved", true),
        o.optJSONObject("values").toMap(), o.optJSONArray("conflicts").strings(),
        o.optJSONArray("warnings").objects().map { Warning(it.optString("key"), it.optString("text")) },
        o.optJSONObject("hint")?.str("text"), o.optJSONObject("hint")?.str("suggest")
    )

    internal fun JSONObject?.toMap(): Map<String, Any?> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { if (isNull(it)) null else get(it) }
    }

    internal fun JSONArray?.strings(): List<String> = (0 until (this?.length() ?: 0)).map { this!!.optString(it) }

    internal fun bars(a: JSONArray?, label: String, value: String) =
        a.objects().map { Bar(it.optString(label), it.optDouble(value, 0.0)) }

    fun query(year: Int?, search: String): String = (year?.let { "&year=$it" } ?: "") +
        (if (search.isBlank()) "" else "&q=" + URLEncoder.encode(search.trim(), "UTF-8"))
}

suspend fun ServerApi.centers(token: String, year: Int?, search: String, charts: Boolean): ApiResult<Centers.Page> =
    get("centers" + Centers.query(year, search) + if (charts) "&charts=1" else "", token) { d ->
        val access = d.optJSONObject("access")
        Centers.Page(
            from = d.optLong("from"), to = d.optLong("to"),
            rows = d.optJSONArray("rows").objects().map(Centers::row),
            totals = with(Centers) { d.optJSONObject("totals").toMap() },
            columns = d.optJSONArray("columns").objects().map { c ->
                Centers.Column(c.optString("key"), c.optString("label"), c.optString("type"), c.optString("src"),
                    c.optBoolean("template"), c.optBoolean("total"), with(Centers) { c.optJSONArray("choices").strings() },
                    c.optString("section"))
            },
            writable = with(Centers) { access?.optJSONArray("fields").strings().toSet() },
            manage = access?.optBoolean("manage") == true,
            years = d.optJSONArray("years").objects().map { Centers.Year(it.optInt("year"), it.optInt("rows"), it.optBoolean("locked")) },
            charts = d.optJSONObject("charts")?.let { c ->
                val t = c.optJSONObject("totals") ?: JSONObject()
                Centers.Charts(t.optInt("centers"), t.optDouble("miners", 0.0), t.optDouble("kw", 0.0), t.optInt("conflicts"),
                    Centers.bars(c.optJSONArray("months"), "label", "centers"), Centers.bars(c.optJSONArray("areas"), "label", "miners"),
                    Centers.bars(c.optJSONArray("status"), "label", "count"), Centers.bars(c.optJSONArray("money"), "label", "amount"))
            },
            exportColumns = d.optJSONObject("export")?.let { e ->
                listOf("xlsx", "docx").associateWith { f -> with(Centers) { e.optJSONArray(f).strings() } }
            }.orEmpty()
        )
    }

private fun detail(d: JSONObject): Centers.Detail = with(Centers) {
    Centers.Detail(
        row = row(d), from = d.optJSONObject("from").toMap().mapValues { it.value.toString() },
        system = d.optJSONObject("system").toMap(), file = d.optJSONObject("file").toMap(),
        changes = d.optJSONArray("changes").objects().map {
            Centers.Change(it.optString("label"), it.str("old"), it.str("new"), it.optString("how"), it.optLong("at"), it.str("who"))
        },
        rewards = d.optJSONArray("rewards").objects().map {
            Centers.RewardLine(it.optString("kind"), it.optString("name"), if (it.isNull("amount")) null else it.optDouble("amount"),
                it.optString("rule"), it.optBoolean("final"), it.str("number"), it.optInt("people"))
        }
    )
}

suspend fun ServerApi.centerRow(token: String, id: String): ApiResult<Centers.Detail> =
    get("centers/row&id=$id", token, ::detail)

/** Changed cells by key (text as typed; the server normalises), and choices: «system» or «file». */
suspend fun ServerApi.centerSave(token: String, id: String, values: Map<String, String>, choices: Map<String, String>): ApiResult<Centers.Detail> =
    post("centers/row", JSONObject().put("id", id).put("values", JSONObject(values)).put("choices", JSONObject(choices)), token, ::detail)

/** A second row for the same centre; returns its id. */
suspend fun ServerApi.centerCopy(token: String, id: String): ApiResult<String> =
    post("centers/add", JSONObject().put("copy_of", id), token) { it.optString("id") }

suspend fun ServerApi.centerDelete(token: String, id: String): ApiResult<Unit> =
    post("centers/delete", JSONObject().put("id", id), token) { }

private fun plan(d: JSONObject): Centers.Plan = with(Centers) {
    Centers.Plan(
        token = d.optString("token"),
        sheets = d.optJSONArray("sheets").objects().map {
            Centers.Sheet(it.optString("name"), it.optString("kind"), it.optInt("rows"), it.optBoolean("selected"),
                it.optJSONArray("columns")?.length() ?: 0, it.optJSONArray("missing").strings(), it.optJSONArray("unknown").strings())
        },
        summary = d.optJSONObject("summary").toMap().mapValues { (it.value as? Number)?.toInt() ?: 0 },
        rows = d.optJSONArray("rows").objects().map { r ->
            Centers.PlanRow(r.optString("sheet"), r.optInt("line"), r.optInt("year"), r.optString("status"), r.str("subscription"),
                r.str("subscriber"), r.optJSONArray("conflicts").objects().map {
                    it.optString("label") + ": " + it.optString("system") + " / " + it.optString("file")
                })
        }
    )
}

/** Step one of an import: the file's bytes, and back the preview. */
suspend fun ServerApi.centerImport(token: String, bytes: ByteArray, year: Int): ApiResult<Centers.Plan> =
    postBytes("centers/import&year=$year", bytes, token, ::plan)

/** The preview again with other sheets ticked. */
suspend fun ServerApi.centerReplan(token: String, plan: String, sheets: List<String>): ApiResult<Centers.Plan> =
    post("centers/import/apply", JSONObject().put("token", plan).put("sheets", JSONArray(sheets)).put("preview", 1), token, ::plan)

suspend fun ServerApi.centerApply(token: String, plan: String, sheets: List<String>): ApiResult<Centers.Applied> =
    post("centers/import/apply", JSONObject().put("token", plan).put("sheets", JSONArray(sheets)), token) {
        Centers.Applied(it.optInt("updated"), it.optInt("created"), it.optInt("supplement"))
    }

/** One reward report: «finder», «collection», «enforcement» or «staff». */
suspend fun ServerApi.centerRewards(token: String, year: Int?, kind: String): ApiResult<Centers.Rewards> =
    get("centers/rewards&kind=$kind" + Centers.query(year, ""), token) { d ->
        with(Centers) {
            Centers.Rewards(
                title = d.optString("title"), subtitle = d.optString("subtitle"),
                paragraphs = d.optJSONArray("paragraphs").strings(),
                tables = d.optJSONArray("tables").objects().map { t ->
                    Centers.Table(t.str("caption"), t.optJSONArray("header").strings(),
                        (0 until (t.optJSONArray("rows")?.length() ?: 0)).map { i -> t.optJSONArray("rows")!!.optJSONArray(i).strings() },
                        t.optJSONArray("total")?.strings())
                }
            )
        }
    }

/** The statistics as Excel or Word with the ticked columns; empty means the manager's defaults. */
fun centerExportRoute(year: Int?, search: String, format: String, columns: List<String> = emptyList()) =
    "centers/export&format=$format" + Centers.query(year, search) + if (columns.isEmpty()) "" else "&cols=" + columns.joinToString(",")

fun centerRewardsRoute(year: Int?, kind: String, format: String) = "centers/rewards&format=$format&kind=$kind" + Centers.query(year, "")
