package ir.roozban.feature.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.core.documents.Block
import ir.roozban.core.documents.DocxWriter
import ir.roozban.core.documents.Glyph
import ir.roozban.core.documents.Layout
import ir.roozban.core.documents.Line
import ir.roozban.core.documents.Word
import ir.roozban.feature.tools.ocr.OcrEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of one conversion. */
data class PdfProgress(val page: Int, val pages: Int, val scannedPages: Int)

/**
 * PDF → Word on the phone, page by page (no page limit, little memory): the text layer is read
 * with its positions and rebuilt into Persian lines and paragraphs; a page without text (a scan)
 * is rendered and read with OCR.
 */
@Singleton
class PdfConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocr: OcrEngine,
) {
    private var pdfboxReady = false

    /** Scanned pages can be read (the OCR language data is installed). */
    fun hasOcr(): Boolean = ocr.languages() != null

    suspend fun convert(input: Uri, output: Uri, onProgress: (PdfProgress) -> Unit): PdfProgress {
        if (!pdfboxReady) {
            PDFBoxResourceLoader.init(context)
            pdfboxReady = true
        }
        // Both readers need a file: copy the PDF to the app's cache once.
        val file = File(context.cacheDir, "pdf/input.pdf").apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(input)?.use { i -> file.outputStream().use { i.copyTo(it) } }
            ?: throw IllegalStateException("فایل PDF باز نشد.")
        var scanned = 0
        var total = 0
        try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                total = doc.numberOfPages
                val renderer = lazy { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }
                val out = context.contentResolver.openOutputStream(output) ?: throw IllegalStateException("فایل خروجی ساخته نشد.")
                out.buffered().use { stream ->
                    DocxWriter(stream).use { docx ->
                        for (n in 1..total) {
                            currentCoroutineContext().ensureActive()
                            val box = doc.getPage(n - 1).cropBox
                            val glyphs = GlyphStripper.page(doc, n)
                            val blocks = if (glyphs.count { !it.text.isBlank() } >= MIN_TEXT_GLYPHS) {
                                Layout.paragraphs(Layout.lines(glyphs), box.lowerLeftX + MARGIN, box.upperRightX - MARGIN).map { Block.Text(it) }
                            } else if (ocr.languages() != null) {
                                scanned++
                                ocrPage(renderer.value, n - 1)
                            } else {
                                scanned++
                                emptyList()
                            }
                            docx.page(blocks)
                            onProgress(PdfProgress(n, total, scanned))
                        }
                    }
                }
                if (renderer.isInitialized()) renderer.value.close()
            }
        } finally {
            file.delete()
        }
        return PdfProgress(total, total, scanned)
    }

    /** A scanned page: rendered at about 300 dpi, read line by line, then laid out like text. */
    private suspend fun ocrPage(renderer: PdfRenderer, index: Int): List<Block> {
        val page = renderer.openPage(index)
        val scale = SCAN_DPI / 72f
        val w = (page.width * scale).toInt().coerceAtMost(MAX_SCAN_SIDE)
        val h = (page.height * w / page.width.toFloat()).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        page.close()
        val lines = try {
            ocr.recognize(bitmap)
        } finally {
            bitmap.recycle()
        }
        val asLines = lines.map { l ->
            val size = (l.bottom - l.top) * 0.8f
            val words = l.text.split(' ').filter { it.isNotBlank() }.map { Word(it, l.left.toFloat(), l.right.toFloat(), size, false, false) }
            Line(words, l.bottom.toFloat(), l.left.toFloat(), l.right.toFloat(), size, Layout.isRtl(l.text))
        }
        return Layout.paragraphs(asLines, w * 0.05f, w * 0.95f).map { Block.Text(it) }
    }

    /** Every glyph of a page with its position and font. */
    private class GlyphStripper : PDFTextStripper() {
        val glyphs = ArrayList<Glyph>()

        override fun processTextPosition(text: TextPosition) {
            val font = text.font
            val name = font?.name.orEmpty()
            val weight = font?.fontDescriptor?.fontWeight ?: 0f
            glyphs += Glyph(
                text = text.unicode ?: return,
                x = text.xDirAdj,
                y = text.yDirAdj,
                width = text.widthDirAdj,
                size = text.fontSizeInPt.takeIf { it > 0 } ?: text.heightDir,
                bold = weight >= 600 || font?.fontDescriptor?.isForceBold == true || name.contains("Bold", ignoreCase = true),
                italic = name.contains("Italic", ignoreCase = true) || name.contains("Oblique", ignoreCase = true),
            )
        }

        companion object {
            fun page(doc: PDDocument, n: Int): List<Glyph> {
                val s = GlyphStripper()
                s.startPage = n
                s.endPage = n
                s.getText(doc)
                return s.glyphs
            }
        }
    }

    private companion object {
        const val MIN_TEXT_GLYPHS = 20
        const val MARGIN = 20f
        const val SCAN_DPI = 300f
        const val MAX_SCAN_SIDE = 2600
    }
}
