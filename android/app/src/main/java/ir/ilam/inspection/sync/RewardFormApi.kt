package ir.ilam.inspection.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * One centre's reward form, live from the server: the people with their tick,
 * organisation, action and percentage; saved, made final with a number, or
 * opened again; and the one-page Excel and Word. Kinds: «finder»,
 * «collection», «enforcement», «staff». The splitting rules are the server's.
 */
object RewardForms {
    data class Person(
        val key: String, val name: String, val post: String, val org: String, val action: String,
        /** Percent of the staff share; null for every kind but «staff», and for the CEO. */
        val pct: Double?, val source: String, val on: Boolean, val share: Double = 0.0,
        /** Arrived from the visit or the expert after the form was saved. */
        val isNew: Boolean = false
    ) {
        val isCeo get() = key == "ceo"
    }

    data class Calc(val amount: Double, val pot: Double, val count: Int, val each: Double, val ceo: Double, val staff: Double,
                    val base: Double, val pctSum: Double, val payers: Map<String, Double>)

    data class Form(
        val rowId: String, val title: String, val trackingCode: String?, val finderName: String?,
        val kind: String, val name: String, val final: Boolean, val number: String?, val finalAt: Long?, val editable: Boolean,
        val people: List<Person>, val calc: Calc?, val rule: String?, val terms: String?, val paragraphs: List<String>,
        val notes: List<String>, val names: List<String>, val posts: List<String>, val orgs: Map<String, String>,
        val sources: Map<String, String>
    )

    internal fun form(d: JSONObject): Form {
        val row = d.optJSONObject("row") ?: JSONObject()
        val model = d.optJSONObject("model") ?: JSONObject()
        return Form(
            rowId = row.optString("id"), title = row.optString("title"), trackingCode = row.str("tracking_code"),
            finderName = row.str("finder_name"), kind = d.optString("kind"), name = d.optString("name"), final = d.optBoolean("final"),
            number = d.str("number"), finalAt = if (d.isNull("final_at")) null else d.optLong("final_at"),
            editable = d.optBoolean("editable"),
            people = d.optJSONArray("people").objects().map {
                Person(it.optString("key"), it.optString("name"), it.optString("post"), it.optString("org"), it.optString("action"),
                    if (it.isNull("pct")) null else it.optDouble("pct"), it.optString("source"), it.optBoolean("on"),
                    it.optDouble("share", 0.0), it.optBoolean("new"))
            },
            calc = d.optJSONObject("calc")?.let { c ->
                Calc(c.optDouble("amount", 0.0), c.optDouble("pot", 0.0), c.optInt("count"), c.optDouble("each", 0.0),
                    c.optDouble("ceo", 0.0), c.optDouble("staff", 0.0), c.optDouble("base", 0.0), c.optDouble("pct_sum", 0.0),
                    c.optJSONObject("payers")?.let { p -> p.keys().asSequence().associateWith { k -> p.optDouble(k, 0.0) } }.orEmpty())
            },
            rule = d.str("rule"), terms = d.str("terms"),
            paragraphs = strings(model.optJSONArray("paragraphs")), notes = strings(model.optJSONArray("notes")),
            names = strings(d.optJSONArray("names")), posts = strings(d.optJSONArray("posts")),
            orgs = pairs(d.optJSONObject("orgs")), sources = pairs(d.optJSONObject("sources"))
        )
    }

    private fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.optString(it) }
    private fun pairs(o: JSONObject?) = o?.let { j -> j.keys().asSequence().associateWith { j.optString(it) } }.orEmpty()

    internal fun json(people: List<Person>) = JSONArray(people.map {
        JSONObject().put("key", it.key).put("name", it.name).put("post", it.post).put("org", it.org).put("action", it.action)
            .put("pct", it.pct ?: JSONObject.NULL).put("source", it.source).put("on", it.on)
    })
}

suspend fun ServerApi.rewardForm(token: String, row: String, kind: String): ApiResult<RewardForms.Form> =
    get("centers/form&row=$row&kind=$kind", token, RewardForms::form)

/** Saves [people] (when given), then «final» or «unlock» when [action] says so. */
suspend fun ServerApi.rewardFormPost(token: String, row: String, kind: String, people: List<RewardForms.Person>?,
                                     action: String?): ApiResult<RewardForms.Form> {
    val body = JSONObject()
    people?.let { body.put("people", RewardForms.json(it)) }
    action?.let { body.put("action", it) }
    return post("centers/form&row=$row&kind=$kind", body, token, RewardForms::form)
}

fun rewardFormRoute(row: String, kind: String, format: String) = "centers/form/export&row=$row&kind=$kind&format=$format"
