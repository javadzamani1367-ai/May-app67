package ir.ilam.inspection.field.thermal

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import ir.ilam.inspection.field.data.FieldDrafts
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.db.FieldFileEntity
import java.io.File
import java.io.FileOutputStream

/** What happened to one gallery file. */
data class ImportResult(val original: FieldFileEntity?, val exifKept: Boolean, val exifDropped: Boolean)

/**
 * Brings a HIKMICRO file into an inspection.
 *
 * The gallery file is only read. Its bytes are copied as they are — that copy
 * is the evidence, with its SHA-256 — and for a photo a second copy gets the
 * route's coordinates in its EXIF. That second copy is kept only if every
 * other byte of it is identical to the original (JpegSegments); otherwise it
 * is deleted and the coordinates live in the sidecar alone.
 */
class ThermalImporter(private val context: Context, private val drafts: FieldDrafts) {

    suspend fun import(itemId: String, media: FoundMedia, at: Located?): ImportResult {
        val mime = if (media.video) MIME_MP4 else MIME_JPEG
        if (media.mime != mime) return ImportResult(null, exifKept = false, exifDropped = false)
        val original = drafts.newFile(itemId, if (media.video) "mp4" else "jpg")
        val copied = runCatching {
            context.contentResolver.openInputStream(Uri.parse(media.uri))?.use { input ->
                FileOutputStream(original).use { input.copyTo(it) }
            } != null
        }.getOrDefault(false)
        if (!copied) return ImportResult(null, exifKept = false, exifDropped = false)

        val entity = drafts.attach(itemId, original, FileRole.THERMAL, mime, media.takenAt,
            at?.latitude, at?.longitude, at?.accuracy, uncertain = at?.uncertain ?: true)
        if (media.video || at == null || entity == null) return ImportResult(entity, exifKept = false, exifDropped = false)

        val exifCopy = drafts.newFile(itemId, "jpg")
        val kept = runCatching {
            original.copyTo(exifCopy, overwrite = true)
            ExifInterface(exifCopy.absolutePath).apply {
                setLatLong(at.latitude, at.longitude)
                saveAttributes()
            }
            JpegSegments.onlyExifChanged(original.readBytes(), exifCopy.readBytes())
        }.getOrDefault(false)
        if (kept) {
            drafts.attach(itemId, exifCopy, FileRole.THERMAL_EXIF, MIME_JPEG, media.takenAt,
                at.latitude, at.longitude, at.accuracy, uncertain = at.uncertain)
        } else {
            exifCopy.delete()
        }
        return ImportResult(entity, exifKept = kept, exifDropped = !kept)
    }

    companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_MP4 = "video/mp4"
    }
}
