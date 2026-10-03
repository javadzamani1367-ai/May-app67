package ir.ilam.inspection.ui.visit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.LocationSource
import ir.ilam.inspection.ui.location.PositionCard

/**
 * The position of the case — one of the things a report is judged on.
 *
 * The card itself is the one every TavanKav app shares ([PositionCard]); what
 * is particular here is where the point is stored and the facts under the map,
 * which say how and when it was taken, because a point typed in at the office
 * is not a point measured at the meter.
 */
@Composable
fun LocationCard(report: ReportEntity, viewModel: VisitViewModel) {
    val meta by viewModel.locationFix.collectAsStateWithLifecycle()
    PositionCard(
        latitude = report.latitude,
        longitude = report.longitude,
        accuracy = report.gpsAccuracy,
        onMeasured = { fix, samples -> viewModel.applyFix(fix, samples) },
        onPicked = { lat, lon -> viewModel.setCoordinates(lat, lon, LocationSource.MAP) },
        onTyped = { lat, lon -> viewModel.setCoordinates(lat, lon, LocationSource.MANUAL) }
    ) {
        LocationFacts(report = report, meta = meta)
    }
}
