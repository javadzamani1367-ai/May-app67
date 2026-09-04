package ir.ilam.inspection.export

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal OOXML writer. `.docx` and `.xlsx` are just zip archives of XML
 * parts, so both are produced here with the platform zip writer — no Office
 * library, no size cost.
 */
class OoxmlPackage(private val target: File) {

    private val entries = LinkedHashMap<String, ByteArray>()
    private val fileEntries = LinkedHashMap<String, File>()

    fun addXml(path: String, xml: String) = apply {
        entries[path] = (XML_DECLARATION + xml).toByteArray(Charsets.UTF_8)
    }

    fun addFile(path: String, file: File) = apply {
        if (file.exists()) fileEntries[path] = file
    }

    fun write(): File {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            fileEntries.forEach { (path, file) ->
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip as OutputStream) }
                zip.closeEntry()
            }
        }
        return target
    }

    companion object {
        const val XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n"

        /** The relationship file every OOXML package starts from. */
        fun rootRels(target: String, type: String): String = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="$type" Target="$target"/>
            </Relationships>
        """.trimIndent()
    }
}
