package ir.ilam.inspection.sync

import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import org.json.JSONArray
import org.json.JSONObject

/**
 * The manager's side of the comprehensive statistics, the same as the web
 * panel's «دستورالعمل‌های پاداش» and «ستون‌ها، دسترسی‌ها و قفل سال»: the reward
 * instructions — one letter setting all four rewards — and who sees which
 * column, the default export columns and the year locks.
 */
object CenterAdmin {
    data class Tier(val from: String = "", val to: String = "", val amount: String = "")
    data class Role(val label: String = "", val weight: String = "")

    /** One instruction as typed: every number a string until the server reads it. */
    data class Draft(
        val id: String? = null, val letterNo: String = "", val letterId: String = "", val letterDate: String = "",
        val effectiveFrom: String = "", val note: String = "",
        val finderMode: String = "progressive", val finderCap: String = "", val tiers: List<Tier> = List(6) { Tier() },
        val collectionRate: String = "", val collectionCount: String = "collected", val collectionCap: String = "",
        val enforcementRate: String = "", val enforcementCount: String = "found", val enforcementCap: String = "",
        val orgs: Set<String> = setOf("faraja", "sepah"),
        val staffPct: String = "", val ceoPct: String = "", val staffShare: String = "", val base: String = "energy",
        val staffCap: String = "", val roles: List<Role> = emptyList()
    )

    data class Rule(val id: String, val text: String, val note: String?, val kinds: List<String>, val draft: Draft)

    data class Rules(val rules: List<Rule>, val names: Map<String, String>, val modes: Map<String, String>,
                     val counts: Map<String, String>, val enforcers: Map<String, String>, val bases: Map<String, String>,
                     val staffRoles: List<String>)

    data class Settings(val executor: Boolean, val units: List<Set<String>>, val unitNames: List<String>, val excel: Set<String>,
                        val word: Set<String>, val columns: List<Centers.Column>, val years: List<Centers.Year>)

    private fun num(o: JSONObject?, key: String): String =
        if (o == null || o.isNull(key) || !o.has(key)) "" else PersianNumbers.plain(o.optDouble(key))

    private fun date(o: JSONObject, key: String): String = if (o.isNull(key) || !o.has(key)) "" else PersianDate.format(o.optLong(key))

    private fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.optString(it) }
    private fun pairs(o: JSONObject?) = o?.let { j -> j.keys().asSequence().associateWith { j.optString(it) } }.orEmpty()

    internal fun rules(d: JSONObject) = Rules(
        rules = d.optJSONArray("rules").objects().map { r ->
            val s = r.optJSONObject("sections") ?: JSONObject()
            val f = s.optJSONObject("finder")
            val c = s.optJSONObject("collection")
            val e = s.optJSONObject("enforcement")
            val st = s.optJSONObject("staff")
            val tiers = f?.optJSONArray("tiers").objects().map { Tier(num(it, "from"), num(it, "to"), num(it, "amount")) }
            Rule(r.optString("id"), r.optString("text"), r.str("note"), s.keys().asSequence().toList(), Draft(
                id = r.optString("id"), letterNo = r.str("letter_no").orEmpty(), letterId = r.str("letter_id").orEmpty(),
                letterDate = date(r, "letter_date"), effectiveFrom = date(r, "effective_from"), note = r.str("note").orEmpty(),
                finderMode = f?.str("mode") ?: "progressive", finderCap = num(f, "cap"),
                tiers = tiers + List((6 - tiers.size).coerceAtLeast(0)) { Tier() },
                collectionRate = num(c, "per_device"), collectionCount = c?.str("count") ?: "collected", collectionCap = num(c, "cap"),
                enforcementRate = num(e, "per_device"), enforcementCount = e?.str("count") ?: "found", enforcementCap = num(e, "cap"),
                orgs = e?.optJSONArray("orgs")?.let { strings(it).toSet() } ?: setOf("faraja", "sepah"),
                staffPct = num(st, "pct"), ceoPct = num(st, "ceo_pct"), staffShare = num(st, "staff_pct"),
                base = st?.str("base") ?: "energy", staffCap = num(st, "cap"),
                roles = st?.optJSONArray("roles").objects().map { Role(it.optString("label"), num(it, "weight")) }
            ))
        },
        names = pairs(d.optJSONObject("names")), modes = pairs(d.optJSONObject("modes")), counts = pairs(d.optJSONObject("counts")),
        enforcers = pairs(d.optJSONObject("enforcers")), bases = pairs(d.optJSONObject("bases")),
        staffRoles = strings(d.optJSONArray("staff_roles"))
    )

    internal fun body(d: Draft): JSONObject {
        val sections = JSONObject()
            .put("finder", JSONObject().put("mode", d.finderMode).put("cap", d.finderCap).put("tiers", JSONArray(d.tiers.map {
                JSONObject().put("from", it.from).put("to", it.to).put("amount", it.amount)
            })))
            .put("collection", JSONObject().put("per_device", d.collectionRate).put("count", d.collectionCount).put("cap", d.collectionCap))
            .put("enforcement", JSONObject().put("per_device", d.enforcementRate).put("count", d.enforcementCount)
                .put("cap", d.enforcementCap).put("orgs", JSONArray(d.orgs.toList())))
            .put("staff", JSONObject().put("pct", d.staffPct).put("ceo_pct", d.ceoPct).put("staff_pct", d.staffShare)
                .put("base", d.base).put("cap", d.staffCap)
                .put("roles", JSONArray(d.roles.map { JSONObject().put("label", it.label).put("weight", it.weight) })))
        return JSONObject().put("id", d.id ?: "").put("letter_no", d.letterNo).put("letter_id", d.letterId)
            .put("letter_date", d.letterDate).put("effective_from", d.effectiveFrom).put("note", d.note).put("sections", sections)
    }

    internal fun settings(d: JSONObject) = Settings(
        executor = d.optBoolean("executor"),
        units = (0 until (d.optJSONArray("units")?.length() ?: 0)).map { strings(d.optJSONArray("units")!!.optJSONArray(it)).toSet() },
        unitNames = strings(d.optJSONArray("unit_names")),
        excel = strings(d.optJSONArray("excel")).toSet(), word = strings(d.optJSONArray("word")).toSet(),
        columns = d.optJSONArray("columns").objects().map { c ->
            Centers.Column(c.optString("key"), c.optString("label"), c.optString("type"), c.optString("src"),
                c.optBoolean("template"), c.optBoolean("total"), strings(c.optJSONArray("choices")), c.optString("section"))
        },
        years = d.optJSONArray("years").objects().map { Centers.Year(it.optInt("year"), it.optInt("rows"), it.optBoolean("locked")) }
    )
}

suspend fun ServerApi.centerRules(token: String): ApiResult<CenterAdmin.Rules> = get("centers/rules", token, CenterAdmin::rules)

suspend fun ServerApi.centerRuleSave(token: String, draft: CenterAdmin.Draft): ApiResult<CenterAdmin.Rules> =
    post("centers/rules", CenterAdmin.body(draft), token, CenterAdmin::rules)

suspend fun ServerApi.centerRuleDelete(token: String, id: String): ApiResult<CenterAdmin.Rules> =
    post("centers/rules", JSONObject().put("delete", id), token, CenterAdmin::rules)

suspend fun ServerApi.centerSettings(token: String): ApiResult<CenterAdmin.Settings> =
    get("centers/settings", token, CenterAdmin::settings)

suspend fun ServerApi.centerSettingsSave(token: String, s: CenterAdmin.Settings): ApiResult<CenterAdmin.Settings> =
    post("centers/settings", JSONObject().put("executor", if (s.executor) 1 else 0)
        .put("units", JSONArray(s.units.map { JSONArray(it.toList()) }))
        .put("excel", JSONArray(s.excel.toList())).put("word", JSONArray(s.word.toList())), token, CenterAdmin::settings)

suspend fun ServerApi.centerYearLock(token: String, year: Int, lock: Boolean): ApiResult<CenterAdmin.Settings> =
    post("centers/settings", JSONObject().put("year", year).put("lock", if (lock) 1 else 0), token, CenterAdmin::settings)
