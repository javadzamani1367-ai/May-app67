package ir.ilam.inspection.field.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

/** The second tap: which inspection, or which report. */
@Composable
fun ChoiceScreen(group: ChoiceGroup, onBack: () -> Unit, onPick: (FieldKind) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = stringResource(if (group == ChoiceGroup.INSPECT) R.string.field_home_inspect else R.string.field_home_report),
            onBack = onBack
        )
        Column(modifier = Modifier.padding(Spacing.screen)) {
            if (group == ChoiceGroup.INSPECT) {
                OptionCard(stringResource(R.string.field_thermal), stringResource(R.string.field_thermal_hint),
                    Icons.Filled.Thermostat, Tone.ACCENT, enabled = true, onClick = { onPick(FieldKind.THERMAL) })
                OptionCard(stringResource(R.string.field_feeder), stringResource(R.string.field_feeder_hint),
                    Icons.Filled.ElectricalServices, Tone.INFO, enabled = true, onClick = { onPick(FieldKind.FEEDER) })
            } else {
                OptionCard(stringResource(R.string.field_crypto), stringResource(R.string.field_crypto_hint),
                    Icons.Filled.Memory, Tone.DANGER, enabled = true, onClick = { onPick(FieldKind.CRYPTO) })
                OptionCard(stringResource(R.string.field_illegal), stringResource(R.string.field_illegal_hint),
                    Icons.Filled.Bolt, Tone.WARNING, enabled = true, onClick = { onPick(FieldKind.ILLEGAL) })
            }
        }
    }
}

/** Where each form will open; the forms arrive stage by stage (docs/FIELD-APP.md). */
@Composable
fun SoonScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.field_soon_title), onBack = onBack)
        EmptyState(message = stringResource(R.string.field_soon_message), icon = Icons.Outlined.Construction)
    }
}
