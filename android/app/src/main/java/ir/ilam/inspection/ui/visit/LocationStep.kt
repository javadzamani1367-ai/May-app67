package ir.ilam.inspection.ui.visit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.AutoSave
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.LocationProvider
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.launch

/** Step 1 — confirm the address and capture the GIS position. */
@Composable
fun LocationStep(detail: ReportDetail, viewModel: VisitViewModel) {
    val report = detail.report
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { LocationProvider(context) }

    var county by remember(report.id) { mutableStateOf(report.county.orEmpty()) }
    var district by remember(report.id) { mutableStateOf(report.district.orEmpty()) }
    var address by remember(report.id) { mutableStateOf(report.address.orEmpty()) }
    var postalCode by remember(report.id) { mutableStateOf(report.postalCode.orEmpty()) }
    var fileNumber by remember(report.id) { mutableStateOf(report.fileNumber.orEmpty()) }
    var billNumber by remember(report.id) { mutableStateOf(report.billNumber.orEmpty()) }
    var subscription by remember(report.id) { mutableStateOf(report.subscriptionNumber.orEmpty()) }
    var usageType by remember(report.id) { mutableStateOf(report.usageType.orEmpty()) }

    var status by remember { mutableStateOf<Int?>(null) }
    var locating by remember { mutableStateOf(false) }

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
                status = null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) capture() else status = R.string.gps_permission_needed
    }

    AutoSave(listOf(county, district, address, postalCode, fileNumber, billNumber, usageType)) {
        viewModel.setLocationField(
            county = county,
            district = district,
            address = address,
            postalCode = postalCode,
            fileNumber = fileNumber,
            billNumber = billNumber,
            usageType = usageType
        )
    }
    AutoSave(subscription) { viewModel.setSubscriptionNumber(subscription) }

    SectionCard(title = stringResource(R.string.visit_step_location)) {
        Column {
            AppTextField(stringResource(R.string.field_county), county, { county = it })
            AppTextField(stringResource(R.string.field_district), district, { district = it })
            AppTextField(
                label = stringResource(R.string.field_address),
                value = address,
                onValueChange = { address = it },
                singleLine = false,
                minLines = 2
            )
            NumberField(stringResource(R.string.field_postal_code), postalCode, { postalCode = it })
            AppTextField(stringResource(R.string.field_file_number), fileNumber, { fileNumber = it })
            AppTextField(stringResource(R.string.field_bill_number), billNumber, { billNumber = it })
            NumberField(
                stringResource(R.string.field_subscription_number),
                subscription,
                { subscription = it }
            )
            AppTextField(stringResource(R.string.field_usage_type), usageType, { usageType = it })
        }
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
        }
    }
}
