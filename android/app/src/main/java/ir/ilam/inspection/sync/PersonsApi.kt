package ir.ilam.inspection.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * People and their accounts, for the manager only: the same as the web panel's
 * persons pages. A person is registered once (by national ID); each tick of an
 * area and a role is an account with its own code and one-time password; each
 * app install has its own code, written against the person.
 */
object Persons {
    data class Row(val personId: String?, val userId: String, val code: String, val name: String, val nationalId: String?,
                   val kind: String?, val kindName: String, val active: Boolean)

    data class Group(val title: String, val rows: List<Row>)

    data class AreaBlock(val code: String, val name: String, val count: Int, val groups: List<Group>)

    data class Kind(val key: String, val name: String, val field: Boolean)

    data class Catalog(val areas: List<Pair<String, String>>, val kinds: List<Kind>, val groups: List<Pair<String, List<String>>>,
                       val apps: List<Pair<String, String>>)

    data class Directory(val areas: List<AreaBlock>, val province: List<Row>, val idle: List<Row>, val catalog: Catalog)

    data class Person(val id: String, val number: Int, val nationalId: String?, val name: String, val phone: String?,
                      val homeArea: String?, val note: String?, val autoBind: Boolean, val active: Boolean)

    data class Account(val id: String, val code: String, val area: String?, val areaLabel: String, val kind: String?,
                       val kindName: String, val permissions: Int, val active: Boolean)

    data class Figure(val label: String, val value: Int)

    data class RoleWork(val code: String, val kindName: String, val active: Boolean, val figures: List<Figure>,
                        val scores: List<Pair<String, Int?>>)

    data class AreaWork(val area: String, val name: String, val home: Boolean, val roles: List<RoleWork>)

    data class Detail(val person: Person, val accounts: List<Account>, val devices: Map<String, String>, val work: List<AreaWork>,
                      val catalog: Catalog)

    /** A new account's code and password, shown once. */
    data class Made(val code: String, val password: String, val kind: String, val area: String)

    data class Request(val id: String, val deviceCode: String, val name: String?, val nationalId: String?, val phone: String?,
                       val appName: String, val note: String?, val personName: String?, val at: Long)

    internal fun row(o: JSONObject) = Row(o.str("person_id"), o.optString("user_id"), o.optString("user_code"),
        o.str("name") ?: o.optString("full_name"), o.str("national_id"), o.str("kind"), o.optString("kind_name"), o.optBoolean("active", true))

    internal fun catalog(o: JSONObject?) = Catalog(
        areas = o?.optJSONArray("areas").objects().map { it.optString("code") to it.optString("name") },
        kinds = o?.optJSONArray("kinds").objects().map { Kind(it.optString("key"), it.optString("name"), it.optBoolean("field")) },
        groups = o?.optJSONArray("groups").objects().map { g ->
            g.optString("title") to (g.optJSONArray("kinds")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty())
        },
        apps = o?.optJSONArray("apps").objects().map { it.optString("key") to it.optString("name") }
    )
}

suspend fun ServerApi.persons(token: String, query: String, area: String?): ApiResult<Persons.Directory> =
    get("persons&q=" + Flow.encode(query) + (area?.let { "&area=$it" } ?: ""), token) { data ->
        Persons.Directory(
            areas = data.optJSONArray("areas").objects().map { a ->
                Persons.AreaBlock(a.optString("code"), a.optString("name"), a.optInt("count"),
                    a.optJSONArray("groups").objects().map { g -> Persons.Group(g.optString("title"), g.optJSONArray("rows").objects().map(Persons::row)) })
            },
            province = data.optJSONArray("province").objects().map(Persons::row),
            idle = data.optJSONArray("idle").objects().map(Persons::row),
            catalog = Persons.catalog(data.optJSONObject("catalog"))
        )
    }

suspend fun ServerApi.person(token: String, id: String): ApiResult<Persons.Detail> =
    get("persons/one&id=" + Flow.encode(id), token) { data ->
        val p = data.optJSONObject("person") ?: JSONObject()
        val devices = data.optJSONObject("devices") ?: JSONObject()
        Persons.Detail(
            person = Persons.Person(p.optString("id"), p.optInt("number"), p.str("national_id"), p.optString("full_name"), p.str("phone"),
                p.str("home_area"), p.str("note"), p.optBoolean("auto_bind"), p.optBoolean("active", true)),
            accounts = data.optJSONArray("accounts").objects().map { a ->
                Persons.Account(a.optString("id"), a.optString("user_code"), a.str("area"), a.optString("area_label"), a.str("kind"),
                    a.optString("kind_name"), a.optInt("permissions"), a.optInt("active", 1) == 1)
            },
            devices = devices.keys().asSequence().associateWith { devices.optString(it) },
            work = data.optJSONArray("work").objects().map { w ->
                Persons.AreaWork(w.optString("area"), w.optString("name"), w.optBoolean("home"), w.optJSONArray("roles").objects().map { r ->
                    Persons.RoleWork(r.optString("user_code"), r.optString("kind_name"), r.optBoolean("active", true),
                        r.optJSONArray("figures").objects().map { Persons.Figure(it.optString("label"), it.optInt("value")) },
                        r.optJSONArray("scores").objects().map { it.optString("kind") to it.int("score") })
                })
            },
            catalog = Persons.catalog(data.optJSONObject("catalog"))
        )
    }

/** Saves a person; returns the id (new or the same). */
suspend fun ServerApi.savePerson(token: String, id: String?, fields: Map<String, String>): ApiResult<String> =
    post("persons/save", JSONObject(fields as Map<*, *>).apply { if (id != null) put("id", id) }, token) { it.optString("id") }

/**
 * The person's ticks: "401:expert", "-:illegal_hq"… with the field permissions of each.
 * New accounts come back with their one-time passwords.
 */
suspend fun ServerApi.applyTicks(token: String, personId: String, ticks: Set<String>, permissions: Map<String, Int>): ApiResult<List<Persons.Made>> =
    post("persons/accounts", JSONObject().put("person_id", personId).put("ticks", JSONArray(ticks.toList()))
        .put("permissions", JSONObject(permissions as Map<*, *>)), token) { data ->
        data.optJSONArray("made").objects().map { Persons.Made(it.optString("user_code"), it.optString("password"), it.optString("kind"), it.optString("area")) }
    }

suspend fun ServerApi.resetPassword(token: String, userId: String): ApiResult<String> =
    post("persons/password", JSONObject().put("user_id", userId), token) { it.optString("password") }

suspend fun ServerApi.setDevice(token: String, personId: String, app: String, code: String): ApiResult<Boolean> =
    post("persons/device", JSONObject().put("person_id", personId).put("app", app).put("device_code", code), token) { true }

suspend fun ServerApi.personRequests(token: String): ApiResult<List<Persons.Request>> =
    get("persons/requests", token) { data ->
        data.optJSONArray("requests").objects().map {
            Persons.Request(it.optString("id"), it.optString("device_code"), it.str("full_name"), it.str("national_id"), it.str("phone"),
                it.optString("app_name"), it.str("note"), it.str("person_name"), it.optLong("created_at"))
        }
    }

/** "register" (to the matched person, or a new one from the request) or "reject"; returns the person id when registered. */
suspend fun ServerApi.decideRequest(token: String, id: String, action: String, fullName: String = "", nationalId: String = ""): ApiResult<String?> =
    post("persons/requests/decide", JSONObject().put("id", id).put("action", action).put("full_name", fullName)
        .put("national_id", nationalId), token) { it.str("person_id") }
