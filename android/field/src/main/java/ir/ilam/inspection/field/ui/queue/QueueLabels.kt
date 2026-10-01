package ir.ilam.inspection.field.ui.queue

import androidx.compose.runtime.Composable
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
    }
)

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
