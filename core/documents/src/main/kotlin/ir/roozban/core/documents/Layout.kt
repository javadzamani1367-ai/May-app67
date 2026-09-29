package ir.roozban.core.documents

import java.text.Normalizer
import kotlin.math.abs

/**
 * One character (or ligature) as a PDF page draws it. [x] is the left edge, [y] the baseline,
 * both in points from the top-left of the page.
 */
data class Glyph(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val size: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

data class Word(val text: String, val x0: Float, val x1: Float, val size: Float, val bold: Boolean, val italic: Boolean)

/** A line of text in reading order. */
data class Line(val words: List<Word>, val y: Float, val x0: Float, val x1: Float, val size: Float, val rtl: Boolean) {
    val text: String get() = words.joinToString(" ") { it.text }
    val bold: Boolean get() = words.count { it.bold } * 2 > words.size
}

enum class Align { START, END, CENTER, JUSTIFY }

data class Paragraph(val lines: List<Line>, val align: Align, val heading: Int = 0) {
    val text: String get() = lines.joinToString(" ") { it.text }
    val rtl: Boolean get() = lines.count { it.rtl } * 2 >= lines.size
    val size: Float get() = lines.map { it.size }.average().toFloat()
    val bold: Boolean get() = lines.all { it.bold }
}

/**
 * Rebuilds lines and paragraphs from positioned glyphs, in logical (reading) order: PDFs store
 * Persian text right to left on the page, often as presentation-form glyphs, so text is normalized
 * and each line reordered for its direction.
 */
object Layout {
    fun lines(glyphs: List<Glyph>): List<Line> {
        val normalized = glyphs.mapNotNull { g -> normalize(g.text).takeIf { it.isNotEmpty() }?.let { g.copy(text = it) } }
        // A half-space has no width and shares its neighbours' position, so sorting by x would put
        // it on either side: attach it to the letter it logically follows. Some writers draw in
        // reading order (that letter comes before it in the stream), others left to right (for
        // Persian, the letter after it in the stream).
        val attach = IntArray(normalized.size) { -1 }
        for ((i, g) in normalized.withIndex()) {
            if (g.text != ZWNJ) continue
            val p = (i - 1 downTo 0).firstOrNull { normalized[it].text != ZWNJ }?.takeIf { normalized[it].text.isNotBlank() }
            val n = (i + 1 until normalized.size).firstOrNull { normalized[it].text != ZWNJ }
                ?.takeIf { normalized[it].text.isNotBlank() && p != null && abs(normalized[it].y - normalized[p].y) <= 0.45f * maxOf(normalized[it].size, 1f) }
            attach[i] = if (p != null && n != null && isRtl(normalized[n].text) && normalized[n].x > normalized[p].x + 0.01f) n else p ?: -1
        }
        val suffix = HashMap<Int, String>()
        for (i in attach.indices) if (attach[i] >= 0) suffix[attach[i]] = ZWNJ
        val clean = ArrayList<Glyph>(normalized.size)
        for ((i, g) in normalized.withIndex()) {
            if (g.text == ZWNJ) continue
            clean += suffix[i]?.let { g.copy(text = g.text + it) } ?: g
        }
        if (clean.isEmpty()) return emptyList()
        // Group by baseline, top to bottom.
        val rows = ArrayList<MutableList<Glyph>>()
        for (g in clean.sortedBy { it.y }) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.last().y - g.y) <= 0.45f * maxOf(g.size, row.last().size, 1f)) row += g else rows.add(mutableListOf(g))
        }
        return rows.flatMap { splitColumns(it) }.map { line(it) }.filter { it.words.isNotEmpty() }
    }

    /** A wide horizontal gap inside a row means two columns (or a table's cells). */
    private fun splitColumns(row: List<Glyph>): List<List<Glyph>> {
        val sorted = row.sortedBy { it.x }
        val out = ArrayList<MutableList<Glyph>>()
        for (g in sorted) {
            val cur = out.lastOrNull()
            val prev = cur?.last()
            if (prev != null && g.x - (prev.x + prev.width) > 3.5f * maxOf(g.size, prev.size)) out.add(mutableListOf(g))
            else if (cur == null) out.add(mutableListOf(g)) else cur += g
        }
        return out
    }

    private fun line(row: List<Glyph>): Line {
        // Visual order, left to right; words split at spaces and at gaps.
        val sorted = row.sortedBy { it.x }
        val visualWords = ArrayList<MutableList<Glyph>>()
        for (g in sorted) {
            if (g.text.isBlank()) {
                visualWords.add(mutableListOf())
                continue
            }
            val cur = visualWords.lastOrNull()
            val prev = cur?.lastOrNull()
            val gap = if (prev == null) 0f else g.x - (prev.x + prev.width)
            if (cur == null || (prev != null && gap > 0.22f * maxOf(g.size, 1f))) visualWords.add(mutableListOf(g)) else cur += g
        }
        val words = visualWords.filter { it.isNotEmpty() }.map { w ->
            val rtlWord = w.any { isRtl(it.text) }
            // Inside a right-to-left word the glyphs run right to left.
            val text = if (rtlWord) w.reversed().joinToString("") { it.text } else w.joinToString("") { it.text }
            Word(fixMixed(text), w.first().x, w.last().x + w.last().width, w.map { it.size }.average().toFloat(), w.count { it.bold } * 2 > w.size, w.any { it.italic })
        }
        val rtl = words.sumOf { w -> w.text.count { isRtlChar(it) } } >= words.sumOf { w -> w.text.count { it.isLetter() && !isRtlChar(it) } }
        val ordered = if (rtl) reorderRtl(words) else words
        return Line(ordered, row.map { it.y }.average().toFloat(), sorted.first().x, sorted.last().let { it.x + it.width }, row.map { it.size }.average().toFloat(), rtl)
    }

    /**
     * Words of a right-to-left line: reading order is right to left, except that a run of
     * left-to-right words (Latin, numbers) keeps its own left-to-right order.
     */
    private fun reorderRtl(visual: List<Word>): List<Word> {
        val out = ArrayList<Word>()
        var i = visual.size - 1
        while (i >= 0) {
            if (isLtrWord(visual[i].text)) {
                var j = i
                while (j - 1 >= 0 && isLtrWord(visual[j - 1].text)) j--
                out += visual.subList(j, i + 1)
                i = j - 1
            } else {
                out += visual[i]
                i--
            }
        }
        return out
    }

    /** Digits drawn inside a reversed Persian word («۱۴۰۵» came out «۵۰۴۱»): put them back. */
    private fun fixMixed(text: String): String {
        if (!text.any { isRtlChar(it) }) return text
        return DIGIT_RUN.replace(text) { it.value.reversed() }
    }

    private fun isLtrWord(t: String) = t.none { isRtlChar(it) } && t.any { it.isLetterOrDigit() }

    fun isRtl(s: String) = s.any { isRtlChar(it) }

    /** Persian and Arabic letters; digits (either kind) run left to right. */
    private fun isRtlChar(c: Char) = (c in '֐'..'ࣿ' && c !in '٠'..'٩' && c !in '۰'..'۹') ||
        c in 'יִ'..'﷿' || c in 'ﹰ'..'﻿'

    /**
     * Presentation forms (the shaped glyphs many PDFs store) back to letters; Arabic ی/ک to Persian;
     * no bidi control characters.
     */
    fun normalize(s: String): String {
        var t = if (s.any { it in 'ﭐ'..'﷿' || it in 'ﹰ'..'﻿' }) Normalizer.normalize(s, Normalizer.Form.NFKC) else s
        t = t.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        return t.filterNot { it in '‎'..'‏' || it in '‪'..'‮' || it in '⁦'..'⁩' || it == '﻿' }
    }

    /**
     * Paragraphs: consecutive lines of the same size and alignment, not far apart. A line much
     * larger than the body text is a heading.
     */
    fun paragraphs(lines: List<Line>, pageLeft: Float, pageRight: Float): List<Paragraph> {
        if (lines.isEmpty()) return emptyList()
        val body = lines.map { it.size }.sorted()[lines.size / 2]
        val width = pageRight - pageLeft
        fun align(l: Line): Align {
            val left = l.x0 - pageLeft
            val right = pageRight - l.x1
            return when {
                abs(left - right) < width * 0.06f && left > width * 0.1f -> Align.CENTER
                left < width * 0.04f && right < width * 0.04f -> Align.JUSTIFY
                (l.rtl && right < width * 0.06f) || (!l.rtl && left < width * 0.06f) -> Align.START
                else -> Align.END
            }
        }
        // The usual distance between lines of one paragraph (font sizes in PDFs are not always
        // reliable, the spacing is).
        val gaps = lines.zipWithNext { a, b -> b.y - a.y }.filter { it > 0 }.sorted()
        val spacing = if (gaps.isEmpty()) body * 1.4f else gaps[gaps.size / 3]
        val out = ArrayList<Paragraph>()
        var cur = ArrayList<Line>()
        var prev: Line? = null
        fun flush() {
            if (cur.isEmpty()) return
            val size = cur.map { it.size }.average().toFloat()
            val heading = when {
                size >= body * 1.6f -> 1
                size >= body * 1.25f -> 2
                cur.all { it.bold } && cur.size == 1 && cur[0].words.size <= 12 -> 3
                else -> 0
            }
            val aligns = cur.map(::align)
            val a = if (Align.JUSTIFY in aligns && cur.size > 1) Align.JUSTIFY else aligns.groupingBy { it }.eachCount().maxBy { it.value }.key
            out += Paragraph(cur, a, heading)
            cur = ArrayList()
        }
        for (l in lines) {
            val p = prev
            val newPara = p == null ||
                l.y - p.y > spacing * 1.3f ||
                abs(l.size - p.size) > 0.15f * maxOf(l.size, p.size) ||
                l.bold != p.bold && (l.words.size < 12 || p.words.size < 12) ||
                // The previous line ended short of the margin: its paragraph ended there.
                (p.rtl && p.x0 - pageLeft > width * 0.25f && align(p) != Align.CENTER) ||
                (!p.rtl && pageRight - p.x1 > width * 0.25f && align(p) != Align.CENTER)
            if (newPara) flush()
            cur += l
            prev = l
        }
        flush()
        return out
    }

    private val DIGIT_RUN = Regex("[0-9۰-۹٠-٩]{2,}")
    private const val ZWNJ = "\u200C"
}
