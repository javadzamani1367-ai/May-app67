package ir.ilam.inspection.util.map

import android.app.Application
import android.content.Context
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Draws a map view from the installed Ilam map instead of the internet.
 *
 * Each view gets its own tile source over the map file, closed when the view
 * is detached (osmdroid-mapsforge leaves that to its caller). Rendered tiles
 * are cached under a name that carries the map version, so a newer map is
 * never shown with tiles drawn from the old one.
 */
object OfflineTiles {

    private var graphicsReady = false

    /** True if the view now draws from the offline map; false leaves it on the online tiles. */
    fun apply(context: Context, view: MapView): Boolean {
        val manifest = OfflineMap.current(context) ?: return false
        val file = OfflineMap.mapFile(context) ?: return false
        return runCatching {
            if (!graphicsReady) {
                MapsForgeTileSource.createInstance(context.applicationContext as Application)
                graphicsReady = true
            }
            val source = MapsForgeTileSource.createFromFiles(arrayOf(file), InternalRenderTheme.DEFAULT, "tavankav-ilam-${manifest.version}")
            view.tileProvider = OwnedProvider(context, source)
            view.setUseDataConnection(false)
            true
        }.getOrDefault(false)
    }

    private class OwnedProvider(context: Context, private val source: MapsForgeTileSource) :
        MapsForgeTileProvider(SimpleRegisterReceiver(context.applicationContext), source, null) {
        private val closed = AtomicBoolean(false)

        override fun detach() {
            super.detach()
            if (closed.compareAndSet(false, true)) runCatching { source.dispose() }
        }
    }
}
