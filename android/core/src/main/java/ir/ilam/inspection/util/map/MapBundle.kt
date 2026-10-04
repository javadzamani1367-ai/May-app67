package ir.ilam.inspection.util.map

import ir.ilam.inspection.util.geo.GeoFormatException
import ir.ilam.inspection.util.geo.GeoIndex
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** What a map bundle says about itself (manifest.json, written by .github/workflows/map.yml). */
data class MapManifest(
    val version: String,
    val region: String,
    val mapSize: Long,
    val mapSha256: String,
    val geoSize: Long,
    val geoSha256: String
) {
    companion object {
        const val FORMAT = 1
        const val MANIFEST = "manifest.json"
        const val MAP = "ilam.map"
        const val GEO = "ilam.geo"

        fun parse(text: String): MapManifest {
            val j = runCatching { JSONObject(text) }.getOrNull() ?: throw MapBundleException(MapBundleException.Reason.MANIFEST)
            if (j.optInt("format") != FORMAT || j.optString("map") != MAP || j.optString("geo") != GEO) {
                throw MapBundleException(MapBundleException.Reason.MANIFEST)
            }
            return MapManifest(
                version = j.optString("version").takeIf { it.matches(Regex("[0-9A-Za-z._-]{1,32}")) }
                    ?: throw MapBundleException(MapBundleException.Reason.MANIFEST),
                region = j.optString("region"),
                mapSize = j.optLong("map_size"),
                mapSha256 = j.optString("map_sha256").lowercase(),
                geoSize = j.optLong("geo_size"),
                geoSha256 = j.optString("geo_sha256").lowercase()
            )
        }
    }
}

class MapBundleException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_A_BUNDLE, UNEXPECTED_FILE, TOO_LARGE, MANIFEST, MISSING_FILE, CHECKSUM, BAD_INDEX }
}

/**
 * Unpacks a map bundle — manifest.json, ilam.map, ilam.geo in one zip — into
 * a folder, and refuses it unless every byte is what the manifest promises.
 * Only those three names are written, flat, so a crafted zip cannot place a
 * file anywhere else; sizes are capped so it cannot fill the phone.
 */
object MapBundle {

    /** Far above the real map (a few megabytes), far below a full phone. */
    const val MAX_FILE_BYTES = 400L * 1024 * 1024

    private val ALLOWED = setOf(MapManifest.MANIFEST, MapManifest.MAP, MapManifest.GEO)

    fun unpack(input: InputStream, target: File): MapManifest {
        target.deleteRecursively()
        target.mkdirs()
        var entries = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entry.isDirectory) continue
                val name = entry.name
                if (name !in ALLOWED) throw MapBundleException(MapBundleException.Reason.UNEXPECTED_FILE)
                File(target, name).outputStream().use { out ->
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FILE_BYTES) throw MapBundleException(MapBundleException.Reason.TOO_LARGE)
                        out.write(buffer, 0, n)
                    }
                }
            }
        }
        if (entries == 0) throw MapBundleException(MapBundleException.Reason.NOT_A_BUNDLE)
        return verify(target)
    }

    /** Checks an unpacked folder against its own manifest; the index must also read. */
    fun verify(folder: File): MapManifest {
        val manifestFile = File(folder, MapManifest.MANIFEST)
        if (!manifestFile.isFile) throw MapBundleException(MapBundleException.Reason.MISSING_FILE)
        val manifest = MapManifest.parse(manifestFile.readText())
        check(File(folder, MapManifest.MAP), manifest.mapSize, manifest.mapSha256)
        check(File(folder, MapManifest.GEO), manifest.geoSize, manifest.geoSha256)
        try {
            File(folder, MapManifest.GEO).inputStream().use { GeoIndex.read(it) }
        } catch (e: GeoFormatException) {
            throw MapBundleException(MapBundleException.Reason.BAD_INDEX)
        }
        return manifest
    }

    private fun check(file: File, size: Long, sha256: String) {
        if (!file.isFile) throw MapBundleException(MapBundleException.Reason.MISSING_FILE)
        if (file.length() != size || sha256(file) != sha256) throw MapBundleException(MapBundleException.Reason.CHECKSUM)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val BUFFER = 64 * 1024
}
