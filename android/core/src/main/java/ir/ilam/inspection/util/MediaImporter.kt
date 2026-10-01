package ir.ilam.inspection.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** A file brought in from the gallery, ready to be recorded against a case. */
data class ImportedMedia(val file: File, val isVideo: Boolean, val capturedAt: Long)

/**
 * Brings existing photos and videos into the case. Photos go through the same
 * downscale, compression and stamping as captured ones, so an imported shot
 * weighs the same and carries the same context; videos are copied byte for
 * byte, because re-encoding them on a phone is what actually loses quality.
 */
class MediaImporter(
    private val context: Context,
    private val files: FileStore,
    private val processor: MediaProcessor
) {

    fun import(uri: Uri, reportId: String, stamp: PhotoStamp, quality: Int): ImportedMedia? {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isVideo = mime.startsWith("video")
        val extension = if (isVideo) "mp4" else "jpg"
        val target = files.newMediaFile(reportId, extension)

        if (isVideo) {
            val copied = copy(uri, target)
            return if (copied) ImportedMedia(target, true, sourceTime(uri, null)) else null
        }

        val staging = files.newMediaFile(reportId, "import.jpg")
        if (!copy(uri, staging)) return null
        val capturedAt = sourceTime(uri, staging)
        val ok = processor.processPhoto(
            source = staging,
            target = target,
            stamp = stamp.copy(capturedAt = capturedAt),
            quality = quality
        )
        staging.delete()
        return if (ok) ImportedMedia(target, false, capturedAt) else null
    }

    private fun copy(uri: Uri, target: File): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)

    /**
     * When the photo was actually taken, not when it was imported: stamping an
     * old photo with today's date would put a false date on the record.
     */
    private fun sourceTime(uri: Uri, staged: File?): Long {
        val fromExif = staged?.let { file ->
            runCatching {
                ExifInterface(file.absolutePath)
                    .getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?.let { EXIF_FORMAT.parse(it)?.time }
            }.getOrNull()
        }
        if (fromExif != null && fromExif > 0) return fromExif

        val fromStore = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_TAKEN)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
            } ?: 0L
        }.getOrDefault(0L)
        if (fromStore > 0) return fromStore

        return System.currentTimeMillis()
    }

    private companion object {
        val EXIF_FORMAT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    }
}
