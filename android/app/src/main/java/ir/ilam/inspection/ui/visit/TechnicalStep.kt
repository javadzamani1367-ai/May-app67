package ir.ilam.inspection.ui.visit

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

/**
 * Step 3 — the supply and the meter. The whole step is one value, so it is
 * held as one and saved as one; the two cards below only ever hand back a
 * changed copy of it.
 */
@Composable
fun TechnicalStep(detail: ReportDetail, viewModel: VisitViewModel) {
    val report = detail.report
    var input by remember(report.id) { mutableStateOf(TechnicalInput.from(report)) }

    AutoSave(input) { viewModel.setTechnical(it) }

    SectionCard(title = stringResource(R.string.visit_step_technical)) {
        PhaseMeasurementCard(input = input, onChange = { input = it })
    }

    SectionCard(title = stringResource(R.string.field_meter_health)) {
        MeterHealthCard(input = input, onChange = { input = it })
    }
}
