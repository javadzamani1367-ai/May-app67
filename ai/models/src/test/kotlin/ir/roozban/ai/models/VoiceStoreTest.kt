package ir.roozban.ai.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoiceStoreTest {
    @TempDir
    lateinit var tmp: File

    private fun zip(name: String, vararg entries: Pair<String, String>): File {
        val f = File(tmp, name)
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (path, body) ->
                z.putNextEntry(ZipEntry(path))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return f
    }

    private fun voiceZip(name: String) = zip(
        "$name.zip",
        "model.onnx" to "m-$name", "tokens.txt" to "t", "espeak-ng-data/fa_dict" to "d-$name", "espeak-ng-data/voices/fa" to "v",
    )

    @Test
    fun `voices unpack once and share the phonemizer data`() {
        val store = VoiceStore(File(tmp, "voices"))
        assertThat(store.installed()).isEmpty()
        val first = voiceZip("amir")
        store.install("tts-fa-amir", first)
        assertThat(first.exists()).isFalse()
        store.install("tts-fa-ganji", voiceZip("ganji"))
        assertThat(store.installed()).containsExactly("tts-fa-amir", "tts-fa-ganji")
        assertThat(File(store.modelDir("tts-fa-ganji"), "model.onnx").readText()).isEqualTo("m-ganji")
        // The first voice's copy of the shared data is kept.
        assertThat(File(store.espeakDir, "fa_dict").readText()).isEqualTo("d-amir")
        assertThat(File(store.modelDir("tts-fa-ganji"), "espeak-ng-data").exists()).isFalse()

        store.delete("tts-fa-amir")
        assertThat(store.installed()).containsExactly("tts-fa-ganji")
        store.delete("tts-fa-ganji")
        assertThat(store.espeakDir.exists()).isFalse()
    }

    @Test
    fun `broken or hostile zips are rejected`() {
        val store = VoiceStore(File(tmp, "voices"))
        runCatching { store.install("x", zip("bad.zip", "tokens.txt" to "t", "espeak-ng-data/a" to "a")) }
        assertThat(store.installed()).isEmpty()
        val escape = runCatching { store.install("y", zip("evil.zip", "../../evil.txt" to "x")) }
        assertThat(escape.isFailure).isTrue()
        assertThat(File(tmp, "evil.txt").exists()).isFalse()
    }
}
