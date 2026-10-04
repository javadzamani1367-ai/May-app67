package ir.ilam.inspection.util.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * The offline map of Ilam on this phone: the drawn map and the address index,
 * installed from a bundle (map.yml builds it). Kept in the app's private
 * storage, like everything else.
 *
 * A new bundle is unpacked and checked beside the old one, and only then
 * swapped in, so a failed or interrupted update leaves the working map alone.
 */
object OfflineMap {

    private val _installed = MutableStateFlow<MapManifest?>(null)
    private var loaded = false

    /** The installed map, or null. Every map view and the address lookup follow it. */
    val installed: StateFlow<MapManifest?> = _installed.asStateFlow()

    fun current(context: Context): MapManifest? {
        if (!loaded) reload(context)
        return _installed.value
    }

    fun mapFile(context: Context): File? = current(context)?.let { File(folder(context), MapManifest.MAP) }

    fun geoFile(context: Context): File? = current(context)?.let { File(folder(context), MapManifest.GEO) }

    /** Unpacks, checks, and only then replaces what is installed. Throws [MapBundleException]. */
    suspend fun install(context: Context, open: () -> InputStream): MapManifest = withContext(Dispatchers.IO) {
        val root = root(context)
        val incoming = File(root, INCOMING)
        try {
            val manifest = open().use { MapBundle.unpack(it, incoming) }
            val old = File(root, OLD)
            old.deleteRecursively()
            val current = folder(context)
            if (current.exists() && !current.renameTo(old)) throw MapBundleException(MapBundleException.Reason.NOT_A_BUNDLE)
            if (!incoming.renameTo(current)) {
                old.renameTo(current)
                throw MapBundleException(MapBundleException.Reason.NOT_A_BUNDLE)
            }
            old.deleteRecursively()
            loaded = true
            _installed.value = manifest
            manifest
        } finally {
            incoming.deleteRecursively()
        }
    }

    suspend fun remove(context: Context) = withContext(Dispatchers.IO) {
        folder(context).deleteRecursively()
        loaded = true
        _installed.value = null
    }

    /** Reads what is installed. Sizes only — the full check ran at install time. */
    private fun reload(context: Context) {
        loaded = true
        val folder = folder(context)
        _installed.value = runCatching {
            val manifest = MapManifest.parse(File(folder, MapManifest.MANIFEST).readText())
            val sizesMatch = File(folder, MapManifest.MAP).length() == manifest.mapSize &&
                File(folder, MapManifest.GEO).length() == manifest.geoSize
            manifest.takeIf { sizesMatch }
        }.getOrNull()
    }

    private fun root(context: Context) = File(context.filesDir, "maps").apply { mkdirs() }
    private fun folder(context: Context) = File(root(context), CURRENT)

    private const val CURRENT = "current"
    private const val INCOMING = "incoming"
    private const val OLD = "old"
}
