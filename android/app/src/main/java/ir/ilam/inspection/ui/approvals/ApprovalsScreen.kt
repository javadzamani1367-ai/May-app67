package ir.ilam.inspection.ui.approvals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.GroupHeader
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.pending.CaseCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Manager only: what is waiting for a decision.
 *
 * Cases this phone holds can be opened and decided here and now, and are
 * shown as the same cards as everywhere else. Cases the server knows about but
 * this phone has not synced are listed separately and plainly marked, because
 * a queue that quietly leaves them out would tell the manager everything is
 * done when it is not.
 */
@Composable
fun ApprovalsScreen(onBack: () -> Unit, onOpenCase: (String) -> Unit) {
    val appContainer = LocalContext.current.container
    val viewModel: ApprovalsViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { ApprovalsViewModel(it) } }
    )
    val local by viewModel.local.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Cases already listed from this phone must not appear twice.
    val localIds = local.map { it.id }.toSet()
    val remoteOnly = state.remote.filter { it.reportId !in localIds }

    Scaffold(
        topBar = {
            TavanTopBar(
                title = stringResource(R.string.approval_pending_title),
                subtitle = stringResource(R.string.approval_subtitle, PersianNumbers.toPersian(local.size + remoteOnly.size)),
                onBack = onBack
            ) {
                HeaderAction(Icons.Filled.Refresh, stringResource(R.string.users_refresh), viewModel::refresh)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }

            GroupHeader(stringResource(R.string.approval_on_this_phone)) {
                StatusBadge(PersianNumbers.toPersian(local.size), tone = Tone.WARNING, icon = Icons.Filled.PhoneAndroid)
            }
            if (local.isEmpty()) Muted(R.string.approval_pending_empty)
            local.forEach { report ->
                CaseCard(
                    report = report,
                    daysWaiting = 0,
                    onClick = { onOpenCase(report.id) },
                    onDeleteRequest = {},
                    horizontalPadding = 0.dp
                )
            }

            if (remoteOnly.isNotEmpty() || state.offline) {
                SectionCard(
                    title = stringResource(R.string.approval_on_server),
                    icon = Icons.Filled.Cloud,
                    tone = Tone.INFO,
                    modifier = Modifier.padding(top = Spacing.md)
                ) {
                    if (state.offline) Muted(R.string.approval_server_offline)
                    remoteOnly.forEach { pending ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ToneIcon(icon = Icons.Filled.CloudQueue, tone = Tone.INFO, size = 36.dp)
                            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                                Text(
                                    text = PersianNumbers.toPersian(pending.trackingCode),
                                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr)
                                )
                                Text(
                                    text = listOfNotNull(
                                        pending.county.ifBlank { null },
                                        PersianNumbers.toPersian(pending.expertCode).ifBlank { null },
                                        PersianDate.formatWithTime(pending.submittedAt)
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (remoteOnly.isNotEmpty()) Muted(R.string.approval_not_synced_hint)
                }
            }
        }
    }
}

@Composable
private fun Muted(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
