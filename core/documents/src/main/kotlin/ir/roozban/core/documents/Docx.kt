package ir.roozban.core.documents

import java.io.OutputStream
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A picture to place in the document, in page order. */
class DocxImage(val png: ByteArray, val widthPt: Float, val heightPt: Float)

/** One block of a page: a paragraph or a picture, top to bottom. */
sealed interface Block {
    data class Text(val paragraph: Paragraph) : Block
    class Image(val image: DocxImage) : Block
}

/**
 * Writes a .docx (Office Open XML) page by page, so any number of pages fits in little memory:
 * right-to-left paragraphs for Persian, headings, bold, alignment, pictures, and a page break
 * between the PDF's pages.
 */
class DocxWriter(out: OutputStream, private val font: String = "Vazirmatn") : AutoCloseable {
    private val zip = ZipOutputStream(out)
    private val body: Writer
    private val images = ArrayList<String>()
    private var pages = 0

    /** Pictures are stored after the text (the document part is still being streamed). */
    private val pending = ArrayList<Pair<String, ByteArray>>()

    init {
        entry("[Content_Types].xml", CONTENT_TYPES)
        entry("_rels/.rels", ROOT_RELS)
        entry("word/styles.xml", styles(font))
        entry("word/settings.xml", SETTINGS)
        zip.putNextEntry(ZipEntry("word/document.xml"))
        body = zip.writer()
        body.write(DOCUMENT_START)
    }

    fun page(blocks: List<Block>) {
        if (pages > 0) body.write("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
        pages++
        for (b in blocks) when (b) {
            is Block.Text -> paragraph(b.paragraph)
            is Block.Image -> image(b.image)
        }
        body.flush()
    }

    private fun paragraph(p: Paragraph) {
        val jc = when (p.align) {
            // In a right-to-left paragraph «start» is the right edge.
            Align.START -> "start"
            Align.END -> "end"
            Align.CENTER -> "center"
            Align.JUSTIFY -> "both"
        }
        body.write("<w:p><w:pPr>")
        if (p.heading > 0) body.write("""<w:pStyle w:val="Heading${p.heading}"/>""")
        if (p.rtl) body.write("<w:bidi/>")
        body.write("""<w:jc w:val="$jc"/></w:pPr>""")
        // Runs: consecutive words with the same weight.
        val words = p.lines.flatMap { it.words }
        var i = 0
        while (i < words.size) {
            var j = i
            while (j + 1 < words.size && words[j + 1].bold == words[i].bold && words[j + 1].italic == words[i].italic) j++
            val text = words.subList(i, j + 1).joinToString(" ") { it.text } + if (j + 1 < words.size) " " else ""
            val halfPoints = (words[i].size * 2).toInt().coerceIn(12, 144)
            body.write("<w:r><w:rPr>")
            if (words[i].bold) body.write("<w:b/><w:bCs/>")
            if (words[i].italic) body.write("<w:i/><w:iCs/>")
            if (p.rtl) body.write("<w:rtl/>")
            if (p.heading == 0) body.write("""<w:sz w:val="$halfPoints"/><w:szCs w:val="$halfPoints"/>""")
            body.write("""</w:rPr><w:t xml:space="preserve">${escape(text)}</w:t></w:r>""")
            i = j + 1
        }
        body.write("</w:p>")
    }

    private fun image(img: DocxImage) {
        val n = images.size + 1
        val name = "image$n.png"
        images += name
        pending += name to img.png
        // Scale to fit the text width (about 16 cm) keeping the proportions; EMU = 12700 per point.
        val maxW = 450f
        val scale = if (img.widthPt > maxW) maxW / img.widthPt else 1f
        val cx = (img.widthPt * scale * 12700).toLong()
        val cy = (img.heightPt * scale * 12700).toLong()
        body.write(
            """<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$n" name="Picture $n"/>""" +
                """<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$n" name="$name"/><pic:cNvPicPr/></pic:nvPicPr>""" +
                """<pic:blipFill><a:blip r:embed="rImg$n"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""" +
                """<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""",
        )
    }

    override fun close() {
        body.write(DOCUMENT_END)
        body.flush()
        zip.closeEntry()
        for ((name, bytes) in pending) {
            zip.putNextEntry(ZipEntry("word/media/$name"))
            zip.write(bytes)
            zip.closeEntry()
        }
        val rels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        rels.append("""<Relationship Id="rStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        rels.append("""<Relationship Id="rSettings" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>""")
        images.forEachIndexed { i, name ->
            rels.append("""<Relationship Id="rImg${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/$name"/>""")
        }
        rels.append("</Relationships>")
        entry("word/_rels/document.xml.rels", rels.toString())
        zip.finish()
        zip.flush()
    }

    private fun entry(name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }

    private fun ZipOutputStream.writer(): Writer = object : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = this@writer.write(String(cbuf, off, len).toByteArray())
        override fun flush() = this@writer.flush()
        override fun close() = Unit
    }

    companion object {
        fun escape(s: String): String = buildString(s.length) {
            for (c in s) when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                // Characters XML 1.0 does not allow.
                c < ' ' && c != '\t' && c != '\n' && c != '\r' -> Unit
                else -> append(c)
            }
        }

        private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/></Types>"""

        private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rDoc" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

        private const val SETTINGS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:defaultTabStop w:val="720"/><w:compat><w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word" w:val="15"/></w:compat></w:settings>"""

        private const val DOCUMENT_START = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>"""

        private const val DOCUMENT_END = """<w:sectPr><w:bidi/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="709" w:footer="709" w:gutter="0"/></w:sectPr></w:body></w:document>"""

        private fun styles(font: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            """<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="$font" w:hAnsi="$font" w:cs="$font"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:lang w:bidi="fa-IR"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="300" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""" +
            """<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>""" +
            (1..3).joinToString("") { l ->
                val sz = listOf(40, 32, 26)[l - 1]
                """<w:style w:type="paragraph" w:styleId="Heading$l"><w:name w:val="heading $l"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="120"/><w:outlineLvl w:val="${l - 1}"/></w:pPr><w:rPr><w:b/><w:bCs/><w:sz w:val="$sz"/><w:szCs w:val="$sz"/></w:rPr></w:style>"""
            } +
            "</w:styles>"
    }
}
