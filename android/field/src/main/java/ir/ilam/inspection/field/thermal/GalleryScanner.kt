package ir.ilam.inspection.field.thermal

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Finds the photos and videos HIKMICRO Viewer saved during a session.
 *
 * Only reads: nothing in the gallery is ever written, moved or deleted. The
 * folder filter keeps other apps' pictures out; the time window keeps out
 * thermal files from other days. The time is taken from the file name where
 * HIKMICRO wrote one (to the millisecond), from the gallery's record otherwise.
 */
class GalleryScanner(private val context: Context) {

    fun scan(folder: String, from: Long, to: Long): List<FoundMedia> =
        (query(images(), folder, from, to, video = false) + query(videos(), folder, from, to, video = true))
            .sortedBy { it.takenAt }

    private fun images(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun videos(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    @Suppress("DEPRECATION")
    private fun query(collection: Uri, folder: String, from: Long, to: Long, video: Boolean): List<FoundMedia> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.Images.ImageColumns.DATE_TAKEN)
            if (video) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()
        // The folder: RELATIVE_PATH from Android 10, the full path before it.
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH
        else MediaStore.MediaColumns.DATA
        // A day of slack on the coarse column; the precise window is applied below.
        val selection = "$pathColumn LIKE ? AND ${MediaStore.MediaColumns.DATE_ADDED} >= ?"
        val args = arrayOf("%$folder%", ((from - DAY) / 1000).toString())
        val found = mutableListOf<FoundMedia>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val size = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val added = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)
            val duration = if (video) c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION) else -1
            while (c.moveToNext()) {
                val fileName = c.getString(name) ?: continue
                val fromName = HikmicroNames.capturedAt(fileName)
                val takenAt = fromName ?: c.getLong(taken).takeIf { it > 0 } ?: (c.getLong(added) * 1000)
                val length = if (video) c.getLong(duration).takeIf { it > 0 } else null
                val end = takenAt + (length ?: 0)
                if (end < from || takenAt > to) continue
                found += FoundMedia(
                    uri = ContentUris.withAppendedId(collection, c.getLong(id)).toString(),
                    name = fileName,
                    video = video,
                    mime = c.getString(mime) ?: if (video) "video/mp4" else "image/jpeg",
                    size = c.getLong(size),
                    takenAt = takenAt,
                    durationMillis = length,
                    timeFromName = fromName != null
                )
            }
        }
        return found
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
