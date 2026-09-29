package ir.roozban.feature.tools.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.ai.runtime.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A line of recognized text with its box on the page, in pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Reads Persian text in pictures with Tesseract, on the phone. Needs the language
 * data from [ModelManager] (downloaded once). One page at a time.
 */
@Singleton
class OcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelManager,
) {
    private val lock = Mutex()
    private var api: TessBaseAPI? = null
    private var languages: String? = null

    /** Languages ready to use, or null when the Persian data is missing (see ModelCatalog.ocr). */
    fun languages(): String? = if ("fas" in models.ocrInstalled()) "fas" else null

    suspend fun readImage(uri: Uri): String = withContext(Dispatchers.Default) {
        val bitmap = loadBitmap(uri) ?: throw IllegalStateException("تصویر باز نشد.")
        try {
            recognize(bitmap).joinToString("\n") { it.text }
        } finally {
            bitmap.recycle()
        }
    }

    /** Lines of [bitmap] top to bottom; for scanned PDF pages. */
    suspend fun recognize(bitmap: Bitmap): List<OcrLine> = lock.withLock {
        withContext(Dispatchers.Default) {
            val t = tess()
            t.setImage(bitmap)
            val lines = ArrayList<OcrLine>()
            t.getUTF8Text() // runs recognition
            val it = t.getResultIterator() ?: return@withContext emptyList<OcrLine>()
            it.begin()
            do {
                val text = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)?.trim().orEmpty()
                if (text.isEmpty()) continue
                val r = it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                lines += OcrLine(text, r.left, r.top, r.right, r.bottom)
            } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            it.delete()
            t.clear()
            lines
        }
    }

    private fun tess(): TessBaseAPI {
        val langs = languages() ?: throw IllegalStateException("داده‌های زبان نصب نیست.")
        api?.let { if (languages == langs) return it else it.recycle() }
        val t = TessBaseAPI()
        check(t.init(models.ocrDir.absolutePath, langs, TessBaseAPI.OEM_LSTM_ONLY)) { "آماده‌سازی خواندن متن نشد." }
        t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        // Phone photos: adaptive (Sauvola) thresholding copes with uneven light.
        t.setVariable("thresholding_method", "2")
        t.setVariable("user_defined_dpi", "300")
        t.setVariable("preserve_interword_spaces", "1")
        api = t
        languages = langs
        return t
    }

    fun release() {
        api?.recycle()
        api = null
    }

    /** The picture, upright (EXIF rotation applied) and at most [MAX_SIDE] pixels on its long side. */
    private fun loadBitmap(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (rotation == 0) return bmp
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (rotated != bmp) bmp.recycle()
        return rotated
    }

    private companion object {
        const val MAX_SIDE = 3000
    }
}
