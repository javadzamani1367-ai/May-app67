package ir.ilam.inspection.field.thermal

/**
 * Proof that writing GPS into a copy of a thermal photo left everything else
 * in it alone.
 *
 * A radiometric JPEG keeps its temperature data in segments of its own (and
 * sometimes after the image's end). Writing EXIF may only touch the EXIF
 * segment; if anything else — another APP segment, the image data, bytes
 * after the end — differs by a single byte, the copy is not trusted and is
 * thrown away. The original is never written to at all.
 */
object JpegSegments {

    /** A segment's marker and its bytes, the length field included. */
    data class Segment(val marker: Int, val bytes: List<Byte>)

    /**
     * Everything except the EXIF segment, in order: the header segments, then
     * the scan and whatever follows it, as one piece. Null if this is not a JPEG.
     */
    fun withoutExif(data: ByteArray): List<Segment>? {
        if (data.size < 4 || data[0] != 0xFF.toByte() || data[1] != 0xD8.toByte()) return null
        val out = mutableListOf<Segment>()
        var i = 2
        while (i + 4 <= data.size) {
            if (data[i] != 0xFF.toByte()) return null
            val marker = data[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue } // fill byte
            if (marker == 0xDA) {
                // Start of scan: the image data and anything after it, untouched as one block.
                out += Segment(marker, data.copyOfRange(i, data.size).toList())
                return out
            }
            if (marker == 0xD9) return out
            val length = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
            if (length < 2 || i + 2 + length > data.size) return null
            val segment = data.copyOfRange(i, i + 2 + length)
            if (!(marker == 0xE1 && isExif(segment))) out += Segment(marker, segment.toList())
            i += 2 + length
        }
        return null
    }

    /** True when [copy] differs from [original] in nothing but its EXIF segment. */
    fun onlyExifChanged(original: ByteArray, copy: ByteArray): Boolean {
        val a = withoutExif(original) ?: return false
        val b = withoutExif(copy) ?: return false
        return a == b
    }

    private fun isExif(segment: ByteArray): Boolean {
        val tag = "Exif".toByteArray()
        if (segment.size < 4 + tag.size + 2) return false
        return tag.indices.all { segment[4 + it] == tag[it] } && segment[8] == 0.toByte() && segment[9] == 0.toByte()
    }
}
