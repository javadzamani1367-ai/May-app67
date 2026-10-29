package ir.minerfinder.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.graph
import ir.minerfinder.ui.theme.Palette

/** Audio inputs as large radio rows; an unplugged choice still shows, marked missing. */
@Composable
fun InputPicker(selected: Int, onSelect: (Int) -> Unit) {
    val inputs by LocalContext.current.graph.audioInputs.inputs.collectAsState()
    Column {
        inputs.forEach { input ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onSelect(input.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = input.id == selected, onClick = { onSelect(input.id) })
                Text(input.name)
            }
        }
        if (inputs.none { it.id == selected }) {
            Text(stringResource(R.string.input_missing), color = Palette.Warm)
        }
    }
}
