package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.AutoSave
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianNumbers

/** Step 1 — confirm the address and capture the GIS position. */
@Composable
fun LocationStep(detail: ReportDetail, viewModel: VisitViewModel) {
    val report = detail.report
    val catalog = LocalContext.current.container.counties

    // The county is chosen, not typed. Its area code is official and goes into
    // the tracking code and the report, so a typed name that matched no county
    // would leave the area stale — pointing at a different area of Ilam than
    // the one the expert is standing in.
    var county by remember(report.id) {
        mutableStateOf(
            catalog.defaults.firstOrNull {
                it.name == report.county && it.code == report.areaCode
            } ?: catalog.defaults.firstOrNull { it.name == report.county }
        )
    }
    var district by remember(report.id) { mutableStateOf(report.district.orEmpty()) }
    var address by remember(report.id) { mutableStateOf(report.address.orEmpty()) }
    var postalCode by remember(report.id) { mutableStateOf(report.postalCode.orEmpty()) }
    var fileNumber by remember(report.id) { mutableStateOf(report.fileNumber.orEmpty()) }
    var billNumber by remember(report.id) { mutableStateOf(report.billNumber.orEmpty()) }
    var subscription by remember(report.id) { mutableStateOf(report.subscriptionNumber.orEmpty()) }
    var usageType by remember(report.id) { mutableStateOf(report.usageType.orEmpty()) }

    AutoSave(
        listOf(county?.name, county?.code, district, address, postalCode, fileNumber, billNumber, usageType)
    ) {
        viewModel.setLocationField(
            county = county?.name,
            areaCode = county?.code,
            district = district,
            address = address,
            postalCode = postalCode,
            fileNumber = fileNumber,
            billNumber = billNumber,
            usageType = usageType
        )
    }
    AutoSave(subscription) { viewModel.setSubscriptionNumber(subscription) }

    SectionCard(title = stringResource(R.string.visit_step_location)) {
        Column {
            DropdownField(
                label = stringResource(R.string.field_county),
                options = catalog.defaults,
                selected = county,
                optionLabel = {
                    stringResource(
                        R.string.county_with_code,
                        it.name,
                        PersianNumbers.toPersian(it.code)
                    )
                },
                onSelect = { county = it }
            )
            AppTextField(stringResource(R.string.field_district), district, { district = it })
            AppTextField(
                label = stringResource(R.string.field_address),
                value = address,
                onValueChange = { address = it },
                singleLine = false,
                minLines = 2
            )
            NumberField(stringResource(R.string.field_postal_code), postalCode, { postalCode = it })
            AppTextField(stringResource(R.string.field_file_number), fileNumber, { fileNumber = it })
            AppTextField(stringResource(R.string.field_bill_number), billNumber, { billNumber = it })
            NumberField(
                stringResource(R.string.field_subscription_number),
                subscription,
                { subscription = it }
            )
            AppTextField(stringResource(R.string.field_usage_type), usageType, { usageType = it })
        }
    }

    CoordinatesCard(report = report, viewModel = viewModel)
}
