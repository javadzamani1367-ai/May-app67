package ir.ilam.inspection.export

import java.io.File

/**
 * A `.docx` holding one document body and nothing else — no images, so no
 * relationship parts beyond the required pair.
 *
 * The case report needs image relationships and builds its own package; this
 * is for the plainer documents, like the unit performance report, where a
 * whole builder would be ceremony around four lines.
 */
class DocxWriter {

    fun write(target: File, documentXml: String): File =
        OoxmlPackage(target)
            .addXml("[Content_Types].xml", WordDocumentXml.contentTypes)
            .addXml(
                "_rels/.rels",
                OoxmlPackage.rootRels("word/document.xml", WordDocumentXml.DOCUMENT_RELATIONSHIP_TYPE)
            )
            .addXml(
                "word/_rels/document.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>"
            )
            .addXml("word/document.xml", documentXml)
            .write()
}
