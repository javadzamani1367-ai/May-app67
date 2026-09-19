package ir.ilam.inspection.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.sync.ServerCaseSync
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Sending cases to the central server, and — on the manager's app — fetching
 * everyone else's.
 *
 * Its own file because the settings screen is already at its length, and
 * because this is the one card whose wording has to be careful: an expert has
 * to be able to tell "the office has this case" from "the office has the
 * paperwork but not the photographs yet".
 */
@Composable
fun ServerSyncCard(
    configured: Boolean,
    busy: Boolean,
    pending: Int,
    lastRun: Long?,
    outcome: ServerCaseSync.Outcome?,
    autoSync: Boolean,
    onAutoSyncChange: (Boolean) -> Unit,
    onSync: () -> Unit
) {
    SectionCard(title = stringResource(R.string.server_sync_title)) {
        Column {
            if (!configured) {
                Text(
                    text = stringResource(R.string.server_sync_no_target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text(
                text = stringResource(
                    R.string.server_sync_unsent,
                    PersianNumbers.toPersian(pending)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    if (UserRole.isManager) {
                        R.string.server_sync_hint_manager
                    } else {
                        R.string.server_sync_hint_expert
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            Button(
                onClick = onSync,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.server_sync_now)) }

            // Wi-Fi only, and the label says so: an expert paying for data on
            // their own SIM has to be able to see that from the switch itself,
            // not discover it from a bill.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.server_sync_auto),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.server_sync_auto_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoSync, onCheckedChange = onAutoSyncChange)
            }

            lastRun?.let {
                Text(
                    text = stringResource(R.string.server_sync_last, PersianDate.formatWithTime(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            outcome?.let { ServerSyncOutcome(it) }
        }
    }
}

/**
 * The result in plain numbers rather than a tick.
 *
 * "Synced" on its own is the wrong word for this: rows and files travel
 * separately, so a run can genuinely send five cases and none of their photos,
 * and the expert has to know which of the two happened before they leave a
 * place with signal.
 */
@Composable
private fun ServerSyncOutcome(outcome: ServerCaseSync.Outcome) {
    // Counted lines first, then the plain ones. The pairs are built without
    // touching resources so the numbers and their wording stay one list.
    val counted = listOf(
        outcome.pushed to R.string.server_sync_pushed,
        outcome.filesUp to R.string.server_sync_files_up,
        outcome.pulled to R.string.server_sync_pulled,
        outcome.filesDown to R.string.server_sync_files_down,
        outcome.failed to R.string.server_sync_failed
    ).filter { (count, _) -> count > 0 }

    counted.forEach { (count, label) ->
        OutcomeLine(stringResource(label, PersianNumbers.toPersian(count)))
    }
    if (counted.isEmpty() && outcome.reachedServer) {
        OutcomeLine(stringResource(R.string.server_sync_nothing))
    }
    if (outcome.offline) OutcomeLine(stringResource(R.string.server_sync_offline))
    outcome.refusal?.let { OutcomeLine(it) }
}

@Composable
private fun OutcomeLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp)
    )
}
