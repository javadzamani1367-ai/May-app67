package ir.ilam.inspection.ui.visit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.AttendeeOrg
import ir.ilam.inspection.data.model.EntryMethod
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.attendeeOrgLabel
import ir.ilam.inspection.util.PersianNumbers

/** Step 4 — the miners found on site and the people present during the visit. */
@Composable
fun DevicesStep(detail: ReportDetail, viewModel: VisitViewModel) {
    DeviceSection(detail, viewModel)
    AttendeeSection(detail, viewModel)
}

@Composable
private fun DeviceSection(detail: ReportDetail, viewModel: VisitViewModel) {
    var model by rememberSaveable { mutableStateOf("") }
    var serial by rememberSaveable { mutableStateOf("") }
    var power by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var method by remember { mutableStateOf(EntryMethod.MANUAL) }
    val duplicate by viewModel.deviceError.collectAsStateWithLifecycle()

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let {
            serial = it
            method = EntryMethod.BARCODE
            viewModel.clearDeviceError()
        }
    }

    SectionCard(title = stringResource(R.string.devices_title)) {
        Column {
            Text(
                text = stringResource(
                    R.string.device_count,
                    PersianNumbers.toPersian(detail.deviceCount)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.device_total_power,
                    PersianNumbers.grouped(detail.totalPower)
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            detail.devices.forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = PersianNumbers.toPersian(device.rowNumber) + ". " +
                            listOfNotNull(
                                device.model,
                                device.serialNumber,
                                device.powerWatt?.let { PersianNumbers.toPersian(it) }
                            ).joinToString(" - "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeDevice(device) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            }
            if (detail.devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.devices_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AppTextField(stringResource(R.string.device_model), model, { model = it })
            AppTextField(
                label = stringResource(R.string.device_serial),
                value = serial,
                onValueChange = {
                    serial = it
                    method = EntryMethod.MANUAL
                    viewModel.clearDeviceError()
                },
                error = if (duplicate) stringResource(R.string.device_duplicate_serial) else null
            )
            NumberField(
                label = stringResource(R.string.device_power),
                value = power,
                onValueChange = { power = it },
                decimal = true
            )
            AppTextField(stringResource(R.string.device_note), note, { note = it })
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { scanner.launch(barcodeOptions()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Text(
                        text = stringResource(R.string.device_scan_barcode),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Button(
                    onClick = {
                        viewModel.addDevice(model, serial, power, method, note)
                        model = ""
                        serial = ""
                        power = ""
                        note = ""
                        method = EntryMethod.MANUAL
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.device_add))
                }
            }
        }
    }
}

@Composable
private fun AttendeeSection(detail: ReportDetail, viewModel: VisitViewModel) {
    var org by remember { mutableStateOf(AttendeeOrg.POWER_COMPANY) }
    var name by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var orgName by rememberSaveable { mutableStateOf("") }

    SectionCard(title = stringResource(R.string.attendees_title)) {
        Column {
            detail.attendees.forEach { attendee ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = listOfNotNull(
                            attendee.fullName,
                            attendee.position,
                            attendee.orgName
                        ).joinToString(" - "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeAttendee(attendee) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            }
            if (detail.attendees.isEmpty()) {
                Text(
                    text = stringResource(R.string.attendees_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownField(
                label = stringResource(R.string.attendee_org),
                options = AttendeeOrg.entries.toList(),
                selected = org,
                optionLabel = { attendeeOrgLabel(it) },
                onSelect = { org = it }
            )
            AppTextField(stringResource(R.string.attendee_name), name, { name = it })
            AppTextField(stringResource(R.string.attendee_position), position, { position = it })
            if (org == AttendeeOrg.OTHER) {
                AppTextField(stringResource(R.string.attendee_org_name), orgName, { orgName = it })
            }
            Button(
                onClick = {
                    viewModel.addAttendee(org, name, position, orgName)
                    name = ""
                    position = ""
                    orgName = ""
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.attendee_add))
            }
        }
    }
}

/** Portrait, single scan, no beep — the phone is often held over a rack. */
private fun barcodeOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES + ScanOptions.QR_CODE)
    .setBeepEnabled(false)
    .setOrientationLocked(true)
