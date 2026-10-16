package ir.ilam.inspection.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * The crew performance report, as the manager app reads it from the server
 * (`/manage/crew/report`, `overtime`, `export`): the same figures as the web panel's crew page, built
 * there from the moments panels and poles were recorded on the map.
 */
object CrewWork {
    data class Point(val at: Long, val type: String, val latitude: Double?, val longitude: Double?, val plate: String, val inspection: Boolean)

    data class Day(
        val day: String,
        val first: Long,
        val last: Long,
        val hours: Double,
        val visits: Int,
        val inspections: Int,
        val subscribers: Int,
        val overtime: Double,
        val holiday: String?,
        val outside: Int,
        val km: Double,
        val places: List<String>,
        val points: List<Point>
    )

    data class Total(
        val days: Int,
        val holidays: Int,
        val visits: Int,
        val inspections: Int,
        val types: Map<String, Int>,
        val subscribers: Int,
        val buckets: Map<String, Int>,
        val overtime: Double,
        val overtimeInspections: Int,
        val outside: Int,
        val km: Double,
        val avgStart: Int?,
        val avgEnd: Int?
    )

    data class Approved(val hours: Double, val note: String)

    data class Member(val key: String, val name: String, val roles: List<String>, val total: Total, val approved: Approved?, val days: List<Day>)

    data class Visit(val number: Int, val label: String, val first: Long, val last: Long, val inspections: Int,
        val latitude: Double?, val longitude: Double?, val place: String?)

    data class Report(
        val members: List<Member>,
        val visits: List<Visit>,
        /** False when the server has no offline map: «outside the city» is then unknown. */
        val geo: Boolean,
        val from: Long,
        val to: Long,
        val typeNames: Map<String, String>,
        val bucketNames: Map<String, String>
    )

    fun parse(o: JSONObject): Report = Report(
        members = o.optJSONArray("members").objects().map(::member),
        visits = o.optJSONArray("visits").objects().map {
            Visit(it.optInt("n"), it.optString("label"), it.optLong("first"), it.optLong("last"), it.optInt("inspections"),
                it.doubleOrNull("lat"), it.doubleOrNull("lon"), it.stringOrNull("place"))
        },
        geo = o.optBoolean("geo"),
        from = o.optLong("from"),
        to = o.optLong("to"),
        typeNames = o.optJSONObject("type_names").strings(),
        bucketNames = o.optJSONObject("bucket_names").strings()
    )

    private fun member(o: JSONObject): Member {
        val t = o.optJSONObject("total") ?: JSONObject()
        val approved = o.optJSONObject("approved")
        return Member(
            key = o.optString("key"),
            name = o.optString("name"),
            roles = o.optJSONArray("roles").let { a -> (0 until (a?.length() ?: 0)).map { a!!.optString(it) } },
            total = Total(
                days = t.optInt("days"), holidays = t.optInt("holidays"), visits = t.optInt("visits"),
                inspections = t.optInt("inspections"), types = t.optJSONObject("types").ints(), subscribers = t.optInt("subscribers"),
                buckets = t.optJSONObject("buckets").ints(), overtime = t.optDouble("overtime", 0.0),
                overtimeInspections = t.optInt("overtime_inspections"), outside = t.optInt("outside"), km = t.optDouble("km", 0.0),
                avgStart = t.intOrNull("avg_start"), avgEnd = t.intOrNull("avg_end")
            ),
            approved = approved?.let { Approved(it.optDouble("hours", 0.0), it.optString("note")) },
            days = o.optJSONArray("days").objects().map { d ->
                Day(
                    day = d.optString("day"), first = d.optLong("first"), last = d.optLong("last"), hours = d.optDouble("hours", 0.0),
                    visits = d.optInt("visits"), inspections = d.optInt("inspections"), subscribers = d.optInt("subscribers"),
                    overtime = d.optDouble("overtime", 0.0), holiday = d.stringOrNull("holiday"), outside = d.optInt("outside"),
                    km = d.optDouble("km", 0.0),
                    places = d.optJSONArray("places").let { a -> (0 until (a?.length() ?: 0)).map { a!!.optString(it) } },
                    points = d.optJSONArray("points").objects().map { p ->
                        Point(p.optLong("at"), p.optString("type"), p.doubleOrNull("lat"), p.doubleOrNull("lon"),
                            p.optString("plate"), p.optBoolean("inspection"))
                    }
                )
            }
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }
    private fun JSONObject?.ints(): Map<String, Int> = this?.keys()?.asSequence()?.associateWith { optInt(it) }.orEmpty()
    private fun JSONObject?.strings(): Map<String, String> = this?.keys()?.asSequence()?.associateWith { optString(it) }.orEmpty()
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
}

suspend fun ServerApi.crewReport(token: String, from: Long, to: Long): ApiResult<CrewWork.Report> =
    get("manage/crew/report&from=$from&to=$to", token, CrewWork::parse)

/** The overtime the manager approves for one person over the same period; blank hours clear it. */
suspend fun ServerApi.saveCrewOvertime(token: String, memberKey: String, hours: String, note: String, from: Long, to: Long): ApiResult<Unit> =
    post("manage/crew/overtime", JSONObject().put("member_key", memberKey).put("hours", hours.trim()).put("note", note.trim())
        .put("from", from).put("to", to), token) { }

/** The route of the Excel (`xlsx`) or Word (`docx`) file; [memberKey] for one person only. */
fun crewExportRoute(from: Long, to: Long, format: String, memberKey: String?): String =
    "manage/crew/export&from=$from&to=$to&format=$format" +
        (memberKey?.let { "&member=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
