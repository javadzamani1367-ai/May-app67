package ir.ilam.inspection.export

/**
 * WordprocessingML fragments. Every paragraph carries `w:bidi` and every run
 * `w:rtl`, which is what makes Word lay the report out right to left.
 */
object WordDocumentXml {

    const val NAMESPACES =
        "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""

    /** Half points: Word sizes are doubled, so 24 means 12pt. */
    private const val BODY_SIZE = 22
    private const val TITLE_SIZE = 30
    private const val HEADING_SIZE = 26

    fun document(body: String): String =
        "<w:document $NAMESPACES><w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"850\" w:right=\"700\" w:bottom=\"850\" w:left=\"700\"/>" +
            "<w:bidi/></w:sectPr></w:body></w:document>"

    fun paragraph(text: String, size: Int = BODY_SIZE, bold: Boolean = false, centered: Boolean = false): String {
        val alignment = if (centered) "center" else "right"
        return "<w:p><w:pPr><w:bidi/><w:jc w:val=\"$alignment\"/></w:pPr>" +
            run(text, size, bold) + "</w:p>"
    }

    fun title(text: String): String = paragraph(text, TITLE_SIZE, bold = true, centered = true)

    fun heading(text: String): String = paragraph(text, HEADING_SIZE, bold = true)

    fun run(text: String, size: Int = BODY_SIZE, bold: Boolean = false): String {
        val properties = buildString {
            append("<w:rPr><w:rtl/>")
            append("<w:rFonts w:ascii=\"Vazirmatn\" w:hAnsi=\"Vazirmatn\" w:cs=\"Vazirmatn\"/>")
            if (bold) append("<w:b/><w:bCs/>")
            append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>")
            append("</w:rPr>")
        }
        val lines = text.split("\n")
        val content = lines.mapIndexed { index, line ->
            val prefix = if (index == 0) "" else "<w:br/>"
            prefix + "<w:t xml:space=\"preserve\">" + line + "</w:t>"
        }.joinToString("")
        return "<w:r>$properties$content</w:r>"
    }

    /** A table whose first row is a header; `widths` are fiftieths of a percent. */
    fun table(rows: List<List<String>>, headerRow: Boolean = true): String {
        if (rows.isEmpty()) return ""
        val columns = rows.first().size
        val width = 5000 / columns
        return buildString {
            append("<w:tbl><w:tblPr><w:bidiVisual/>")
            append("<w:tblW w:w=\"5000\" w:type=\"pct\"/>")
            append("<w:tblBorders>")
            listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach {
                append("<w:$it w:val=\"single\" w:sz=\"6\" w:color=\"B9C2C7\"/>")
            }
            append("</w:tblBorders></w:tblPr><w:tblGrid>")
            repeat(columns) { append("<w:gridCol w:w=\"$width\"/>") }
            append("</w:tblGrid>")
            rows.forEachIndexed { rowIndex, cells ->
                append("<w:tr>")
                cells.forEach { cell ->
                    append("<w:tc><w:tcPr><w:tcW w:w=\"$width\" w:type=\"pct\"/>")
                    if (headerRow && rowIndex == 0) {
                        append("<w:shd w:val=\"clear\" w:fill=\"F2F5F7\"/>")
                    }
                    append("</w:tcPr>")
                    append(paragraph(cell, bold = headerRow && rowIndex == 0))
                    append("</w:tc>")
                }
                append("</w:tr>")
            }
            append("</w:tbl>")
            append(paragraph(""))
        }
    }

    /** An inline image sized to fit the text column; EMU units, 914400 per inch. */
    fun image(relationshipId: String, index: Int, widthEmu: Long, heightEmu: Long): String =
        "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing>" +
            "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
            "<wp:extent cx=\"$widthEmu\" cy=\"$heightEmu\"/>" +
            "<wp:docPr id=\"$index\" name=\"Picture$index\"/>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$index\" name=\"Picture$index\"/>" +
            "<pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"$relationshipId\"/>" +
            "<a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/>" +
            "<a:ext cx=\"$widthEmu\" cy=\"$heightEmu\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"

    val contentTypes: String = """
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Default Extension="jpeg" ContentType="image/jpeg"/>
          <Default Extension="jpg" ContentType="image/jpeg"/>
          <Default Extension="png" ContentType="image/png"/>
          <Override PartName="/word/document.xml"
            ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
    """.trimIndent()

    const val DOCUMENT_RELATIONSHIP_TYPE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"

    const val IMAGE_RELATIONSHIP_TYPE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
}
