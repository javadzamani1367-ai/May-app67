package ir.ilam.inspection.ui.approvals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/**
 * Manager only: what is waiting for a decision.
 *
 * Cases this phone holds can be opened and decided here and now. Cases the
 * server knows about but this phone has not synced are listed separately and
 * plainly marked, because a queue that quietly leaves them out would tell the
 * manager everything is done when it is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text(stringResource(R.string.approval_pending_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }

            SectionCard(title = stringResource(R.string.approval_on_this_phone)) {
                Column {
                    if (local.isEmpty()) {
                        Empty(R.string.approval_pending_empty)
                    }
                    local.forEachIndexed { index, report ->
                        if (index > 0) HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCase(report.id) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = TrackingCode.forDisplay(report.displayCode).orEmpty(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = listOfNotNull(
                                    report.county,
                                    PersianNumbers.toPersian(report.expertCode).ifBlank { null },
                                    report.approvalAt?.let { PersianDate.formatWithTime(it) }
                                ).joinToString(" — "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (remoteOnly.isNotEmpty() || state.offline) {
                SectionCard(title = stringResource(R.string.approval_on_server)) {
                    Column {
                        if (state.offline) {
                            Empty(R.string.approval_server_offline)
                        }
                        remoteOnly.forEach { pending ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    text = PersianNumbers.toPersian(pending.trackingCode),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = listOfNotNull(
                                        pending.county.ifBlank { null },
                                        PersianNumbers.toPersian(pending.expertCode).ifBlank { null },
                                        PersianDate.formatWithTime(pending.submittedAt)
                                    ).joinToString(" — "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (remoteOnly.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.approval_not_synced_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.users_refresh))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
