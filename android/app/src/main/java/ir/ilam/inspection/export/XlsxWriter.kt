package ir.ilam.inspection.export

import java.io.File

/**
 * A real `.xlsx` from a table of strings.
 *
 * Written as OOXML with inline strings, so there is no shared-string table and
 * no spreadsheet library: the whole format is a zip of four small XML parts,
 * and a library for that would cost megabytes in an APK with a twenty
 * megabyte ceiling.
 *
 * Shared by the case export and the unit performance report — one writer, so
 * a fix to the right-to-left layout or the escaping reaches both.
 */
class XlsxWriter(private val sheetName: String) {

    fun write(target: File, rows: List<List<String>>): File =
        OoxmlPackage(target)
            .addXml("[Content_Types].xml", CONTENT_TYPES)
            .addXml("_rels/.rels", OoxmlPackage.rootRels("xl/workbook.xml", DOCUMENT_TYPE))
            .addXml("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            .addXml("xl/workbook.xml", workbook())
            .addXml("xl/worksheets/sheet1.xml", sheet(rows))
            .write()

    private fun workbook(): String =
        "<workbook xmlns=\"$SPREADSHEET_NS\" xmlns:r=\"$RELATIONSHIP_NS\"><sheets>" +
            "<sheet name=\"${escape(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    /** Right-to-left sheet so column A sits on the right, as Persian users expect. */
    private fun sheet(rows: List<List<String>>): String = buildString {
        append("<worksheet xmlns=\"$SPREADSHEET_NS\">")
        append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews>")
        append("<sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            append("<row r=\"${rowIndex + 1}\">")
            cells.forEachIndexed { columnIndex, value ->
                val reference = columnName(columnIndex) + (rowIndex + 1)
                append("<c r=\"$reference\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                append(escape(value))
                append("</t></is></c>")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columnName(index: Int): String {
        var remaining = index
        val name = StringBuilder()
        while (true) {
            name.insert(0, ('A' + remaining % 26))
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return name.toString()
    }

    companion object {
        fun ensureExtension(fileName: String): String =
            if (fileName.endsWith(".xlsx", ignoreCase = true)) fileName else "$fileName.xlsx"

        /** XML, not HTML: an unescaped ampersand in an address breaks the file. */
        fun escape(value: String?): String = (value ?: "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        private const val SPREADSHEET_NS =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val RELATIONSHIP_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val DOCUMENT_TYPE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"

        private val CONTENT_TYPES = """
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
        """.trimIndent()

        private val WORKBOOK_RELS = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
                Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()
    }

}
