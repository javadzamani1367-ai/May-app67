package ir.minerfinder.ui.signatures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.ui.theme.Palette

/** Label, miner model and a note for a freshly recorded signature. */
@Composable
fun NameDialog(onSave: (label: String, model: String, note: String) -> Unit, onCancel: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.signatures_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.signatures_label)) }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.signatures_model)) }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.signatures_note)) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label.trim(), model.trim(), note.trim()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDelete(name: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.signatures_delete_title)) },
        text = { Text(name) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = Palette.Hot) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
