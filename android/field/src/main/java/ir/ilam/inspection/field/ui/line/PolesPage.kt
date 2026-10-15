package ir.ilam.inspection.field.ui.line

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.LineAnalysis
import ir.ilam.inspection.field.data.PoleReading
import ir.ilam.inspection.field.data.PoleState
import ir.ilam.inspection.field.ui.form.FormPage
import ir.ilam.inspection.ui.capture.CameraCapture
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.PersianNumbers

/**
 * Every pole of the walk, in order, each with its readings and whether it is
 * located; the chart on top once there are two points. "Add pole" is pinned
 * at the bottom, already numbered.
 */
@Composable
fun PolesPage(viewModel: LineViewModel, onBack: () -> Unit, onOpenPole: (String) -> Unit) {
    val poles = viewModel.payload.poles
    val verdict = viewModel.verdict()
    FormPage(
        title = stringResource(R.string.line_poles_title),
        onBack = onBack,
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.line_add_pole, PersianNumbers.toPersian(poles.size + 1)),
                onClick = { onOpenPole(viewModel.addPole()) },
                icon = Icons.Filled.Add,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        if (verdict.points.size >= 2) {
            SectionCard(title = stringResource(R.string.line_analysis_title), tone = Tone.ACCENT) { LineChart(verdict, height = 170.dp) }
        }
        poles.forEachIndexed { index, pole -> PoleRow(index + 1, pole, onClick = { onOpenPole(pole.id) }) }
        if (poles.isEmpty()) {
            Text(
                stringResource(R.string.line_poles_need),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.lg)
            )
        }
    }
}

/** A pole in the list: its number in a disc coloured by whether it is complete, its readings, its place. */
@Composable
private fun PoleRow(number: Int, pole: PoleReading, onClick: () -> Unit) {
    val complete = LineAnalysis.poleMissing(pole, number).isEmpty()
    val tone = when {
        !complete -> Tavan.colors.danger
        pole.state == PoleState.NOT_MEASURED -> Tavan.colors.warning
        else -> Tavan.colors.success
    }
    val colors = phaseColors()
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = onClick) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(46.dp).background(tone.container, CircleShape), contentAlignment = Alignment.Center) {
                Text(PersianNumbers.toPersian(number), style = MaterialTheme.typography.titleLarge, color = tone.strong)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    stringResource(R.string.line_pole_title, PersianNumbers.toPersian(number)) +
                        (pole.plate.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium
                )
                if (pole.state == PoleState.MEASURED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        listOf(pole.phases.r, pole.phases.s, pole.phases.t).forEachIndexed { i, value ->
                            Text(
                                "${"RST"[i]} ${PersianNumbers.toPersian(value.ifBlank { "—" })}",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors[i]
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.line_state_not_measured) + (pole.reason.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tavan.colors.warning.strong
                    )
                }
            }
            Icon(
                if (pole.hasPosition) Icons.Filled.Place else Icons.Filled.GpsNotFixed,
                contentDescription = null,
                tint = if (pole.hasPosition) Tavan.colors.info.strong else Tavan.colors.danger.strong
            )
        }
    }
}

/**
 * A photo of this pole — the clamp meter on the phase, the plate. Goes into
 * the item's photos like any other, stamped with the pole's own position,
 * and the pole remembers it.
 */
@Composable
fun PoleCamera(viewModel: LineViewModel, pole: PoleReading, stampCode: String) {
    val context = LocalContext.current
    var capturing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capturing = true
    }
    SecondaryButton(
        text = stringResource(R.string.line_pole_photo) +
            (if (pole.photos.isNotEmpty()) " (" + stringResource(R.string.line_pole_photos, PersianNumbers.toPersian(pole.photos.size)) + ")" else ""),
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) capturing = true else permission.launch(Manifest.permission.CAMERA)
        },
        icon = Icons.Filled.PhotoCamera,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
    )
    if (capturing) {
        Dialog(onDismissRequest = { capturing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CameraCapture(
                canTakePhoto = true,
                canRecordVideo = false,
                photoTarget = { viewModel.newFile("jpg") },
                videoTarget = { viewModel.newFile("mp4") },
                onPhoto = { raw ->
                    val place = if (pole.latitude != null && pole.longitude != null) {
                        Fix(pole.latitude, pole.longitude, pole.accuracy ?: 0.0)
                    } else null
                    viewModel.addCameraPhoto(raw, stampCode, place) { at ->
                        viewModel.updatePole(pole.id) { it.copy(photos = it.photos + at) }
                    }
                },
                onVideo = {},
                onClose = { capturing = false }
            )
        }
    }
}
