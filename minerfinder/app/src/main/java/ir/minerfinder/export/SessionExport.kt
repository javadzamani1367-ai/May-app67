package ir.minerfinder.export

import ir.minerfinder.core.geo.Intersection
import ir.minerfinder.data.BearingEntity
import ir.minerfinder.data.SessionEntity
import ir.minerfinder.data.TrackPointEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** CSV and JSON of one session. Plain functions of the data, so they are unit-tested. */
object SessionExport {

    private fun iso(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    /**
     * One row per logged point and per bearing ("type" tells them apart), one
     * column per source that appears anywhere in the session.
     */
    fun csv(points: List<TrackPointEntity>, bearings: List<BearingEntity>): String {
        val sourceIds = points.flatMap { JSONObject(it.sourcesJson).keys().asSequence().toList() }.distinct().sorted()
        val sb = StringBuilder()
        sb.append("type,time_utc,time_ms,lat,lon,accuracy_m,probability,confidence,bearing_deg,sharpness")
        sourceIds.forEach { sb.append(',').append(it) }
        sb.append('\n')
        for (p in points) {
            val sources = JSONObject(p.sourcesJson)
            sb.append("point,").append(iso(p.time)).append(',').append(p.time).append(',')
                .append(num(p.lat, 7)).append(',').append(num(p.lon, 7)).append(',')
                .append(num(p.accuracyM.toDouble(), 1)).append(',').append(num(p.probability.toDouble(), 1)).append(',')
                .append(num(p.confidence.toDouble(), 3)).append(",,")
            sourceIds.forEach { id -> sb.append(',').append(if (sources.has(id)) num(sources.getDouble(id), 1) else "") }
            sb.append('\n')
        }
        for (b in bearings) {
            sb.append("bearing,").append(iso(b.time)).append(',').append(b.time).append(',')
                .append(num(b.lat, 7)).append(',').append(num(b.lon, 7)).append(",,,,")
                .append(num(b.bearingDeg, 1)).append(',').append(num(b.sharpness, 3))
            repeat(sourceIds.size) { sb.append(',') }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun json(
        session: SessionEntity,
        points: List<TrackPointEntity>,
        bearings: List<BearingEntity>,
        estimate: Intersection?,
        appVersion: String,
    ): String {
        val root = JSONObject()
        root.put("format", "minerfinder-session")
        root.put("version", 1)
        root.put("app", appVersion)
        root.put("session", JSONObject().apply {
            put("id", session.id)
            put("startedAt", iso(session.startedAt))
            session.endedAt?.let { put("endedAt", iso(it)) }
            put("note", session.note)
        })
        root.put("points", JSONArray().apply {
            points.forEach { p ->
                put(JSONObject().apply {
                    put("time", iso(p.time)); put("lat", p.lat); put("lon", p.lon)
                    put("accuracyM", p.accuracyM.toDouble()); put("probability", p.probability.toDouble())
                    put("confidence", p.confidence.toDouble()); put("sources", JSONObject(p.sourcesJson))
                })
            }
        })
        root.put("bearings", JSONArray().apply {
            bearings.forEach { b ->
                put(JSONObject().apply {
                    put("time", iso(b.time)); put("lat", b.lat); put("lon", b.lon)
                    put("bearingDeg", b.bearingDeg); put("sharpness", b.sharpness)
                })
            }
        })
        estimate?.let {
            root.put("estimate", JSONObject().apply {
                put("lat", it.point.lat); put("lon", it.point.lon); put("rmsMeters", it.rmsMeters); put("lines", it.linesUsed)
            })
        }
        return root.toString(2)
    }

    private fun num(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)
}
