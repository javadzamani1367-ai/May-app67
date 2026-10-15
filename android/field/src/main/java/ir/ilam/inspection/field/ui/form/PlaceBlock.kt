package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.field
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.location.LiveAccuracyPill
import ir.ilam.inspection.ui.location.MiniMap
import ir.ilam.inspection.ui.location.OfflineMapCard
import ir.ilam.inspection.ui.location.distanceFrom
import ir.ilam.inspection.ui.location.formatMetres
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.LocationWarmup
import ir.ilam.inspection.util.map.OfflineMap

/**
 * Where it is, in one block: the position (recorded on the map, which opens
 * on the user with the pin on them), when it was recorded, and the address,
 * written by itself from the offline map the moment the position is known
 * and editable like any field. With no offline map yet, the block offers
 * to fetch it right there, since the address cannot be drafted without it.
 */
@Composable
fun PlaceBlock(
    editor: ItemEditor,
    item: FieldItemEntity,
    reading: LocationWarmup.Reading?,
    fresh: Boolean,
    onOpenMap: () -> Unit,
    onAddress: (String) -> Unit,
    title: String = stringResource(R.string.hub_position_title),
    extra: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val installed by OfflineMap.installed.collectAsStateWithLifecycle(OfflineMap.current(context))
    val hasFix = item.latitude != null && item.longitude != null
    SectionCard(title = title, icon = Icons.Filled.MyLocation, tone = Tone.INFO, trailing = { LiveAccuracyPill(reading, fresh) }) {
        Column {
            if (hasFix) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = Spacing.xs).clip(MaterialTheme.shapes.medium)) {
                    MiniMap(item.latitude!!, item.longitude!!, item.accuracy, Tavan.colors.info.strong, onClick = onOpenMap)
                }
            }
            Text(
                when {
                    !hasFix -> stringResource(R.string.hub_position_missing)
                    item.accuracy != null -> stringResource(R.string.hub_position_done, formatMetres(item.accuracy))
                    else -> stringResource(R.string.hub_position_picked)
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (hasFix) Tavan.colors.success.strong else Tavan.colors.danger.strong
            )
            item.positionAt?.let { at ->
                val distance = item.observerLat?.let { lat ->
                    item.observerLon?.let { lon -> distanceFrom(Fix(lat, lon, 0.0), item.latitude ?: lat, item.longitude ?: lon) }
                }
                Text(
                    stringResource(R.string.place_recorded_at, PersianDate.formatWithTime(at)) +
                        (distance?.let { " · " + stringResource(R.string.place_observer_distance, formatMetres(it)) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // One way in: the map. It opens on the user, with the pin on them;
            // one tap records, or the map is dragged to put the pin elsewhere.
            PrimaryButton(
                text = stringResource(if (hasFix) R.string.place_change_on_map else R.string.place_record_on_map),
                onClick = onOpenMap,
                icon = Icons.Filled.Map,
                tone = if (hasFix) Tone.BRAND else Tone.SUCCESS,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            )
            Box(modifier = Modifier.padding(top = Spacing.md)) {
                AddressField(item.address.orEmpty(), item.latitude, item.longitude, onAddress,
                    lastAuto = editor.autoAddress, onAuto = { editor.autoAddress = it })
            }
            if (installed == null) {
                Text(
                    stringResource(R.string.place_need_map),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OfflineMapCard(serverAddress = context.field.prefs.serverAddress)
            }
            extra()
        }
    }
}
