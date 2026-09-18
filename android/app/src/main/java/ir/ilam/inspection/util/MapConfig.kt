package ir.ilam.inspection.util

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * osmdroid setup. The tile cache lives in the app's private storage — the same
 * rule as the media files — and the user agent is the package name, which the
 * tile servers require. Tiles already fetched keep working with no network, so
 * an expert who looked at an area before travelling can still pick a point on
 * the map in a village with no signal.
 */
object MapConfig {

    private const val PREFS = "osmdroid"

    fun ensure(context: Context) {
        val configuration = Configuration.getInstance()
        if (configuration.userAgentValue == context.packageName) return
        runCatching {
            configuration.load(context, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
            configuration.userAgentValue = context.packageName
            val base = File(context.filesDir, "osmdroid")
            configuration.osmdroidBasePath = base
            configuration.osmdroidTileCache = File(base, "tiles")
        }
    }

    /** Ilam city — where the map opens when the case has no coordinate yet. */
    const val DEFAULT_LATITUDE = 33.6374
    const val DEFAULT_LONGITUDE = 46.4227
}
