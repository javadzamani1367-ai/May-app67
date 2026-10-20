package ir.ilam.inspection.ui.fielditems

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.FieldManage
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * The field app's reports and inspections, as the office sees them: by status
 * (each tab with its count), by kind, and by a search over code, plate and
 * address. Read live from the server — these are not this phone's cases.
 */
@Composable
fun FieldItemsScreen(
    viewModel: FieldItemsViewModel,
    onBack: (() -> Unit)?,
    onOpen: (String) -> Unit,
    /** The illegal-power app starts here, with settings in the header instead of "back". */
    onSettings: (() -> Unit)? = null
) {
    val state by viewModel.list.collectAsStateWithLifecycle()
    val filter = state.filter
    // Each time the list is shown, so an item moved on its own page is in its new tab.
    LaunchedEffect(Unit) { viewModel.reload() }
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = stringResource(if (UserRole.isIllegal) R.string.ill_title else R.string.fi_title),
            subtitle = stringResource(R.string.fi_subtitle),
            onBack = onBack,
            showBrand = onBack == null
        ) {
            HeaderAction(Icons.Filled.Refresh, stringResource(R.string.fi_refresh), onClick = viewModel::reload)
            onSettings?.let { HeaderAction(Icons.Filled.Settings, stringResource(R.string.ill_settings), onClick = it) }
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        Column(modifier = Modifier.padding(horizontal = Spacing.screen)) {
            AppTextField(stringResource(R.string.fi_search), filter.query, viewModel::setQuery)
        }
        ChipRow {
            val total = state.counts.values.sum()
            ToneChip(count(stringResource(R.string.fi_all), total), filter.status == null && !filter.overdue, Tone.BRAND,
                onClick = { viewModel.setStatus(null) })
            if (state.overdue > 0) {
                ToneChip(count(stringResource(R.string.fi_overdue), state.overdue), filter.overdue, Tone.DANGER,
                    onClick = viewModel::showOverdue)
            }
            FieldManage.STATUSES.forEach { status ->
                ToneChip(count(stringResource(fieldStatusLabel(status)), state.counts[status] ?: 0), filter.status == status,
                    fieldStatusTone(status), onClick = { viewModel.setStatus(status) })
            }
        }
        // The server narrows the list by role; the chips only offer what can come back.
        val kinds = when {
            UserRole.isIllegal -> emptyList()
            UserRole.isExecutor -> FieldManage.KINDS - FieldManage.KIND_CRYPTO - FieldManage.KIND_ILLEGAL
            else -> FieldManage.KINDS
        }
        if (kinds.isNotEmpty()) ChipRow {
            ToneChip(stringResource(R.string.fi_all_kinds), filter.kind == null, Tone.BRAND, onClick = { viewModel.setKind(null) })
            kinds.forEach { kind ->
                ToneChip(stringResource(fieldKindLabel(kind)), filter.kind == kind, fieldKindTone(kind),
                    onClick = { viewModel.setKind(kind) })
            }
        }
        if (state.items.isEmpty() && state.loaded && !state.busy) {
            EmptyState(message = stringResource(R.string.fi_empty), icon = Icons.AutoMirrored.Filled.Assignment)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(state.items, key = { it.id }) { row -> FieldRowCard(row) { onOpen(row.id) } }
                if (state.more) {
                    item {
                        SecondaryButton(stringResource(R.string.fi_more), viewModel::loadMore, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) { content() }
}

private fun count(label: String, n: Int) = "$label (${PersianNumbers.toPersian(n)})"

@Composable
private fun FieldRowCard(row: FieldManage.Row, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.Top) {
            ToneIcon(icon = fieldKindIcon(row.kind), tone = fieldKindTone(row.kind), size = 44.dp)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.trackingCode.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                        modifier = Modifier.weight(1f)
                    )
                    if (row.overdue) StatusBadge(stringResource(R.string.fi_overdue), Tone.DANGER, solid = true)
                    else StatusBadge(stringResource(fieldStatusLabel(row.status)), fieldStatusTone(row.status))
                }
                Text(
                    stringResource(fieldKindLabel(row.kind)) + " · " + PersianDate.formatWithTime(row.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                (row.address ?: row.plate)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(
                        stringResource(R.string.fi_reporter, row.reporter),
                        row.expert?.let { stringResource(R.string.fi_expert, it) },
                        row.dueAt?.let { stringResource(R.string.fi_due, PersianDate.format(it)) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.overdue) Tavan.colors.danger.strong else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
