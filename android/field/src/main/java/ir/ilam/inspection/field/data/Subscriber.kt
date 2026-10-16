package ir.ilam.inspection.field.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A subscriber fed from a pole whose current was found abnormal: where the
 * house is, who it is, what was measured, and the evidence. Photos and videos
 * are the item's files, named here by capture time (as a pole's photos are).
 */
data class Subscriber(
    val id: String = UUID.randomUUID().toString(),
    val threePhase: Boolean = false,
    /** Single phase: [PhaseReading.r] only. */
    val amps: PhaseReading = PhaseReading(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    val positionAt: Long? = null,
    val observerLat: Double? = null,
    val observerLon: Double? = null,
    val observerAccuracy: Double? = null,
    /** Up to [MAX_POSTAL_CODES] ten-digit postal codes: a building can have many. */
    val postalCodes: List<String> = emptyList(),
    val fileNumber: String = "",
    val subscription: String = "",
    /** Person, building or commercial panel name. */
    val name: String = "",
    val ampPhotos: List<Long> = emptyList(),
    val ampVideos: List<Long> = emptyList(),
    val buildingPhotos: List<Long> = emptyList()
) {
    val hasPosition: Boolean get() = latitude != null && longitude != null

    /** The phase values that were typed, as numbers. */
    fun values(): List<Double> =
        (if (threePhase) listOf(amps.r, amps.s, amps.t) else listOf(amps.r)).mapNotNull { it.toDoubleOrNull() }

    val ampsComplete: Boolean get() = values().size == (if (threePhase) 3 else 1)

    /** Above the manager's threshold on any phase. */
    fun abnormal(singleA: Int, threeA: Int): Boolean = values().any { it > if (threePhase) threeA else singleA }

    fun allMedia(): List<Long> = ampPhotos + ampVideos + buildingPhotos

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("phase", if (threePhase) 3 else 1)
        put("amps", JSONObject().apply {
            amps.r.toDoubleOrNull()?.let { put("r", it) }
            if (threePhase) {
                amps.s.toDoubleOrNull()?.let { put("s", it) }
                amps.t.toDoubleOrNull()?.let { put("t", it) }
            }
        })
        putOpt("lat", latitude)
        putOpt("lon", longitude)
        putOpt("acc", accuracy?.let { Math.round(it * 10) / 10.0 })
        putOpt("at", positionAt)
        putOpt("olat", observerLat)
        putOpt("olon", observerLon)
        putOpt("oacc", observerAccuracy?.let { Math.round(it * 10) / 10.0 })
        val codes = postalCodes.map { latinDigits(it) }.filter { it.isNotEmpty() }
        if (codes.isNotEmpty()) put("postal_codes", JSONArray(codes))
        if (fileNumber.isNotBlank()) put("file_number", fileNumber.trim())
        if (subscription.isNotBlank()) put("subscription", subscription.trim())
        if (name.isNotBlank()) put("name", name.trim())
        if (ampPhotos.isNotEmpty()) put("amp_photos", JSONArray(ampPhotos))
        if (ampVideos.isNotEmpty()) put("amp_videos", JSONArray(ampVideos))
        if (buildingPhotos.isNotEmpty()) put("building_photos", JSONArray(buildingPhotos))
    }

    companion object {
        const val MAX_POSTAL_CODES = 20

        /** Ten digits, Persian or Latin; an empty field is not an error. */
        fun validPostalCode(code: String): Boolean = code.isBlank() || latinDigits(code).matches(Regex("\\d{10}"))

        fun latinDigits(text: String): String = text.trim().map { c ->
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        }.joinToString("")

        fun fromJson(o: JSONObject): Subscriber {
            val amps = o.optJSONObject("amps") ?: JSONObject()
            fun num(key: String): String {
                if (!amps.has(key) || amps.isNull(key)) return ""
                val v = amps.optDouble(key)
                return if (v.isNaN()) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
            }
            fun double(key: String): Double? = if (o.has(key) && !o.isNull(key)) o.optDouble(key).takeIf { !it.isNaN() } else null
            fun longs(key: String): List<Long> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.optLong(it) }.filter { it > 0 }
            }
            val codes = o.optJSONArray("postal_codes")
            return Subscriber(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                threePhase = o.optInt("phase", 1) == 3,
                amps = PhaseReading(num("r"), num("s"), num("t")),
                latitude = double("lat"),
                longitude = double("lon"),
                accuracy = double("acc"),
                positionAt = if (o.has("at") && !o.isNull("at")) o.optLong("at") else null,
                observerLat = double("olat"),
                observerLon = double("olon"),
                observerAccuracy = double("oacc"),
                postalCodes = (0 until (codes?.length() ?: 0)).map { codes!!.optString(it) },
                fileNumber = o.optString("file_number"),
                subscription = o.optString("subscription"),
                name = o.optString("name"),
                ampPhotos = longs("amp_photos"),
                ampVideos = longs("amp_videos"),
                buildingPhotos = longs("building_photos")
            )
        }
    }
}
