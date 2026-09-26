package ir.ilam.inspection.ui.pending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.appFieldColors
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.util.PersianNumbers

/** One queue of cases, with search over it. */
@Composable
fun CaseListTab(
    status: ReportStatus,
    cases: List<ReportEntity>,
    query: String,
    deviceCounts: Map<String, Int>,
    onQuery: (String) -> Unit,
    daysWaiting: (ReportEntity) -> Int,
    onOpen: (ReportEntity) -> Unit,
    onDeleteRequest: (ReportEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_filter))
                    }
                }
            } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md)
        )
        Text(
            text = stringResource(R.string.cases_count, PersianNumbers.toPersian(cases.size)) +
                "  ·  " + stringResource(R.string.card_long_press_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screen + 4.dp)
        )
        if (cases.isEmpty()) {
            EmptyState(
                message = stringResource(
                    when {
                        query.isNotBlank() -> R.string.search_empty
                        status == ReportStatus.PENDING -> R.string.list_empty_pending
                        status == ReportStatus.VISITED -> R.string.list_empty_visited
                        else -> R.string.list_empty_archive
                    }
                ),
                icon = when {
                    query.isNotBlank() -> Icons.Outlined.SearchOff
                    status == ReportStatus.PENDING -> Icons.Outlined.PendingActions
                    status == ReportStatus.VISITED -> Icons.Outlined.TaskAlt
                    else -> Icons.Outlined.Inventory2
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = Spacing.sm, bottom = 96.dp)
            ) {
                items(cases, key = { it.id }) { report ->
                    CaseCard(
                        report = report,
                        daysWaiting = daysWaiting(report),
                        devices = deviceCounts[report.id] ?: 0,
                        onClick = { onOpen(report) },
                        onDeleteRequest = { onDeleteRequest(report) }
                    )
                }
            }
        }
    }
}
