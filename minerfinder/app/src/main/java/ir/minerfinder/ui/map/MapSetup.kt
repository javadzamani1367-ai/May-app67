package ir.minerfinder.ui.map

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * osmdroid configuration: tiles cached in the app's private storage, kept for
 * a year and never re-fetched while valid, so an area browsed once with
 * internet stays on the map offline.
 */
object MapSetup {
    private const val YEAR_MS = 365L * 24 * 3600 * 1000

    fun init(context: Context) {
        val base = File(context.filesDir, "osmdroid")
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            tileFileSystemCacheMaxBytes = 800L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 700L * 1024 * 1024
            expirationOverrideDuration = YEAR_MS
        }
    }

    fun cacheBytes(context: Context): Long =
        File(context.filesDir, "osmdroid/tiles").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearCache(context: Context) {
        File(context.filesDir, "osmdroid/tiles").deleteRecursively()
    }
}
