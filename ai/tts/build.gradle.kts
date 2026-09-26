import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

plugins {
    alias(libs.plugins.roozban.android.library)
    alias(libs.plugins.roozban.hilt)
}

android {
    namespace = "ir.roozban.ai.tts"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a")
        }
    }
}

/**
 * sherpa-onnx's prebuilt Android libraries (Apache-2.0), pinned by version and sha256. Only the
 * JNI library and onnxruntime are needed for the Kotlin API.
 */
abstract class FetchSherpaLibs : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input abstract val abis: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Internal abstract val archive: RegularFileProperty
    @get:Inject abstract val archives: ArchiveOperations
    @get:Inject abstract val files: FileSystemOperations

    @TaskAction
    fun fetch() {
        val file = archive.get().asFile
        if (!file.exists() || hash(file) != sha256.get()) {
            file.parentFile.mkdirs()
            val tmp = File(file.path + ".part")
            URI(url.get()).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val got = hash(tmp)
            check(got == sha256.get()) { "sherpa-onnx archive checksum mismatch: $got" }
            tmp.renameTo(file)
        }
        val wanted = abis.get().flatMap { abi -> listOf("$abi/libsherpa-onnx-jni.so", "$abi/libonnxruntime.so") }.toSet()
        files.sync {
            from(archives.tarTree(archives.bzip2(file)))
            // Archive entries are "./jniLibs/<abi>/<lib>"; keep "<abi>/<lib>".
            include { d -> d.isDirectory || d.relativePath.segments.takeLast(2).joinToString("/") in wanted }
            eachFile { relativePath = RelativePath(true, *relativePath.segments.takeLast(2).toTypedArray()) }
            includeEmptyDirs = false
            into(outputDir)
        }
    }

    private fun hash(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
}

val sherpaVersion = "v1.13.8"
val fetchSherpaLibs = tasks.register<FetchSherpaLibs>("fetchSherpaLibs") {
    url.set("https://github.com/k2-fsa/sherpa-onnx/releases/download/$sherpaVersion/sherpa-onnx-$sherpaVersion-android.tar.bz2")
    sha256.set("2ff63469a71cb6009aa2e3ed5f4a670f8abdcbe4bb9ffd23776afc792a6b4f44")
    abis.set(listOf("arm64-v8a", "x86_64", "armeabi-v7a"))
    archive.set(layout.buildDirectory.file("sherpa/sherpa-onnx-$sherpaVersion-android.tar.bz2"))
    outputDir.set(layout.buildDirectory.dir("sherpa/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(fetchSherpaLibs, FetchSherpaLibs::outputDir)
    }
}

dependencies {
    api(projects.ai.runtime)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
