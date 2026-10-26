package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Centers
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

/**
 * Before an Excel or Word of the statistics: which format, and which columns —
 * ticked from the manager's defaults for that format, each one changeable here.
 * The form's own columns first, then the system's extras.
 */
@Composable
fun CenterExportDialog(page: Centers.Page, initial: String, onDismiss: () -> Unit, onExport: (String, List<String>) -> Unit) {
    var format by remember { mutableStateOf(initial) }
    var picked by remember { mutableStateOf(page.exportColumns[initial].orEmpty().toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ct_export_title)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("xlsx" to R.string.ct_excel, "docx" to R.string.ct_word).forEach { (f, label) ->
                        ToneChip(stringResource(label), format == f, Tone.BRAND, onClick = {
                            format = f
                            picked = page.exportColumns[f].orEmpty().toSet()
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = { picked = page.columns.map { it.key }.toSet() }) { Text(stringResource(R.string.ct_cols_all)) }
                    TextButton(onClick = { picked = page.columns.filter { it.template }.map { it.key }.toSet() }) {
                        Text(stringResource(R.string.ct_cols_form))
                    }
                    TextButton(onClick = { picked = emptySet() }) { Text(stringResource(R.string.ct_cols_none)) }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(page.columns, key = { it.key }) { column ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                            .clickable { picked = if (column.key in picked) picked - column.key else picked + column.key }) {
                            Checkbox(checked = column.key in picked, onCheckedChange = null)
                            Text(column.label + if (column.template) "" else " ⁺", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(format, page.columns.map { it.key }.filter { it in picked }) }, enabled = picked.isNotEmpty()) {
                Text(stringResource(R.string.ct_export_make))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
