package ir.ilam.inspection.ui.centers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Centers
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.common.YesNoRow
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/**
 * One cell of a statistics row: read-only when the user may not write it, else
 * the right input for its type, where it came from (system, file, typed), and
 * for a conflicting cell the choice between the system's value, the file's, or
 * the one typed here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CenterCell(
    column: Centers.Column,
    detail: Centers.Detail,
    text: String?,
    choice: String?,
    writable: Boolean,
    onEdit: (String) -> Unit,
    onChoose: (String) -> Unit
) {
    val key = column.key
    val value = detail.row.values[key]
    if (!writable) {
        ValueRow(column.label, CenterFormat.show(column.type, value).ifBlank { "—" })
        return
    }
    val current = text ?: CenterFormat.editText(column.type, value)
    val source = when (detail.from[key]) {
        "system" -> stringResource(R.string.ct_from_system)
        "file" -> stringResource(R.string.ct_from_file)
        "own" -> stringResource(R.string.ct_from_own)
        else -> null
    }
    val label = column.label + (source?.let { " ($it)" } ?: "")
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        when (column.type) {
            "tick" -> YesNoRow(label, current == "1", { onEdit(if (it) "1" else "0") })
            "choice", "status" -> {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    column.choices.forEach { option -> ToneChip(option, current == option, Tone.BRAND, onClick = { onEdit(option) }) }
                }
                AppTextField(stringResource(R.string.ct_other_value), if (current in column.choices) "" else current, onEdit)
            }
            "int", "money", "kw" -> AppTextField(label, current, onEdit, keyboardType = KeyboardType.Decimal, ltr = true)
            "date" -> AppTextField(label + " — " + stringResource(R.string.ct_date_hint), current, onEdit)
            "sheba" -> AppTextField(label, current, onEdit, ltr = true)
            else -> AppTextField(label, current, onEdit)
        }
        if (key in detail.row.conflicts) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tavan.colors.warning.container)
                .padding(Spacing.sm)) {
                Text(stringResource(R.string.ct_conflict_text, CenterFormat.show(column.type, detail.system[key]),
                    CenterFormat.show(column.type, detail.file[key])), style = MaterialTheme.typography.bodyMedium,
                    color = Tavan.colors.warning.onContainer)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ToneChip(stringResource(R.string.ct_pick_system), choice == "system", Tone.WARNING, onClick = { onChoose("system") })
                    ToneChip(stringResource(R.string.ct_pick_file), choice == "file", Tone.WARNING, onClick = { onChoose("file") })
                    ToneChip(stringResource(R.string.ct_pick_own), choice == "own", Tone.WARNING, onClick = {
                        onChoose("own")
                        onEdit(current)
                    })
                }
            }
        } else if (detail.system[key] != null && detail.from[key] != "system" && choice == null) {
            TextButton(onClick = { onChoose("system") }) {
                Text(stringResource(R.string.ct_back_to_system, CenterFormat.show(column.type, detail.system[key])))
            }
        } else if (choice == "system") {
            Text(stringResource(R.string.ct_will_use_system), style = MaterialTheme.typography.bodySmall, color = Tavan.colors.info.strong)
        }
    }
}
