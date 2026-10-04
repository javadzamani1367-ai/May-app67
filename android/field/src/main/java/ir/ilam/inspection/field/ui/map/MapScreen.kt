package ir.ilam.inspection.field.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.field
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.location.OfflineMapCard
import ir.ilam.inspection.ui.location.newMapView
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.util.MapConfig
import ir.ilam.inspection.util.map.OfflineMap
import org.osmdroid.util.GeoPoint

/**
 * The offline map: install or update it, and see it. Every map in the app
 * — position cards, the picker — draws from it once it is installed, and the
 * forms draft their address from it.
 */
@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val installed by OfflineMap.installed.collectAsStateWithLifecycle(OfflineMap.current(context))
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.offline_map_title), onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
        ) {
            OfflineMapCard(serverAddress = context.field.prefs.serverAddress)
            // A new view for each installed version: a view keeps the tiles it was made with.
            key(installed?.version) { Preview() }
        }
    }
}

@Composable
private fun Preview() {
    val context = LocalContext.current
    val map = remember {
        newMapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(PREVIEW_ZOOM)
            controller.setCenter(GeoPoint(MapConfig.DEFAULT_LATITUDE, MapConfig.DEFAULT_LONGITUDE))
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = Modifier.fillMaxWidth().height(360.dp).padding(vertical = Spacing.sm).clip(MaterialTheme.shapes.large)
    )
}

private const val PREVIEW_ZOOM = 13.0
