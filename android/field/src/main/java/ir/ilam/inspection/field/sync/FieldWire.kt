package ir.ilam.inspection.field.sync

import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * An item and its files as `POST /field/item` expects them. Pure, so the shape
 * the server validates (`Field::problem`) is pinned down by a unit test rather
 * than discovered in a village.
 */
object FieldWire {

    fun item(item: FieldItemEntity, deviceCode: String): JSONObject = JSONObject()
        .put("id", item.id)
        .put("kind", item.kind)
        .put("created_at", item.createdAt)
        .put("updated_at", item.updatedAt)
        .put("device_code", deviceCode)
        .putOpt("priority", item.priority)
        .putOpt("latitude", item.latitude)
        .putOpt("longitude", item.longitude)
        .putOpt("accuracy", item.accuracy)
        .putOpt("address", item.address)
        .putOpt("plate", item.plate)
        .putOpt("description", item.description)
        .putOpt("payload", item.payload?.let { runCatching { JSONObject(it) }.getOrNull() })

    fun files(files: List<FieldFileEntity>): JSONArray = JSONArray().apply {
        files.forEach { file ->
            put(
                JSONObject()
                    .put("id", file.id)
                    .put("role", file.role)
                    .put("mime", file.mime)
                    .put("size", file.size)
                    .put("sha256", file.sha256)
                    .putOpt("captured_at", file.capturedAt)
                    .putOpt("latitude", file.latitude)
                    .putOpt("longitude", file.longitude)
                    .putOpt("accuracy", file.accuracy)
                    .put("location_uncertain", if (file.locationUncertain) 1 else 0)
                    .putOpt("asset_type", file.assetType)
                    .putOpt("plate", file.plate)
                    .putOpt("note", file.note)
            )
        }
    }
}

/**
 * The next piece of a file to send, given how much the server already has.
 * Null when there is nothing left. Pure, and tested: an off-by-one here is a
 * file that never completes or one the server rejects as too long.
 */
object ChunkPlan {
    /** Smaller than the server's limit: on one bar of signal, a chunk that fails costs less. */
    const val CHUNK_BYTES = 512 * 1024

    fun next(size: Long, received: Long, chunk: Int = CHUNK_BYTES): LongRange? {
        if (size <= 0 || received >= size || received < 0) return null
        val end = minOf(size, received + chunk) - 1
        return received..end
    }
}
