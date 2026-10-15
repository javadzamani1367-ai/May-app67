package ir.ilam.inspection.ui.location

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PlacedPoint

/**
 * A recorded position, in every TavanKav app the same way: on the map.
 *
 * The position is kept warm from the moment the card is on screen, so the map
 * opens on the user with the pin already on them; standing at the point, one
 * tap records it. A point elsewhere is put under the pin by dragging the map.
 * No typed coordinates and no blind "record where I am" — what is recorded is
 * always what the user saw on the map. What is known about how and when the
 * point was taken goes in [facts], under the map.
 */
@Composable
fun PositionCard(
    latitude: Double?,
    longitude: Double?,
    accuracy: Double?,
    onPlaced: (PlacedPoint) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.location_title),
    facts: @Composable () -> Unit = {}
) {
    // Warm from now on, so the map does not wait for the satellites.
    rememberLiveLocation()
    var showMap by rememberSaveable { mutableStateOf(false) }
    val hasFix = latitude != null && longitude != null
    SectionCard(
        title = title,
        modifier = modifier,
        icon = Icons.Filled.MyLocation,
        tone = Tone.INFO,
        trailing = {
            if (hasFix) {
                val quality = accuracyQuality(accuracy)
                StatusBadge(text = stringResource(quality.label), tone = quality.tone)
            }
        }
    ) {
        Column {
            if (hasFix) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(170.dp).padding(vertical = Spacing.sm)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    MiniMap(latitude!!, longitude!!, accuracy, Tavan.colors.info.strong, onClick = { showMap = true })
                }
                facts()
            } else {
                EmptyState(
                    title = stringResource(R.string.location_not_recorded_title),
                    message = stringResource(R.string.location_not_recorded_hint),
                    icon = Icons.Filled.GpsFixed,
                    modifier = Modifier.height(230.dp)
                )
            }
            PrimaryButton(
                text = stringResource(if (hasFix) R.string.position_change_on_map else R.string.position_record_on_map),
                onClick = { showMap = true },
                icon = Icons.Filled.Map,
                tone = if (hasFix) Tone.BRAND else Tone.SUCCESS,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
            )
        }
    }

    if (showMap) {
        Dialog(
            onDismissRequest = { showMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                LivePositionPage(
                    title = title,
                    recordedLatitude = latitude,
                    recordedLongitude = longitude,
                    onRecord = onPlaced,
                    onBack = { showMap = false }
                )
            }
        }
    }
}
