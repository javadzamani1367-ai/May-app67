package ir.ilam.inspection.field.ui.thermal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.AssetType
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.thermal.Temperatures
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One brought-in thermal file: what it is, when it was taken, where the route
 * puts it and whether that place can be trusted. Tapping selects it for the
 * assignment panel; the thermometer opens the temperature entry.
 */
@Composable
fun ThermalFileRow(
    file: FieldFileEntity,
    name: String,
    temperatures: Temperatures,
    selected: Boolean,
    onToggle: () -> Unit,
    onTemperatures: () -> Unit,
    onRemove: () -> Unit
) {
    val video = file.mime.startsWith("video")
    val selectedColor = Tavan.colors.of(Tone.ACCENT)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)
            .clip(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.background(selectedColor.container).border(1.5.dp, selectedColor.strong, MaterialTheme.shapes.medium) else Modifier)
            .clickable(onClick = onToggle)
            .padding(Spacing.sm)
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Thumbnail(file, video)
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
            Text(name, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr))
            file.capturedAt?.let {
                Text(PersianDate.formatWithSeconds(it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
                when {
                    file.latitude == null -> StatusBadge(stringResource(R.string.thermal_no_position), Tone.DANGER)
                    file.locationUncertain -> StatusBadge(stringResource(R.string.thermal_uncertain), Tone.WARNING)
                    else -> StatusBadge("±" + PersianNumbers.toPersian((file.accuracy ?: 0.0).toInt()), Tone.INFO)
                }
                if (file.assetType != null && !file.plate.isNullOrBlank()) {
                    StatusBadge(assetLabel(file.assetType) + " " + file.plate, Tone.SUCCESS)
                } else {
                    StatusBadge(stringResource(R.string.thermal_unassigned), Tone.WARNING)
                }
            }
            if (!temperatures.isEmpty()) {
                Text(
                    stringResource(R.string.thermal_temps_short, PersianNumbers.toPersian(temperatures.max.ifBlank { "—" }),
                        PersianNumbers.toPersian(temperatures.boxMax.ifBlank { "—" })),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tavan.colors.danger.strong
                )
            }
        }
        Column {
            if (!video) {
                IconButton(onClick = onTemperatures) {
                    Icon(Icons.Filled.Thermostat, contentDescription = stringResource(R.string.thermal_temps_title),
                        tint = Tavan.colors.danger.strong)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.form_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Thumbnail(file: FieldFileEntity, video: Boolean) {
    val store = LocalContext.current.field.files
    val bitmap by produceState<android.graphics.Bitmap?>(null, file.id) {
        value = withContext(Dispatchers.IO) {
            val local = file.path?.let { store.resolve(it) } ?: return@withContext null
            if (video) Thumbnails.forVideo(local) else Thumbnails.forPhoto(local, 240)
        }
    }
    Box(modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (video) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Tavan.colors.info.strong,
                modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun assetLabel(type: Int?): String = stringResource(
    if (type == AssetType.PANEL) R.string.thermal_asset_panel else R.string.thermal_asset_pole
)
