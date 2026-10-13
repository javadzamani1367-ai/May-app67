package ir.ilam.inspection.ui.fielditems

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.FieldManage
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/** The same icon per kind as the field app itself shows. */
fun fieldKindIcon(kind: Int): ImageVector = when (kind) {
    1 -> Icons.Filled.Memory
    2 -> Icons.Filled.Bolt
    3 -> Icons.Filled.Thermostat
    4 -> Icons.Filled.ElectricalServices
    5 -> Icons.Filled.LocalFireDepartment
    else -> Icons.Filled.Timeline
}

fun fieldKindTone(kind: Int): Tone = when (kind) {
    1, 5 -> Tone.DANGER
    2 -> Tone.WARNING
    else -> Tone.INFO
}

fun fieldKindLabel(kind: Int): Int = when (kind) {
    1 -> R.string.fi_kind_crypto
    2 -> R.string.fi_kind_illegal
    3 -> R.string.fi_kind_thermal
    4 -> R.string.fi_kind_feeder
    5 -> R.string.fi_kind_burn
    else -> R.string.fi_kind_line
}

fun fieldStatusLabel(status: Int): Int = when (status) {
    FieldManage.STATUS_REGISTERED -> R.string.fi_status_registered
    FieldManage.STATUS_REVIEWING -> R.string.fi_status_reviewing
    FieldManage.STATUS_REFERRED -> R.string.fi_status_referred
    FieldManage.STATUS_RESULT -> R.string.fi_status_result
    FieldManage.STATUS_CLOSED -> R.string.fi_status_closed
    FieldManage.STATUS_REJECTED -> R.string.fi_status_rejected
    else -> R.string.fi_status_revisit
}

/** New is information, a referral is work out of the office, a result is done, rejected and revisit need a reason. */
fun fieldStatusTone(status: Int): Tone = when (status) {
    FieldManage.STATUS_REGISTERED -> Tone.INFO
    FieldManage.STATUS_REVIEWING -> Tone.BRAND
    FieldManage.STATUS_REFERRED -> Tone.ACCENT
    FieldManage.STATUS_RESULT -> Tone.SUCCESS
    FieldManage.STATUS_CLOSED -> Tone.NEUTRAL
    FieldManage.STATUS_REJECTED -> Tone.DANGER
    else -> Tone.WARNING
}

@Composable
fun ProblemLine(problem: FieldProblem?) {
    val text = when (problem) {
        null -> return
        FieldProblem.Offline -> stringResource(R.string.fi_offline)
        is FieldProblem.Refused -> problem.message.ifBlank { stringResource(R.string.fi_refused) }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Tavan.colors.warning.strong,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)
    )
}
