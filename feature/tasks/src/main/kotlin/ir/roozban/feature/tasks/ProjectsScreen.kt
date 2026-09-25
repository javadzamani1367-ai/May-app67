package ir.roozban.feature.tasks

import androidx.compose.ui.text.font.FontWeight
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.designsystem.components.EmptyState
import ir.roozban.core.designsystem.components.StatusPill
import ir.roozban.core.designsystem.components.roleOf
import ir.roozban.core.designsystem.components.IconBadge
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.RoozbanFab
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.model.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectsScreen(
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text(stringResource(R.string.projects_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(DsR.drawable.ic_settings), stringResource(R.string.tasks_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            RoozbanFab(
                onClick = { creating = true },
                icon = { Icon(painterResource(DsR.drawable.ic_add), null) },
                text = { Text(stringResource(R.string.projects_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.active, key = { it.project.id }) { item ->
                ProjectRow(item, onOpenProject, onEdit = { editing = it }, onArchive = { viewModel.setArchived(it, true) }, onDelete = { deleting = it })
            }
            if (state.archived.isNotEmpty()) {
                item(key = "archived-title") {
                    Text(
                        stringResource(R.string.projects_archived),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                    )
                }
                items(state.archived, key = { it.project.id }) { item ->
                    ProjectRow(item, onOpenProject, onEdit = { editing = it }, onArchive = { viewModel.setArchived(it, false) }, onDelete = { deleting = it })
                }
            }
            if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
                item(key = "empty") {
                    EmptyState(DsR.drawable.ic_folder, stringResource(R.string.projects_empty_title), stringResource(R.string.projects_empty_body), Roozban.colors.warning)
                }
            }
        }
    }

    if (creating) {
        ProjectDialog(initial = null, onConfirm = { name, color -> viewModel.create(name, color); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { project ->
        ProjectDialog(initial = project, onConfirm = { name, color -> viewModel.update(project, name, color); editing = null }, onDismiss = { editing = null })
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.projects_delete_confirm, project.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(project); deleting = null }) {
                    Text(stringResource(R.string.projects_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun ProjectRow(
    item: ProjectItem,
    onOpen: (String) -> Unit,
    onEdit: (Project) -> Unit,
    onArchive: (Project) -> Unit,
    onDelete: (Project) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val color = TagColors.color(item.project.color)
    AppCard(
        onClick = { onOpen(item.project.id) },
        accent = color,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(DsR.drawable.ic_folder, roleOf(color), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(item.project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusPill(stringResource(R.string.projects_open_count, item.openCount), roleOf(color))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(DsR.drawable.ic_more_vert), stringResource(R.string.projects_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.projects_rename)) }, onClick = { menu = false; onEdit(item.project) })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (item.project.archived) R.string.projects_unarchive else R.string.projects_archive)) },
                        onClick = { menu = false; onArchive(item.project) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete(item.project) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectDialog(initial: Project?, onConfirm: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.projects_new else R.string.projects_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.projects_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.projects_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until TagColors.count) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(TagColors.color(i))
                                .border(
                                    width = if (i == color) 3.dp else 0.dp,
                                    color = if (i == color) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { color = i },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
