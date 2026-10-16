package ir.ilam.inspection.field.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Photos and videos picked from the gallery. Two rules the field asked for:
 * nothing older than [MAX_AGE_MILLIS] (evidence from today, not last month),
 * and nothing sent at full size over the crew's own mobile data — photos are
 * re-encoded like the app's own camera photos, videos go to 720p ([VideoShrinker]).
 */
object GalleryImport {

    sealed interface Result {
        /** Saved to the target; [takenAt] is when it was taken, by its own metadata. */
        data class Ok(val takenAt: Long) : Result
        data class TooOld(val takenAt: Long) : Result
        /** No date in the file or the gallery: its age cannot be shown, so it is not taken. */
        data object NoDate : Result
        data object Failed : Result
    }

    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
    private const val MAX_EDGE = 1920
    private const val QUALITY = 85

    /** The age check on its own: `null` means no date could be found. */
    fun isFresh(takenAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        takenAt <= now + CLOCK_SLACK_MILLIS && now - takenAt <= MAX_AGE_MILLIS

    fun photo(context: Context, uri: Uri, target: File, now: Long = System.currentTimeMillis()): Result {
        val takenAt = photoDate(context, uri) ?: galleryDate(context, uri) ?: return Result.NoDate
        if (!isFresh(takenAt, now)) return Result.TooOld(takenAt)
        return if (runCatching { compress(context, uri, target) }.getOrDefault(false)) Result.Ok(takenAt) else Result.Failed
    }

    /** A video: its date is checked before the (slow) re-encoding starts. */
    suspend fun video(context: Context, uri: Uri, target: File, now: Long = System.currentTimeMillis()): Result {
        val takenAt = videoDate(context, uri) ?: return Result.NoDate
        if (!isFresh(takenAt, now)) return Result.TooOld(takenAt)
        return if (VideoShrinker.shrink(context, uri, target)) Result.Ok(takenAt) else Result.Failed
    }

    /** A video's date, by its own metadata or the gallery's. */
    fun videoDate(context: Context, uri: Uri): Long? = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let(::parseVideoDate)
            } finally {
                release()
            }
        }
    }.getOrNull() ?: galleryDate(context, uri)

    /** EXIF «date taken», in the phone's own time zone (cameras write local time). */
    private fun photoDate(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val text = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            text?.let { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }
        }
    }.getOrNull()

    /** The gallery's own «date taken», for files whose metadata was stripped. */
    private fun galleryDate(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
        }
    }.getOrNull()

    /** «20261016T081530.000Z», as Android writes a video's creation time (UTC). */
    internal fun parseVideoDate(text: String): Long? {
        val clean = text.trim().substringBefore('.').removeSuffix("Z")
        if (clean.length < 15) return null
        return runCatching {
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(clean)?.time
        }.getOrNull()?.takeIf { it > EPOCH_SANITY }
    }

    /** At most [MAX_EDGE] on the long side, upright, JPEG at [QUALITY] — as the app's camera photos are. */
    private fun compress(context: Context, uri: Uri, target: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return false
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val upright = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        FileOutputStream(target).use { upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        return target.length() > 0
    }

    /** A phone clock a few minutes ahead of the camera's is not "from the future". */
    private const val CLOCK_SLACK_MILLIS = 10 * 60 * 1000L
    private const val EPOCH_SANITY = 946_684_800_000L
}

/** What a gallery pick did: taken in, too old, without a date, or unreadable. */
data class ImportOutcome(val added: Int, val tooOld: Int, val noDate: Int, val failed: Int) {
    val rejected: Int get() = tooOld + noDate + failed
}
