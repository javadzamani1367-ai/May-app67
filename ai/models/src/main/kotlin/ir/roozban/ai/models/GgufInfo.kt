package ir.roozban.ai.models

import ir.roozban.ai.core.ChatTemplate
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The few header fields of a GGUF file the app needs. */
data class GgufInfo(
    val version: Int,
    val architecture: String?,
    val name: String?,
    val fileType: Int?,
) {
    /** Best guess of the prompt format, for imported files. */
    val template: ChatTemplate
        get() {
            val arch = architecture.orEmpty().lowercase()
            val n = name.orEmpty().lowercase()
            return when {
                arch.startsWith("gemma") -> ChatTemplate.GEMMA
                arch == "qwen3" && "2507" !in n -> ChatTemplate.CHATML_NO_THINK
                else -> ChatTemplate.CHATML
            }
        }

    companion object {
        private const val MAGIC = 0x46554747 // "GGUF" little-endian

        fun read(file: File): GgufInfo = file.inputStream().buffered().use { read(it) }

        /**
         * Reads the header and the leading metadata. Stops at the first array value (the
         * vocabulary), after which nothing the app needs follows `general.*`.
         * Throws [InvalidModelException] when [input] is not GGUF.
         */
        fun read(input: InputStream): GgufInfo {
            val r = LittleEndian(DataInputStream(input))
            try {
                if (r.int() != MAGIC) throw InvalidModelException("not a GGUF file")
                val version = r.int()
                if (version !in 2..3) throw InvalidModelException("unsupported GGUF version $version")
                r.long() // tensor count
                val kvCount = r.long()
                var arch: String? = null
                var name: String? = null
                var fileType: Int? = null
                for (i in 0 until minOf(kvCount, 64)) {
                    val key = r.string()
                    val type = r.int()
                    if (type == TYPE_ARRAY) break
                    val value = r.value(type)
                    when (key) {
                        "general.architecture" -> arch = value as? String
                        "general.name" -> name = value as? String
                        "general.file_type" -> fileType = (value as? Number)?.toInt()
                    }
                }
                return GgufInfo(version, arch, name, fileType)
            } catch (e: EOFException) {
                throw InvalidModelException("truncated GGUF header")
            }
        }

        private const val TYPE_STRING = 8
        private const val TYPE_ARRAY = 9
    }

    private class LittleEndian(private val input: DataInputStream) {
        private val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

        private fun fill(n: Int): ByteBuffer {
            buf.clear()
            input.readFully(buf.array(), 0, n)
            buf.limit(n)
            return buf
        }

        fun int(): Int = fill(4).int

        fun long(): Long = fill(8).long

        fun string(): String {
            val len = long()
            if (len < 0 || len > 1 shl 20) throw InvalidModelException("bad string length")
            val bytes = ByteArray(len.toInt())
            input.readFully(bytes)
            return bytes.toString(Charsets.UTF_8)
        }

        fun value(type: Int): Any = when (type) {
            0 -> fill(1).get()
            1 -> fill(1).get().toInt() and 0xFF
            2 -> fill(2).short
            3 -> fill(2).short.toInt() and 0xFFFF
            4 -> int()
            5 -> int().toLong() and 0xFFFFFFFFL
            6 -> fill(4).float
            7 -> fill(1).get().toInt() != 0
            TYPE_STRING -> string()
            10 -> long()
            11 -> long()
            12 -> fill(8).double
            else -> throw InvalidModelException("unknown GGUF value type $type")
        }
    }
}

class InvalidModelException(message: String) : Exception(message)
