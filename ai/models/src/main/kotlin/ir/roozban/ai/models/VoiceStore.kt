package ir.roozban.ai.models

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Installed Piper voices: `<dir>/<id>/model.onnx` and `tokens.txt`, plus the phonemizer data
 * all voices share in `<dir>/espeak-ng-data`. A downloaded voice zip is unpacked here and then
 * deleted, so a voice takes its space only once.
 */
class VoiceStore(val dir: File) {
    val espeakDir: File get() = File(dir, ESPEAK)

    fun modelDir(id: String): File = File(dir, id)

    fun installed(): Set<String> {
        if (!File(espeakDir, COMPLETE).exists()) return emptySet()
        return dir.listFiles { f -> f.isDirectory && f.name != ESPEAK && isComplete(f) }?.mapTo(HashSet()) { it.name } ?: emptySet()
    }

    /** Unpacks [zip] as voice [id]; the zip is removed afterwards, successful or not. */
    fun install(id: String, zip: File) {
        require(id.isNotEmpty() && '/' !in id && id != ESPEAK && !id.startsWith(".")) { "bad voice id" }
        dir.mkdirs()
        val staging = File(dir, ".$id.tmp").apply { deleteRecursively(); mkdirs() }
        val espeakStaging = File(dir, ".$ESPEAK.tmp").apply { deleteRecursively() }
        val needEspeak = !File(espeakDir, COMPLETE).exists()
        try {
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val name = entry.name.trimStart('/')
                    val (root, target) = if (name == ESPEAK || name.startsWith("$ESPEAK/")) {
                        if (!needEspeak) continue
                        espeakStaging to name.removePrefix(ESPEAK).trimStart('/')
                    } else {
                        staging to name
                    }
                    if (target.isEmpty()) continue
                    val out = File(root, target)
                    // No "../" escapes out of the voice folder.
                    if (!out.canonicalPath.startsWith(root.canonicalPath + File.separator)) throw IOException("bad entry $name")
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile.mkdirs()
                        out.outputStream().use { zin.copyTo(it) }
                    }
                }
            }
            if (!isComplete(staging)) throw IOException("voice files missing")
            if (needEspeak) {
                if (!espeakStaging.isDirectory) throw IOException("phonemizer data missing")
                espeakDir.deleteRecursively()
                if (!espeakStaging.renameTo(espeakDir)) throw IOException("could not store phonemizer data")
                File(espeakDir, COMPLETE).writeText("ok")
            }
            val target = modelDir(id)
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("could not store voice")
        } finally {
            staging.deleteRecursively()
            espeakStaging.deleteRecursively()
            zip.delete()
        }
    }

    fun delete(id: String) {
        modelDir(id).deleteRecursively()
        if (installed().isEmpty()) espeakDir.deleteRecursively()
    }

    private fun isComplete(d: File) = File(d, MODEL).isFile && File(d, TOKENS).isFile

    companion object {
        const val MODEL = "model.onnx"
        const val TOKENS = "tokens.txt"
        private const val ESPEAK = "espeak-ng-data"
        private const val COMPLETE = ".complete"
    }
}
