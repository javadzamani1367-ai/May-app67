package ir.ilam.inspection.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** What is burned onto every photo so a picture can never lose its context. */
data class PhotoStamp(
    val trackingCode: String,
    val expertCode: String,
    val capturedAt: Long,
    val latitude: Double?,
    val longitude: Double?
)

/**
 * Downscales, compresses and stamps captured photos. Bounds come from the
 * media rules: longest edge 1920 px, JPEG quality 85.
 */
class MediaProcessor(private val typeface: Typeface?) {

    fun processPhoto(source: File, target: File, stamp: PhotoStamp, quality: Int = QUALITY): Boolean {
        val decoded = decodeScaled(source) ?: return false
        val rotated = applyExifRotation(decoded, source)
        val stamped = drawStamp(rotated, stamp)
        FileOutputStream(target).use { out ->
            stamped.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), out)
        }
        if (stamped !== rotated) stamped.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
        return target.exists() && target.length() > 0
    }

    private fun decodeScaled(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > MAX_EDGE * 2) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val edge = maxOf(decoded.width, decoded.height)
        if (edge <= MAX_EDGE) return decoded
        val scale = MAX_EDGE.toFloat() / edge
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun applyExifRotation(bitmap: Bitmap, source: File): Bitmap {
        val degrees = when (
            ExifInterface(source.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun drawStamp(source: Bitmap, stamp: PhotoStamp): Bitmap {
        val target = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        val canvas = Canvas(target)
        val lines = stampLines(stamp)
        val textSize = target.height * TEXT_RATIO
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = this@MediaProcessor.typeface ?: Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val padding = textSize * 0.5f
        val bandHeight = lines.size * textSize * 1.35f + padding
        val band = Paint().apply { color = Color.argb(150, 0, 0, 0) }
        canvas.drawRect(0f, target.height - bandHeight, target.width.toFloat(), target.height.toFloat(), band)
        var y = target.height - bandHeight + textSize + padding * 0.3f
        for (line in lines) {
            canvas.drawText(line, target.width - padding, y, text)
            y += textSize * 1.35f
        }
        return target
    }

    private fun stampLines(stamp: PhotoStamp): List<String> {
        val position = if (stamp.latitude != null && stamp.longitude != null) {
            PersianNumbers.toPersian("%.5f , %.5f".format(stamp.latitude, stamp.longitude))
        } else {
            null
        }
        return listOfNotNull(
            PersianDate.formatWithTime(stamp.capturedAt),
            position,
            PersianNumbers.toPersian(stamp.trackingCode + "  /  " + stamp.expertCode)
        )
    }

    private companion object {
        const val MAX_EDGE = 1920
        const val QUALITY = 85
        const val TEXT_RATIO = 0.028f
    }
}
