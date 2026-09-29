package ir.roozban.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.EmptyState
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.SectionTitle
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.designsystem.theme.TagColors
import java.time.ZoneId

/** Done tasks, newest first: search, send one back to the open list, or take it off this list. */
@Composable
internal fun DoneScreen(onBack: () -> Unit, viewModel: DoneViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.tasks_undo)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            val result = snackbar.showSnackbar(m.text, actionLabel = m.undo?.let { undoLabel }, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) m.undo?.let(viewModel::undo)
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text(stringResource(R.string.done_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.total > 0) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::search,
                        placeholder = { Text("جست‌وجو در کارهای انجام‌شده") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        "${PersianDigits.format(state.total)} کار انجام‌شده. برداشتن از این فهرست، کار را از گزارش‌ها و آمار حذف نمی‌کند. " +
                            "هر کار یک سال پس از انجام، از همه‌جا پاک می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.loaded && state.total == 0) {
                item {
                    EmptyState(
                        DsR.drawable.ic_check,
                        "هنوز کاری انجام نشده",
                        "هر کاری را که تیک بزنی، این‌جا با تاریخ و ساعت انجامش می‌ماند.",
                        Roozban.colors.completed,
                    )
                }
            }
            if (state.total > 0 && state.days.isEmpty()) {
                item { Text("چیزی پیدا نشد", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            state.days.forEach { day ->
                item(key = "day:" + day.label) {
                    SectionTitle("${day.label} (${PersianDigits.format(day.entries.size)})", modifier = Modifier.padding(top = 8.dp))
                }
                items(day.entries, key = { it.item.taskId + ":" + (it.item.occurrence?.toEpochDay() ?: -1) }) { entry ->
                    DoneCard(entry, onReopen = { viewModel.reopen(entry) }, onRemove = { viewModel.remove(entry) })
                }
            }
        }
    }
}

@Composable
private fun DoneCard(entry: DoneEntry, onReopen: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(DsR.drawable.ic_check), null, tint = Roozban.colors.completed.color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val time = entry.item.at.atZone(ZoneId.systemDefault()).toLocalTime()
                    Text(PersianDateFormatter.time(time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    if (!entry.canReopen) {
                        Text("کار تکراری", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    }
                    entry.project?.let { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val color = TagColors.color(p.color)
                            Icon(painterResource(DsR.drawable.ic_folder), null, tint = color, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(p.name, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(DsR.drawable.ic_more_vert), "گزینه‌ها") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (entry.canReopen) {
                        DropdownMenuItem(
                            text = { Text("برگرداندن به کارهای باز") },
                            leadingIcon = { Icon(painterResource(DsR.drawable.ic_undo), null) },
                            onClick = {
                                menu = false
                                onReopen()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("برداشتن از این فهرست", color = Roozban.colors.error.color) },
                        leadingIcon = { Icon(painterResource(DsR.drawable.ic_delete), null, tint = Roozban.colors.error.color) },
                        onClick = {
                            menu = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}
