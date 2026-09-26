package ir.ilam.inspection.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.pending.CaseCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.visit.DotOverlay
import ir.ilam.inspection.ui.visit.MapControls
import ir.ilam.inspection.ui.visit.newMapView
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.LocationProvider
import ir.ilam.inspection.util.MapConfig
import ir.ilam.inspection.util.PersianNumbers
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

/**
 * Every case that has a position, on one map, coloured by where it stands:
 * blue still to do, green visited, grey filed. It answers the questions a list
 * cannot — which villages keep coming up, which cases sit next to each other
 * and can be visited on the same trip. A tap on a dot brings up its card.
 */
@Composable
fun CasesMapScreen(onBack: () -> Unit, onOpenCase: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val cases by remember { container.reportRepository.observeLocated() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var picked by remember { mutableStateOf<String?>(null) }
    var me by remember { mutableStateOf<Fix?>(null) }
    val colors = Tavan.colors

    val overlay = remember { CasePointsOverlay(context) { picked = it } }
    val myDot = remember { DotOverlay(context, colors.info.strong) }
    val map = remember {
        newMapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(9.0)
            controller.setCenter(GeoPoint(MapConfig.DEFAULT_LATITUDE, MapConfig.DEFAULT_LONGITUDE))
            overlays.add(overlay)
            overlays.add(myDot)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }

    // Frame all the cases the first time they arrive.
    var framed by remember { mutableStateOf(false) }
    LaunchedEffect(cases) {
        overlay.points = cases.mapNotNull { report ->
            val lat = report.latitude ?: return@mapNotNull null
            val lon = report.longitude ?: return@mapNotNull null
            val tone = when (ReportStatus.of(report.status)) {
                ReportStatus.PENDING -> colors.info.strong
                ReportStatus.VISITED -> colors.success.strong
                ReportStatus.ARCHIVED -> colors.neutral.strong
            }
            CasePoint(report.id, GeoPoint(lat, lon), tone.toArgb())
        }
        map.invalidate()
        if (!framed && overlay.points.isNotEmpty()) {
            framed = true
            if (overlay.points.size == 1) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(overlay.points.first().point)
            } else {
                map.post {
                    runCatching {
                        map.zoomToBoundingBox(BoundingBox.fromGeoPoints(overlay.points.map { it.point }), false, 96)
                    }
                }
            }
        }
    }

    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        runCatching {
            LocationProvider(context).fixes().collect { fix ->
                if (me == null || fix.accuracy <= (me?.accuracy ?: Double.MAX_VALUE) * 1.5) {
                    me = fix
                    myDot.point = GeoPoint(fix.latitude, fix.longitude)
                    myDot.accuracyMeters = fix.accuracy.toFloat()
                    map.invalidate()
                }
            }
        }
    }

    val selected = cases.firstOrNull { it.id == picked }
    Scaffold(
        topBar = {
            TavanTopBar(
                title = stringResource(R.string.cases_map_title),
                subtitle = if (cases.isEmpty()) stringResource(R.string.cases_map_empty)
                else stringResource(R.string.cases_map_subtitle, PersianNumbers.toPersian(cases.size)),
                onBack = onBack
            )
        },
        bottomBar = {
            if (selected != null) {
                BottomActionBar(
                    note = {
                        CaseCard(
                            report = selected,
                            daysWaiting = container.reportRepository.daysWaiting(selected),
                            onClick = { onOpenCase(selected.id, selected.status == ReportStatus.PENDING.code) },
                            onDeleteRequest = {},
                            horizontalPadding = 0.dp
                        )
                    }
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.cases_map_open),
                        onClick = { onOpenCase(selected.id, selected.status == ReportStatus.PENDING.code) },
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            if (selected == null && cases.isNotEmpty()) {
                AppCard(modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.md)) {
                    Text(
                        stringResource(R.string.cases_map_hint),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    )
                }
            }
            MapControls(
                locating = granted && me == null,
                onZoomIn = { map.controller.zoomIn() },
                onZoomOut = { map.controller.zoomOut() },
                onMyLocation = {
                    me?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude), 16.0, 700L) }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg)
            )
        }
    }
}
