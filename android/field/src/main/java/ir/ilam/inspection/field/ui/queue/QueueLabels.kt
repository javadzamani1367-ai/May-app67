package ir.ilam.inspection.field.ui.queue

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.ServerStatus
import ir.ilam.inspection.field.data.SyncState
import ir.ilam.inspection.ui.theme.Tone

@Composable
fun kindLabel(kind: FieldKind): String = stringResource(
    when (kind) {
        FieldKind.CRYPTO -> R.string.field_kind_crypto
        FieldKind.ILLEGAL -> R.string.field_kind_illegal
        FieldKind.THERMAL -> R.string.field_kind_thermal
        FieldKind.FEEDER -> R.string.field_kind_feeder
        FieldKind.BURN -> R.string.field_kind_burn
        FieldKind.FEEDER_LINE -> R.string.field_kind_line
    }
)

/** The icon and colour of each kind, the same as on the choice screen. */
fun kindIcon(kind: FieldKind): ImageVector = when (kind) {
    FieldKind.CRYPTO -> Icons.Filled.Memory
    FieldKind.ILLEGAL -> Icons.Filled.Bolt
    FieldKind.BURN -> Icons.Filled.LocalFireDepartment
    FieldKind.THERMAL -> Icons.Filled.Thermostat
    FieldKind.FEEDER -> Icons.Filled.ElectricalServices
    FieldKind.FEEDER_LINE -> Icons.Filled.Timeline
}

fun kindTone(kind: FieldKind): Tone = when (kind) {
    FieldKind.CRYPTO, FieldKind.BURN -> Tone.DANGER
    FieldKind.ILLEGAL -> Tone.WARNING
    FieldKind.THERMAL -> Tone.ACCENT
    FieldKind.FEEDER -> Tone.INFO
    FieldKind.FEEDER_LINE -> Tone.BRAND
}

/** Where the office has taken it: amber while it waits on someone, green when it is done, red when refused. */
fun statusTone(status: ServerStatus): Tone = when (status) {
    ServerStatus.REGISTERED, ServerStatus.REVIEWING -> Tone.INFO
    ServerStatus.REFERRED -> Tone.ACCENT
    ServerStatus.RESULT, ServerStatus.CLOSED -> Tone.SUCCESS
    ServerStatus.REJECTED -> Tone.DANGER
    ServerStatus.REVISIT -> Tone.WARNING
}

@Composable
fun syncLabel(state: SyncState): String = stringResource(
    when (state) {
        SyncState.DRAFT -> R.string.field_state_draft
        SyncState.PENDING -> R.string.field_state_pending
        SyncState.SENT -> R.string.field_state_sent
        SyncState.ERROR -> R.string.field_state_error
    }
)

/** Waiting is amber, arrived is green, refused is red: each colour one meaning. */
fun syncTone(state: SyncState): Tone = when (state) {
    SyncState.DRAFT -> Tone.NEUTRAL
    SyncState.PENDING -> Tone.WARNING
    SyncState.SENT -> Tone.SUCCESS
    SyncState.ERROR -> Tone.DANGER
}

@Composable
fun statusLabel(status: ServerStatus): String = stringResource(
    when (status) {
        ServerStatus.REGISTERED -> R.string.field_status_registered
        ServerStatus.REVIEWING -> R.string.field_status_reviewing
        ServerStatus.REFERRED -> R.string.field_status_referred
        ServerStatus.RESULT -> R.string.field_status_result
        ServerStatus.CLOSED -> R.string.field_status_closed
        ServerStatus.REJECTED -> R.string.field_status_rejected
        ServerStatus.REVISIT -> R.string.field_status_revisit
    }
)
