package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/** A checklist as chips: each tap ticks or unticks one sign. Big enough for a gloved thumb. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckChips(
    keys: List<String>,
    selected: Set<String>,
    label: @Composable (String) -> String,
    onToggle: (String) -> Unit,
    tone: Tone = Tone.ACCENT
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        keys.forEach { key ->
            ToneChip(text = label(key), selected = key in selected, tone = tone, onClick = { onToggle(key) })
        }
    }
}

/** A count with − and + buttons: typing a number with gloves on is slower than tapping. */
@Composable
fun CountRow(label: String, count: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((count - 1).coerceAtLeast(0)) }, enabled = count > 0) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(PersianNumbers.toPersian(count), style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = { onChange(count + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

/** A small heading inside a section card. */
@Composable
fun SubHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md, bottom = 2.dp)
    )
}
