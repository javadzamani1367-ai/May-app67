package ir.ilam.inspection.field.thermal

import ir.ilam.inspection.field.data.FieldDrafts
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * What a thermal inspection carries beyond its files, made when it is
 * finished: one JSON sidecar per thermal file — everything known about it,
 * including the SHA-256 of the untouched original — and the route, gzipped.
 * Made again from scratch if a finished draft is reopened, so nothing stale
 * is sent.
 */
class ThermalFinisher(private val drafts: FieldDrafts) {

    suspend fun prepare(item: FieldItemEntity, payload: ThermalPayload, userCode: String, deviceCode: String, trackFile: File) {
        val existing = drafts.files(item.id)
        existing.filter { it.role == FileRole.SIDECAR || it.role == FileRole.TRACK }.forEach { drafts.removeFile(it) }
        val now = System.currentTimeMillis()
        existing.filter { it.role == FileRole.THERMAL }.forEach { original ->
            val exif = existing.firstOrNull { it.role == FileRole.THERMAL_EXIF && it.capturedAt == original.capturedAt }
            val json = sidecar(item, original, exif, payload.files[original.id], userCode, deviceCode)
            val file = drafts.newFile(item.id, "json")
            file.writeText(json.toString(2))
            drafts.attach(item.id, file, FileRole.SIDECAR, "application/json", now,
                original.latitude, original.longitude, original.accuracy, original.locationUncertain)
        }
        if (trackFile.exists() && trackFile.length() > 0) {
            val packed = drafts.newFile(item.id, "gz")
            GZIPOutputStream(packed.outputStream()).use { out -> trackFile.inputStream().use { it.copyTo(out) } }
            drafts.attach(item.id, packed, FileRole.TRACK, "application/gzip", now, null, null, null)
        }
    }

    private fun sidecar(
        item: FieldItemEntity,
        original: FieldFileEntity,
        exif: FieldFileEntity?,
        info: ThermalFileInfo?,
        userCode: String,
        deviceCode: String
    ): JSONObject = JSONObject().apply {
        put("item_id", item.id)
        putOpt("tracking_code", item.trackingCode)
        put("file_id", original.id)
        put("original_name", info?.name.orEmpty())
        put("original_sha256", original.sha256)
        put("original_size", original.size)
        put("mime", original.mime)
        putOpt("exif_copy_id", exif?.id)
        putOpt("exif_copy_sha256", exif?.sha256)
        original.capturedAt?.let {
            put("captured_at", it)
            put("captured_at_utc", ThermalExport.iso(it))
        }
        putOpt("latitude", original.latitude)
        putOpt("longitude", original.longitude)
        putOpt("accuracy_m", original.accuracy)
        put("location_uncertain", original.locationUncertain)
        put("asset_type", when (original.assetType) { 0 -> "pole"; 1 -> "panel"; else -> JSONObject.NULL })
        putOpt("plate", original.plate)
        putOpt("note", original.note)
        putOpt("address", item.address)
        info?.temperatures?.takeIf { !it.isEmpty() }?.let { put("temperatures_c", it.toJson()) }
        put("user_code", userCode)
        put("device_code", deviceCode)
    }
}
