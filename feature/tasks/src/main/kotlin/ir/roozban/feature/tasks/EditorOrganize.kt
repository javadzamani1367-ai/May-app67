package ir.roozban.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.model.Label
import ir.roozban.core.model.Project
import ir.roozban.core.model.Task

/** Project, labels and subtasks of the task being edited. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrganizeSection(
    task: Task,
    projects: List<Project>,
    labels: List<Label>,
    subtasks: List<Task>,
    onProject: (String?) -> Unit,
    onToggleLabel: (String) -> Unit,
    onAddLabel: (String) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (Task) -> Unit,
    onDeleteSubtask: (Task) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Project
        var menuOpen by remember { mutableStateOf(false) }
        val current = projects.firstOrNull { it.id == task.projectId }
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { menuOpen = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(DsR.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.editor_project), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (current != null) ColorDot(current.color)
                Spacer(Modifier.width(6.dp))
                Text(
                    current?.name ?: stringResource(R.string.editor_no_project),
                    color = if (current != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_no_project)) },
                    onClick = {
                        onProject(null)
                        menuOpen = false
                    },
                )
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        leadingIcon = { ColorDot(project.color) },
                        onClick = {
                            onProject(project.id)
                            menuOpen = false
                        },
                    )
                }
            }
        }

        // Labels
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(DsR.drawable.ic_label), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.editor_labels), style = MaterialTheme.typography.bodyLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                labels.forEach { label ->
                    FilterChip(
                        selected = label.id in task.labelIds,
                        onClick = { onToggleLabel(label.id) },
                        label = { Text(label.name) },
                        leadingIcon = { ColorDot(label.color) },
                    )
                }
            }
            var newLabel by remember { mutableStateOf("") }
            OutlinedTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                placeholder = { Text(stringResource(R.string.editor_new_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onAddLabel(newLabel)
                    newLabel = ""
                }),
            )
        }

        // Subtasks (only for top-level tasks)
        if (task.parentId == null) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(DsR.drawable.ic_checklist), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.editor_subtasks), style = MaterialTheme.typography.bodyLarge)
                }
                subtasks.forEach { sub ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sub.isCompleted, onCheckedChange = { onToggleSubtask(sub) })
                        Text(
                            sub.title,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null,
                        )
                        IconButton(onClick = { onDeleteSubtask(sub) }) {
                            Icon(painterResource(DsR.drawable.ic_close), stringResource(R.string.editor_delete), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                var newSubtask by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.editor_add_subtask)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onAddSubtask(newSubtask)
                        newSubtask = ""
                    }),
                )
            }
        }
    }
}

@Composable
internal fun ColorDot(color: Int, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(TagColors.color(color)))
}
