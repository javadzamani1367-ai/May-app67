package ir.ilam.inspection.field.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A name picked from the manager's list, with its id so a person's work is
 * counted under one record; [id] is null only when the list had not reached
 * the phone yet and the name was typed.
 */
data class Pick(val id: String?, val name: String) {
    fun toJson(): JSONObject = JSONObject().put("name", name.trim()).apply { putOpt("id", id) }

    companion object {
        fun fromJson(json: JSONObject?): Pick? {
            val name = json?.optString("name").orEmpty().trim()
            if (name.isEmpty()) return null
            return Pick(json?.optString("id")?.ifBlank { null }, name)
        }
    }
}

/**
 * The crew of an inspection: lead and lineman required; driver, crypto
 * executor, security expert and illegal-power officer optional, as the kind
 * of work has them ([CrewSlot.forKind]). Everyone named counts in the crew's
 * performance report.
 */
data class Crew(
    val lead: Pick? = null,
    val lineman: Pick? = null,
    val driver: Pick? = null,
    val vehicle: String = "",
    val crypto: Pick? = null,
    val security: Pick? = null,
    val illegal: Pick? = null
) {
    val complete: Boolean get() = lead != null && lineman != null

    fun toJson(): JSONObject = JSONObject().apply {
        lead?.let { put("lead", it.toJson()) }
        lineman?.let { put("lineman", it.toJson()) }
        CrewSlot.entries.forEach { slot -> slot.of(this@Crew)?.let { put(slot.key, it.toJson()) } }
        if (vehicle.isNotBlank()) put("vehicle", vehicle.trim())
    }

    companion object {
        fun fromJson(json: JSONObject?): Crew = if (json == null) Crew() else Crew(
            lead = Pick.fromJson(json.optJSONObject("lead")),
            lineman = Pick.fromJson(json.optJSONObject("lineman")),
            driver = Pick.fromJson(json.optJSONObject("driver")),
            vehicle = json.optString("vehicle"),
            crypto = Pick.fromJson(json.optJSONObject("crypto")),
            security = Pick.fromJson(json.optJSONObject("security")),
            illegal = Pick.fromJson(json.optJSONObject("illegal"))
        )
    }
}

/** The optional people of a crew: their key in the JSON, their role in the manager's list. */
enum class CrewSlot(val key: String, val role: Int) {
    CRYPTO("crypto", Roster.ROLE_CRYPTO),
    ILLEGAL("illegal", Roster.ROLE_ILLEGAL),
    SECURITY("security", Roster.ROLE_SECURITY),
    DRIVER("driver", Roster.ROLE_DRIVER);

    fun of(crew: Crew): Pick? = when (this) {
        DRIVER -> crew.driver
        CRYPTO -> crew.crypto
        SECURITY -> crew.security
        ILLEGAL -> crew.illegal
    }

    fun set(crew: Crew, pick: Pick?): Crew = when (this) {
        DRIVER -> crew.copy(driver = pick)
        CRYPTO -> crew.copy(crypto = pick)
        SECURITY -> crew.copy(security = pick)
        ILLEGAL -> crew.copy(illegal = pick)
    }

    companion object {
        /** Who may go along on each kind of work, besides the lead and the lineman. */
        fun forKind(kind: FieldKind): List<CrewSlot> = when (kind) {
            FieldKind.FEEDER -> listOf(DRIVER)
            FieldKind.ILLEGAL -> listOf(ILLEGAL, SECURITY, DRIVER)
            FieldKind.CRYPTO, FieldKind.THERMAL, FieldKind.FEEDER_LINE -> listOf(CRYPTO, SECURITY, DRIVER)
            FieldKind.BURN -> emptyList()
        }

        /** Kinds whose form starts with the crew. */
        fun hasCrew(kind: FieldKind): Boolean = kind != FieldKind.BURN
    }
}

/** One name of the manager's lists, as the phone keeps it. */
data class RosterEntry(val id: String, val name: String, val role: Int, val county: String?)

/**
 * The manager's crew members and medium-voltage feeders, kept on the phone
 * from the last sync so the forms work with no signal.
 */
data class Roster(val crew: List<RosterEntry> = emptyList(), val feeders: List<RosterEntry> = emptyList()) {

    fun crewFor(role: Int): List<RosterEntry> = crew.filter { it.role == role }

    companion object {
        const val ROLE_LEAD = 0
        const val ROLE_LINEMAN = 1
        const val ROLE_DRIVER = 2
        const val ROLE_CRYPTO = 3
        const val ROLE_SECURITY = 4
        const val ROLE_ILLEGAL = 5

        /** The `roster` object of `GET /field/me`, which is also what the phone stores. */
        fun fromJson(text: String?): Roster {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return Roster()
            fun list(key: String, role: (JSONObject) -> Int): List<RosterEntry> {
                val array = json.optJSONArray(key) ?: JSONArray()
                return (0 until array.length()).mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id")
                    val name = o.optString("name").trim()
                    if (id.isBlank() || name.isEmpty()) null
                    else RosterEntry(id, name, role(o), o.optString("county").ifBlank { null })
                }
            }
            return Roster(crew = list("crew") { it.optInt("role", -1) }, feeders = list("mv_feeders") { -1 })
        }
    }
}
