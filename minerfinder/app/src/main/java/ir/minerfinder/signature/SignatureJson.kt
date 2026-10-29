package ir.minerfinder.signature

import ir.minerfinder.core.signature.Signature
import ir.minerfinder.core.signature.SignatureFeatures
import ir.minerfinder.data.SignatureEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * The exchange format between inspectors:
 * {"format":"minerfinder-signatures","version":1,"signatures":[{...}]}.
 * A file with one bad entry still imports the good ones.
 */
object SignatureJson {
    const val FORMAT = "minerfinder-signatures"

    fun write(signatures: List<Signature>): String {
        val list = JSONArray()
        signatures.forEach { s ->
            list.put(JSONObject().apply {
                put("id", s.id)
                put("label", s.label)
                put("model", s.model)
                put("note", s.note)
                put("createdAt", s.createdAtMs)
                s.fundamentalHz?.let { put("fundamentalHz", it) }
                put("harmonicsHz", JSONArray().apply { s.harmonicsHz.forEach { put(round(it)) } })
                put("bands", JSONArray().apply { s.bands.forEach { put(round(it)) } })
            })
        }
        return JSONObject().put("format", FORMAT).put("version", 1).put("signatures", list).toString(2)
    }

    /** Parsed signatures; throws IllegalArgumentException when the text is not this format at all. */
    fun read(text: String): List<Signature> {
        val root = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("not JSON") }
        require(root.optString("format") == FORMAT) { "not a signature file" }
        val list = root.optJSONArray("signatures") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i -> runCatching { parse(list.getJSONObject(i)) }.getOrNull() }
    }

    private fun parse(o: JSONObject): Signature? {
        val bandsJson = o.getJSONArray("bands")
        if (bandsJson.length() != SignatureFeatures.BANDS) return null
        val harmonics = o.optJSONArray("harmonicsHz")
        return Signature(
            id = o.getString("id"),
            label = o.optString("label"),
            model = o.optString("model"),
            note = o.optString("note"),
            createdAtMs = o.optLong("createdAt"),
            fundamentalHz = if (o.has("fundamentalHz")) o.getDouble("fundamentalHz") else null,
            harmonicsHz = harmonics?.let { a -> (0 until a.length()).map { a.getDouble(it) } } ?: emptyList(),
            bands = DoubleArray(bandsJson.length()) { bandsJson.getDouble(it) },
        )
    }

    fun toEntity(s: Signature) = SignatureEntity(
        id = s.id, label = s.label, model = s.model, note = s.note, createdAt = s.createdAtMs,
        fundamentalHz = s.fundamentalHz,
        harmonicsCsv = s.harmonicsHz.joinToString(",") { round(it).toString() },
        bandsCsv = s.bands.joinToString(",") { round(it).toString() },
    )

    fun fromEntity(e: SignatureEntity) = Signature(
        id = e.id, label = e.label, model = e.model, note = e.note, createdAtMs = e.createdAt,
        fundamentalHz = e.fundamentalHz,
        harmonicsHz = e.harmonicsCsv.split(',').mapNotNull { it.toDoubleOrNull() },
        bands = e.bandsCsv.split(',').mapNotNull { it.toDoubleOrNull() }.toDoubleArray(),
    )

    private fun round(v: Double) = Math.round(v * 1000.0) / 1000.0
}
