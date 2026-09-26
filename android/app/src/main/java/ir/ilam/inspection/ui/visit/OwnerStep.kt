package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.Completion
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.AutoSave
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard

/** Step 2 — the owner or occupier. The national id is checksum validated. */
@Composable
fun OwnerStep(detail: ReportDetail, viewModel: VisitViewModel) {
    val report = detail.report
    var name by remember(report.id) { mutableStateOf(report.ownerName.orEmpty()) }
    var nationalId by remember(report.id) { mutableStateOf(report.ownerNationalId.orEmpty()) }
    var phone by remember(report.id) { mutableStateOf(report.ownerPhone.orEmpty()) }
    var relation by remember(report.id) { mutableStateOf(report.ownerRelation.orEmpty()) }

    AutoSave(listOf(name, nationalId, phone, relation)) {
        viewModel.setOwner(
            name = name,
            nationalId = nationalId,
            phone = phone,
            relation = relation
        )
    }

    val nationalIdError = if (nationalId.isNotBlank() && !Completion.isValidNationalId(nationalId)) {
        stringResource(R.string.error_national_id)
    } else {
        null
    }

    SectionCard(
        title = stringResource(R.string.visit_step_owner),
        subtitle = stringResource(R.string.section_owner_hint),
        icon = Icons.Filled.PersonPin
    ) {
        Column {
            AppTextField(
                label = stringResource(R.string.field_owner_name),
                value = name,
                onValueChange = { name = it },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) }
            )
            NumberField(
                label = stringResource(R.string.field_owner_national_id),
                value = nationalId,
                onValueChange = { nationalId = it.take(10) },
                error = nationalIdError
            )
            AppTextField(
                label = stringResource(R.string.field_owner_phone),
                value = phone,
                onValueChange = { phone = it },
                keyboardType = KeyboardType.Phone,
                ltr = true,
                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) }
            )
            AppTextField(stringResource(R.string.field_owner_relation), relation, { relation = it })
        }
    }
}
