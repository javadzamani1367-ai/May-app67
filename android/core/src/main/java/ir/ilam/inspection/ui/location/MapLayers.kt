package ir.ilam.inspection.ui.location

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import ir.ilam.inspection.util.map.OfflineTiles

/**
 * The two faces of the map: streets (the offline Ilam map, or OpenStreetMap
 * online) and satellite imagery. Imagery needs the internet; it is what lets
 * a point be put on the right roof in a village no street map has drawn.
 */
object MapLayers {

    /** Esri World Imagery: free to display with attribution, no key. */
    private val imagery = object : OnlineTileSourceBase(
        "esri-world-imagery", 0, 19, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Esri, Maxar, Earthstar Geographics"
    ) {
        override fun getTileURLString(index: Long): String =
            baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
    }

    fun satellite(context: Context, view: MapView) {
        view.tileProvider.detach()
        view.tileProvider = MapTileProviderBasic(context.applicationContext, imagery)
        view.setUseDataConnection(true)
        view.invalidate()
    }

    fun streets(context: Context, view: MapView) {
        view.tileProvider.detach()
        if (!OfflineTiles.apply(context, view)) {
            view.tileProvider = MapTileProviderBasic(context.applicationContext, TileSourceFactory.MAPNIK)
            view.setUseDataConnection(true)
        }
        view.invalidate()
    }
}
