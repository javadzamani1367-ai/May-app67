package ir.roozban.core.documents

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class LayoutTest {
    /** Lays [text] out right to left as a PDF would: logical characters, placed from the right edge. */
    private fun rtlLine(text: String, y: Float, right: Float = 500f, size: Float = 12f, bold: Boolean = false): List<Glyph> {
        fun ltr(word: String) = word.none { it in '\u0600'..'\u06EF' || it in '\u06FA'..'\u06FF' }
        // Words grouped into runs; a left-to-right run (Latin, numbers) is drawn left to right as a unit.
        val runs = ArrayList<MutableList<String>>()
        for (w in text.split(" ")) {
            val last = runs.lastOrNull()
            if (last != null && ltr(last.last()) && ltr(w)) last.add(w) else runs.add(mutableListOf(w))
        }
        val out = ArrayList<Glyph>()
        var x = right
        for (run in runs) {
            if (ltr(run.first())) {
                val chars = run.joinToString(" ")
                var lx = x - chars.length * 6f
                x = lx
                for (c in chars) { out += Glyph(c.toString(), lx, y, 6f, size, bold); lx += 6f }
            } else {
                for (c in run.single()) {
                    if (c == '\u200C') { out += Glyph(c.toString(), x, y, 0f, size, bold); continue }
                    x -= 6f
                    out += Glyph(c.toString(), x, y, 6f, size, bold)
                }
            }
            x -= 4f
            out += Glyph(" ", x, y, 4f, size, bold)
        }
        return out
    }

    @Test
    fun `right-to-left lines come back in reading order`() {
        val lines = Layout.lines(rtlLine("سلام دنیا، امروز خوب است", 100f))
        assertThat(lines.single().text).isEqualTo("سلام دنیا، امروز خوب است")
        assertThat(lines.single().rtl).isTrue()
    }

    @Test
    fun `numbers and Latin inside Persian keep their own order`() {
        val lines = Layout.lines(rtlLine("سال ۱۴۰۵ و نسخه PDF Reader آماده است", 100f))
        assertThat(lines.single().text).isEqualTo("سال ۱۴۰۵ و نسخه PDF Reader آماده است")
    }

    @Test
    fun `a half-space stays in place whether the PDF draws in reading order or left to right`() {
        val text = "گونه\u200Cهای دست\u200Cنویس می\u200Cزند"
        val logical = rtlLine(text, 100f)
        assertThat(Layout.lines(logical).single().text).isEqualTo(text)
        assertThat(Layout.lines(logical.reversed()).single().text).isEqualTo(text)
    }

    @Test
    fun `presentation forms become letters`() {
        assertThat(Layout.normalize("ﺳﻼﻡ")).isEqualTo("سلام")
        assertThat(Layout.normalize("ﻯ‏")).isEqualTo("ی")
    }

    @Test
    fun `lines group into paragraphs and a large line is a heading`() {
        val glyphs = rtlLine("عنوان گزارش", 60f, size = 22f, bold = true) +
            rtlLine("این خط اول پاراگراف است که تا انتهای سطر ادامه دارد و بلند است", 110f) +
            rtlLine("و این خط دوم همان پاراگراف است", 126f) +
            rtlLine("پاراگراف تازه پس از فاصله", 170f)
        val lines = Layout.lines(glyphs)
        val paras = Layout.paragraphs(lines, 40f, 500f)
        assertThat(paras.map { it.heading }).containsExactly(1, 0, 0).inOrder()
        assertThat(paras[1].text).isEqualTo("این خط اول پاراگراف است که تا انتهای سطر ادامه دارد و بلند است و این خط دوم همان پاراگراف است")
    }

    @Test
    fun `the docx holds the text right to left, with a page break between pages`() {
        val out = ByteArrayOutputStream()
        DocxWriter(out).use { w ->
            val p = Layout.paragraphs(Layout.lines(rtlLine("صفحهٔ اول & <آزمون>", 100f)), 40f, 500f)
            w.page(p.map { Block.Text(it) })
            w.page(listOf(Block.Image(DocxImage(byteArrayOf(1, 2, 3), 100f, 50f))))
        }
        val entries = HashMap<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                entries[e.name] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertThat(entries.keys).containsAtLeast("[Content_Types].xml", "word/document.xml", "word/media/image1.png", "word/_rels/document.xml.rels")
        val doc = entries.getValue("word/document.xml")
        assertThat(doc).contains("<w:bidi/>")
        assertThat(doc).contains("صفحهٔ اول &amp; &lt;آزمون&gt;")
        assertThat(doc).contains("""w:type="page"""")
        assertThat(entries.getValue("word/_rels/document.xml.rels")).contains("media/image1.png")
    }
}
