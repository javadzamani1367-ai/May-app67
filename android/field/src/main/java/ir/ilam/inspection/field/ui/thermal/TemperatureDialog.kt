package ir.ilam.inspection.field.ui.thermal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.thermal.Temperatures
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The five temperatures printed on a HIKMICRO photo, typed in beside the photo
 * itself. They are not read off the picture automatically: the digits are
 * small, drawn over the scene, and a misread 6 or 9 in an evidence file is
 * worse than a number the user typed while looking at it. Pinch to zoom.
 */
@Composable
fun TemperatureDialog(
    file: FieldFileEntity,
    initial: Temperatures,
    onDismiss: () -> Unit,
    onSave: (Temperatures) -> Unit
) {
    val store = LocalContext.current.field.files
    val bitmap by produceState<android.graphics.Bitmap?>(null, file.id) {
        value = withContext(Dispatchers.IO) { file.path?.let { Thumbnails.forPhoto(store.resolve(it), PREVIEW_EDGE) } }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { zoomChange, pan, _ ->
        scale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    var t by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.thermal_temps_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(260.dp).clipToBounds()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .transformable(zoom)
                ) {
                    bitmap?.let {
                        Image(
                            it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(260.dp).graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = offset.x; translationY = offset.y
                            }
                        )
                    }
                }
                Text(stringResource(R.string.thermal_temps_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.sm))
                Text(stringResource(R.string.thermal_temps_scene), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Degree(R.string.thermal_temp_max, t.max, Modifier.weight(1f)) { t = t.copy(max = it) }
                    Degree(R.string.thermal_temp_min, t.min, Modifier.weight(1f)) { t = t.copy(min = it) }
                }
                Text(stringResource(R.string.thermal_temps_box), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Degree(R.string.thermal_temp_max, t.boxMax, Modifier.weight(1f)) { t = t.copy(boxMax = it) }
                    Degree(R.string.thermal_temp_min, t.boxMin, Modifier.weight(1f)) { t = t.copy(boxMin = it) }
                    Degree(R.string.thermal_temp_avg, t.boxAvg, Modifier.weight(1f)) { t = t.copy(boxAvg = it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(t) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun Degree(label: Int, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    AppTextField(
        label = stringResource(label),
        value = PersianNumbers.toPersian(value),
        onValueChange = { onChange(Temperatures.clean(PersianNumbers.toLatin(it))) },
        modifier = modifier,
        keyboardType = KeyboardType.Decimal,
        ltr = true
    )
}

private const val PREVIEW_EDGE = 1600
private const val MAX_ZOOM = 6f
