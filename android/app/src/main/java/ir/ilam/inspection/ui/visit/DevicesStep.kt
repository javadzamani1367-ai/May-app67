package ir.ilam.inspection.ui.visit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import ir.ilam.inspection.ui.common.AddPanel
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.InfoTile
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.attendeeOrgLabel
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.data.repo.SnippetFields
import ir.ilam.inspection.ui.common.SnippetField
import ir.ilam.inspection.ui.theme.Tone

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

    SectionCard(
        title = stringResource(R.string.devices_title),
        subtitle = stringResource(R.string.devices_hint),
        icon = Icons.Filled.Memory,
        tone = Tone.DANGER
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                InfoTile(
                    label = stringResource(R.string.device_count_label),
                    value = PersianNumbers.toPersian(detail.deviceCount),
                    icon = Icons.Filled.Memory,
                    tone = if (detail.deviceCount > 0) Tone.DANGER else Tone.NEUTRAL,
                    modifier = Modifier.weight(1f)
                )
                InfoTile(
                    label = stringResource(R.string.device_power_label),
                    value = stringResource(R.string.unit_watt, PersianNumbers.grouped(detail.totalPower)),
                    icon = Icons.Filled.Bolt,
                    tone = if (detail.totalPower > 0) Tone.ACCENT else Tone.NEUTRAL,
                    modifier = Modifier.weight(1.3f)
                )
            }
            detail.devices.forEach { device ->
                DeviceCard(device = device, onDelete = { viewModel.removeDevice(device) })
            }
            if (detail.devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.devices_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AddPanel(title = stringResource(R.string.device_new)) {
                AppTextField(stringResource(R.string.device_model), model, { model = it })
                AppTextField(
                    label = stringResource(R.string.device_serial),
                    value = serial,
                    onValueChange = {
                        serial = it
                        method = EntryMethod.MANUAL
                        viewModel.clearDeviceError()
                    },
                    error = if (duplicate) stringResource(R.string.device_duplicate_serial) else null,
                    ltr = true
                )
                NumberField(
                    label = stringResource(R.string.device_power),
                    value = power,
                    onValueChange = { power = it },
                    decimal = true
                )
                SnippetField(
                    label = stringResource(R.string.device_note),
                    value = note,
                    onValueChange = { note = it },
                    fieldKey = SnippetFields.DEVICE_NOTE
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.device_scan_barcode),
                        onClick = { scanner.launch(barcodeOptions()) },
                        icon = Icons.Filled.QrCodeScanner,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = stringResource(R.string.device_add),
                        onClick = {
                            viewModel.addDevice(model, serial, power, method, note)
                            model = ""
                            serial = ""
                            power = ""
                            note = ""
                            method = EntryMethod.MANUAL
                        },
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f)
                    )
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

    SectionCard(
        title = stringResource(R.string.attendees_title),
        subtitle = stringResource(R.string.attendees_hint),
        icon = Icons.Filled.Groups,
        tone = Tone.INFO
    ) {
        Column {
            detail.attendees.forEach { attendee ->
                AttendeeRow(attendee = attendee, onDelete = { viewModel.removeAttendee(attendee) })
            }
            if (detail.attendees.isEmpty()) {
                Text(
                    text = stringResource(R.string.attendees_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AddPanel(title = stringResource(R.string.attendee_new)) {
                DropdownField(
                    label = stringResource(R.string.attendee_org),
                    options = AttendeeOrg.entries.toList(),
                    selected = org,
                    optionLabel = { attendeeOrgLabel(it) },
                    onSelect = { org = it }
                )
                SnippetField(
                    label = stringResource(R.string.attendee_name),
                    value = name,
                    onValueChange = { name = it },
                    fieldKey = SnippetFields.ATTENDEE_NAME
                )
                SnippetField(
                    label = stringResource(R.string.attendee_position),
                    value = position,
                    onValueChange = { position = it },
                    fieldKey = SnippetFields.ATTENDEE_POSITION
                )
                if (org == AttendeeOrg.OTHER) {
                    AppTextField(stringResource(R.string.attendee_org_name), orgName, { orgName = it })
                }
                PrimaryButton(
                    text = stringResource(R.string.attendee_add),
                    onClick = {
                        viewModel.addAttendee(org, name, position, orgName)
                        name = ""
                        position = ""
                        orgName = ""
                    },
                    icon = Icons.Filled.PersonAdd,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

/** Portrait, single scan, no beep, and a windowed preview rather than a
 * full-screen camera — the phone is often held over a rack. */
private fun barcodeOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES + ScanOptions.QR_CODE)
    .setBeepEnabled(false)
    .setOrientationLocked(true)
    .setCaptureActivity(ScanActivity::class.java)
