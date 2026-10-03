package ir.ilam.inspection.field.ui.thermal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.AssetType
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * Pole or panel, its plate and a note, given to every selected file at once:
 * a walk round one panel is often ten pictures of the same plate.
 * [initial] is what the first selected file already carries, so a correction
 * starts from what is there instead of from nothing.
 */
@Composable
fun AssignPanel(
    count: Int,
    initialType: Int?,
    initialPlate: String,
    initialNote: String,
    busy: Boolean,
    onAssign: (type: Int, plate: String, note: String) -> Unit
) {
    var type by remember(initialType, initialPlate) { mutableStateOf(initialType) }
    var plate by remember(initialType, initialPlate) { mutableStateOf(initialPlate) }
    var note by remember(initialType, initialPlate) { mutableStateOf(initialNote) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Text(
            stringResource(R.string.thermal_assign_title, PersianNumbers.toPersian(count)),
            style = MaterialTheme.typography.titleSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
            ToneChip(stringResource(R.string.thermal_asset_pole), type == AssetType.POLE, Tone.INFO, { type = AssetType.POLE },
                modifier = Modifier.weight(1f))
            ToneChip(stringResource(R.string.thermal_asset_panel), type == AssetType.PANEL, Tone.INFO, { type = AssetType.PANEL },
                modifier = Modifier.weight(1f))
        }
        AppTextField(stringResource(R.string.thermal_plate), plate, { plate = it }, ltr = true)
        AppTextField(stringResource(R.string.thermal_file_note), note, { note = it }, singleLine = false, minLines = 2)
        PrimaryButton(
            text = stringResource(R.string.thermal_assign),
            onClick = { type?.let { onAssign(it, plate, note) } },
            icon = Icons.Filled.Check,
            enabled = type != null && plate.isNotBlank(),
            busy = busy,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
        )
    }
}
