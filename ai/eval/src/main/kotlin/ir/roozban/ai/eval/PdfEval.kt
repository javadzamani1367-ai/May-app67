package ir.roozban.ai.eval

import ir.roozban.core.documents.Block
import ir.roozban.core.documents.DocxWriter
import ir.roozban.core.documents.Glyph
import ir.roozban.core.documents.Layout
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File

/**
 * PDF → Word with the app's layout code on desktop PDFBox (the app uses the Android port, same
 * API). Also writes the plain PDFBox text next to it for comparison.
 *
 * usage: PdfEval <in.pdf> <out.docx>   (prints pages, seconds and peak memory)
 */
fun main(args: Array<String>) {
    val (input, output) = args
    val start = System.currentTimeMillis()
    var peak = 0L
    PDDocument.load(File(input)).use { doc ->
        DocxWriter(File(output).outputStream().buffered()).use { docx ->
            for (i in 1..doc.numberOfPages) {
                val glyphs = GlyphStripper.page(doc, i)
                val page = doc.getPage(i - 1).mediaBox
                val lines = Layout.lines(glyphs)
                docx.page(Layout.paragraphs(lines, page.lowerLeftX + 20, page.upperRightX - 20).map { Block.Text(it) })
                val rt = Runtime.getRuntime()
                peak = maxOf(peak, rt.totalMemory() - rt.freeMemory())
            }
        }
        val plain = PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        File("$output.plain.txt").writeText(plain)
        println("PDF pages=${doc.numberOfPages} seconds=${"%.1f".format((System.currentTimeMillis() - start) / 1000.0)} peakMB=${peak / 1_000_000}")
    }
}

/** Collects every glyph of a page with its position and font. */
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
