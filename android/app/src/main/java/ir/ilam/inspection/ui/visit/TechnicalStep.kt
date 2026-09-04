package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.AutoSave
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianNumbers

/** Step 3 — meter and connection. Meter amperage is required to close a case. */
@Composable
fun TechnicalStep(detail: ReportDetail, viewModel: VisitViewModel) {
    val report = detail.report
    var meter by remember(report.id) { mutableStateOf(PersianNumbers.plain(report.meterAmperage)) }
    var measured by remember(report.id) { mutableStateOf(PersianNumbers.plain(report.measuredAmperage)) }
    var connectionType by remember(report.id) { mutableStateOf(report.connectionType.orEmpty()) }
    var sealStatus by remember(report.id) { mutableStateOf(report.sealStatus.orEmpty()) }

    AutoSave(listOf(meter, measured, connectionType, sealStatus)) {
        viewModel.setTechnical(
            meterAmperage = meter,
            measuredAmperage = measured,
            connectionType = connectionType,
            sealStatus = sealStatus
        )
    }

    SectionCard(title = stringResource(R.string.visit_step_technical)) {
        Column {
            NumberField(
                label = stringResource(R.string.field_meter_amperage),
                value = meter,
                onValueChange = { meter = it },
                decimal = true
            )
            NumberField(
                label = stringResource(R.string.field_measured_amperage),
                value = measured,
                onValueChange = { measured = it },
                decimal = true
            )
            AppTextField(
                stringResource(R.string.field_connection_type),
                connectionType,
                { connectionType = it }
            )
            AppTextField(stringResource(R.string.field_seal_status), sealStatus, { sealStatus = it })
        }
    }
}
