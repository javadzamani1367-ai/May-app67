package ir.ilam.inspection.field.data

import ir.ilam.inspection.util.PersianNumbers
import org.json.JSONArray
import org.json.JSONObject

/**
 * One earlier reading of a panel's main breaker or feeder, a pole, or a thermal
 * picture of a plate, as the server keeps it. [current] marks the reading being
 * typed now, shown first so the comparison starts from this visit.
 */
data class HistoryRow(
    val at: Long,
    val r: Double? = null,
    val s: Double? = null,
    val t: Double? = null,
    val n: Double? = null,
    val tempMax: Double? = null,
    val tempBoxMax: Double? = null,
    val tempBoxAvg: Double? = null,
    val fileId: String? = null,
    val anomaly: Boolean = false,
    val current: Boolean = false
) {
    fun phase(index: Int): Double? = when (index) { 0 -> r; 1 -> s; else -> t }
    val hasTemperatures: Boolean get() = tempMax != null || tempBoxMax != null || tempBoxAvg != null
}

/** What a history is of: panel or pole, its plate, and which part of it. */
data class HistoryTarget(val asset: Int, val plate: String, val part: String) {
    val plateKey: String get() = History.plateKey(plate)

    companion object {
        fun main(plate: String) = HistoryTarget(History.PANEL, plate, History.MAIN)
        fun feeder(plate: String, letter: String) = HistoryTarget(History.PANEL, plate, letter)
        fun pole(plate: String) = HistoryTarget(History.POLE, plate, History.POLE_PART)
        fun thermal(asset: Int, plate: String) = HistoryTarget(asset, plate, History.THERMAL)
    }
}

object History {
    const val POLE = 0
    const val PANEL = 1
    const val MAIN = "main"
    const val POLE_PART = "pole"
    const val THERMAL = "thermal"
    /** Thermal pictures the phone keeps of each plate, for comparing without signal. */
    const val IMAGES_PER_PLATE = 3

    /** The server's rule: Latin digits, capitals, letters and digits only — «۱۲-۳۴۵» is 12345. */
    fun plateKey(plate: String): String =
        PersianNumbers.toLatin(plate.trim()).uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(64)

    fun rows(array: JSONArray?): List<HistoryRow> = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }.map(::row)

    fun row(o: JSONObject) = HistoryRow(
        at = o.optLong("at"), r = o.num("r"), s = o.num("s"), t = o.num("t"), n = o.num("n"),
        tempMax = o.num("temp_max"), tempBoxMax = o.num("temp_box_max"), tempBoxAvg = o.num("temp_box_avg"),
        fileId = o.optString("file_id").takeIf { it.isNotBlank() && it != "null" }, anomaly = o.optBoolean("anomaly")
    )

    fun toJson(rows: List<HistoryRow>): String = JSONArray(rows.map { r ->
        JSONObject().put("at", r.at).putOpt("r", r.r).putOpt("s", r.s).putOpt("t", r.t).putOpt("n", r.n)
            .putOpt("temp_max", r.tempMax).putOpt("temp_box_max", r.tempBoxMax).putOpt("temp_box_avg", r.tempBoxAvg)
            .putOpt("file_id", r.fileId).put("anomaly", r.anomaly)
    }).toString()

    /** Percent change from [before]; null when either is missing or the base is zero. */
    fun changePct(now: Double?, before: Double?): Double? =
        if (now == null || before == null || before <= 0.0) null else (now - before) / before * 100

    /**
     * The server's abnormal rise, for the reading being typed: more than [pct] percent
     * and [minA] amps above the last reading or the average of the last five.
     */
    fun isRise(value: Double?, earlier: List<Double>, pct: Int, minA: Int): Boolean {
        if (value == null || earlier.isEmpty()) return false
        val average = earlier.take(5).average()
        return listOf(earlier.first(), average).any { base -> value - base >= minA && value > base * (1 + pct / 100.0) }
    }

    private fun JSONObject.num(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
