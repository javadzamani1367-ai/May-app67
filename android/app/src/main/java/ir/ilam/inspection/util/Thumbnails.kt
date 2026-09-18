package ir.ilam.inspection.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Small previews for the media list. Decoded down before they reach memory: a
 * visit can hold thirty photos and a phone will not carry thirty full frames.
 */
object Thumbnails {

    fun forPhoto(file: File, maxEdge: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return runCatching {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }.getOrNull()
    }

    fun forVideo(file: File): Bitmap? {
        if (!file.exists()) return null
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(0)
            }
        }.getOrNull()
    }

    /** `use` for a retriever that only learned to close itself on API 29. */
    private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T =
        try {
            block(this)
        } finally {
            runCatching { release() }
        }
}
