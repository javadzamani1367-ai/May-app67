package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.PersianNumbers

/**
 * Why some of the last gallery pick was not taken: older than a day, without
 * a date, or unreadable. Says it in words, so a missing photo is never silent.
 */
@Composable
fun ImportNote(editor: ItemEditor) {
    // Left with the page: a refusal seen once is not shown again over later captures.
    DisposableEffect(editor) { onDispose { editor.clearImportNote() } }
    val outcome = editor.lastImport ?: return
    if (outcome.rejected == 0) return
    val parts = buildList {
        if (outcome.tooOld > 0) add(stringResource(R.string.import_too_old, PersianNumbers.toPersian(outcome.tooOld)))
        if (outcome.noDate > 0) add(stringResource(R.string.import_no_date, PersianNumbers.toPersian(outcome.noDate)))
        if (outcome.failed > 0) add(stringResource(R.string.import_failed, PersianNumbers.toPersian(outcome.failed)))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Text(
            if (outcome.added > 0) {
                stringResource(R.string.import_partly, PersianNumbers.toPersian(outcome.added), parts.joinToString("، "))
            } else {
                stringResource(R.string.import_rejected, parts.joinToString("، "))
            },
            style = MaterialTheme.typography.bodySmall,
            color = Tavan.colors.warning.strong,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = editor::clearImportNote, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.form_remove), modifier = Modifier.size(18.dp))
        }
    }
}
