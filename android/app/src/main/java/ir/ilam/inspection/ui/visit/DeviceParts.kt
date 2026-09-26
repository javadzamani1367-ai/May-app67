package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.AttendeeEntity
import ir.ilam.inspection.data.db.DeviceEntity
import ir.ilam.inspection.data.model.AttendeeOrg
import ir.ilam.inspection.data.model.EntryMethod
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.ConfirmDeleteButton
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.common.attendeeOrgLabel
import ir.ilam.inspection.ui.common.entryMethodLabel
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * One mining device as a card: what it is, its serial (the thing a later
 * inspection checks against), its power, and whether the serial was scanned
 * or typed — a typed serial is worth a second look.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceCard(device: DeviceEntity, onDelete: (() -> Unit)? = null) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(icon = Icons.Filled.Memory, tone = Tone.DANGER, size = 42.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(
                    text = PersianNumbers.toPersian(device.rowNumber) + ". " +
                        (device.model?.takeIf { it.isNotBlank() } ?: stringResource(R.string.device_unnamed)),
                    style = MaterialTheme.typography.titleSmall
                )
                device.serialNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.device_serial_value, it),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    device.powerWatt?.let {
                        StatusBadge(
                            text = stringResource(R.string.unit_watt, PersianNumbers.grouped(it)),
                            tone = Tone.ACCENT,
                            icon = Icons.Filled.Bolt
                        )
                    }
                    val method = EntryMethod.of(device.entryMethod)
                    StatusBadge(
                        text = entryMethodLabel(method),
                        tone = if (method == EntryMethod.BARCODE) Tone.INFO else Tone.NEUTRAL,
                        icon = if (method == EntryMethod.BARCODE) Icons.Filled.QrCode2 else Icons.Filled.Keyboard
                    )
                }
                device.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            onDelete?.let { ConfirmDeleteButton(itemName = device.model ?: device.serialNumber, onConfirm = it) }
        }
    }
}

/** One person present at the visit, with their organisation as a coloured badge. */
@Composable
fun AttendeeRow(attendee: AttendeeEntity, onDelete: (() -> Unit)? = null) {
    val org = AttendeeOrg.of(attendee.organization)
    val (icon, tone) = when (org) {
        AttendeeOrg.POWER_COMPANY -> Icons.Filled.PowerSettingsNew to Tone.ACCENT
        AttendeeOrg.SECURITY_POLICE -> Icons.Filled.LocalPolice to Tone.BRAND
        AttendeeOrg.OTHER -> Icons.Filled.Business to Tone.NEUTRAL
    }
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(icon = icon, tone = tone, size = 38.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(attendee.fullName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOfNotNull(
                        attendee.position?.takeIf { it.isNotBlank() },
                        if (org == AttendeeOrg.OTHER) attendee.orgName else attendeeOrgLabel(org)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            onDelete?.let { ConfirmDeleteButton(itemName = attendee.fullName, onConfirm = it) }
        }
    }
}
