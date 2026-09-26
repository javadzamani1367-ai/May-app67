package ir.ilam.inspection.ui.visit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.ui.common.AutoSave
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Tone

/**
 * Inspection and discovery — the supply and the meter. The whole step is one
 * value, so it is held as one and saved as one; the two cards below only ever
 * hand back a changed copy of it.
 */
@Composable
fun TechnicalStep(detail: ReportDetail, viewModel: VisitViewModel) {
    val report = detail.report
    var input by remember(report.id) { mutableStateOf(TechnicalInput.from(report)) }

    AutoSave(input) { viewModel.setTechnical(it) }

    SectionCard(
        title = stringResource(R.string.section_supply),
        subtitle = stringResource(R.string.section_supply_hint),
        icon = Icons.Filled.ElectricalServices,
        tone = Tone.ACCENT
    ) {
        PhaseMeasurementCard(input = input, onChange = { input = it })
    }

    SectionCard(
        title = stringResource(R.string.field_meter_health),
        subtitle = stringResource(R.string.section_meter_hint),
        icon = Icons.Filled.ElectricMeter
    ) {
        MeterHealthCard(input = input, onChange = { input = it })
    }
}
