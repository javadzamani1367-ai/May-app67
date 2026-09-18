package ir.ilam.inspection.ui.visit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.LocationProvider
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.launch

/**
 * The three ways a coordinate reaches the report: read from the phone, picked
 * on the map, or typed in. The sensor is the normal path; the other two exist
 * because a coordinate is sometimes recorded away from the site, and because
 * not every phone in the field can get a fix when it is needed.
 */
@Composable
fun CoordinatesCard(report: ReportEntity, viewModel: VisitViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { LocationProvider(context) }

    var status by remember { mutableStateOf<Int?>(null) }
    var source by remember(report.id) { mutableStateOf<Int?>(null) }
    var locating by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }

    fun capture() {
        locating = true
        status = R.string.gps_waiting
        scope.launch {
            val fix = provider.currentFix()
            locating = false
            if (fix == null) {
                status = R.string.gps_failed
            } else {
                viewModel.applyFix(fix)
                source = if (fix.fromGoogle) R.string.gps_source_google else R.string.gps_source_device
                status = null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) capture() else status = R.string.gps_permission_needed
    }

    SectionCard(title = stringResource(R.string.field_coordinates)) {
        Column {
            val hasFix = report.latitude != null && report.longitude != null
            Text(
                text = if (hasFix) {
                    PersianNumbers.toPersian("%.6f , %.6f".format(report.latitude, report.longitude))
                } else {
                    stringResource(R.string.gps_not_captured)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            if (hasFix && report.gpsAccuracy != null) {
                Text(
                    text = stringResource(
                        R.string.field_gps_accuracy,
                        PersianNumbers.toPersian(report.gpsAccuracy)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            source?.let {
                Text(text = stringResource(it), style = MaterialTheme.typography.bodySmall)
            }
            status?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) capture() else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                enabled = !locating,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.action_capture_gps))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showMap = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_pick_on_map))
                }
                OutlinedButton(
                    onClick = { manualOpen = !manualOpen },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_manual_coordinates))
                }
            }

            if (manualOpen) {
                ManualCoordinates { latitude, longitude ->
                    viewModel.setCoordinates(latitude, longitude)
                    source = R.string.gps_source_manual
                    status = null
                    manualOpen = false
                }
            }
        }
    }

    if (showMap) {
        MapPickerDialog(
            initialLatitude = report.latitude,
            initialLongitude = report.longitude,
            onDismiss = { showMap = false },
            onConfirm = { latitude, longitude ->
                viewModel.setCoordinates(latitude, longitude)
                source = R.string.gps_source_map
                status = null
                showMap = false
            }
        )
    }
}

/** Typed-in coordinates, validated before they can be saved. */
@Composable
private fun ManualCoordinates(onSave: (Double, Double) -> Unit) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        NumberField(
            label = stringResource(R.string.manual_latitude),
            value = latitude,
            onValueChange = { latitude = it; invalid = false },
            decimal = true
        )
        NumberField(
            label = stringResource(R.string.manual_longitude),
            value = longitude,
            onValueChange = { longitude = it; invalid = false },
            decimal = true,
            imeAction = ImeAction.Done
        )
        if (invalid) {
            Text(
                text = stringResource(R.string.manual_coordinates_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = {
                val lat = latitude.toDoubleOrNull()
                val lon = longitude.toDoubleOrNull()
                if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    invalid = true
                } else {
                    onSave(lat, lon)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Text(stringResource(R.string.manual_coordinates_save))
        }
    }
}
