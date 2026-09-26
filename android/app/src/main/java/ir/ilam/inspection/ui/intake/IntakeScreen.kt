package ir.ilam.inspection.ui.intake

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.DateRow
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.JalaliDatePickerDialog
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Quick intake: the short form that puts a case into the pending queue. Three
 * blocks — where the report came from, where the place is, and the rest —
 * and one button that files it and goes straight into the visit.
 */
@Composable
fun IntakeScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val container = LocalContext.current.container
    val viewModel: IntakeViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(container) { IntakeViewModel(it) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdId) {
        state.createdId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TavanTopBar(
                title = stringResource(R.string.intake_title),
                subtitle = stringResource(R.string.intake_subtitle),
                onBack = onBack
            )
        },
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = stringResource(R.string.intake_submit),
                    onClick = viewModel::submit,
                    busy = state.saving,
                    icon = Icons.Filled.TaskAlt,
                    tone = Tone.SUCCESS,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            SectionCard(title = stringResource(R.string.intake_section_type), icon = Icons.Filled.Category) {
                ReportTypePicker(
                    selected = state.type,
                    onSelect = viewModel::setType,
                    error = state.typeError?.let { stringResource(it) }
                )
                if (state.needsManualCode) {
                    AppTextField(
                        label = stringResource(R.string.intake_manual_tracking),
                        value = state.manualTrackingCode,
                        onValueChange = viewModel::setManualCode,
                        error = state.trackingError?.let { stringResource(it) },
                        ltr = true
                    )
                }
            }

            SectionCard(title = stringResource(R.string.intake_section_place), icon = Icons.Filled.Place, tone = Tone.INFO) {
                DropdownField(
                    label = stringResource(R.string.intake_county),
                    options = state.counties,
                    selected = state.county,
                    // Two areas of Ilam city share a name and differ only by
                    // code, so the code is part of what the picker shows.
                    optionLabel = {
                        stringResource(R.string.county_with_code, it.name, PersianNumbers.toPersian(it.code))
                    },
                    onSelect = viewModel::setCounty,
                    error = state.countyError?.let { stringResource(it) }
                )
                AppTextField(
                    label = stringResource(R.string.intake_district),
                    value = state.district,
                    onValueChange = viewModel::setDistrict
                )
                AppTextField(
                    label = stringResource(R.string.intake_address),
                    value = state.address,
                    onValueChange = viewModel::setAddress,
                    singleLine = false,
                    minLines = 2,
                    error = state.addressError?.let { stringResource(it) }
                )
            }

            SectionCard(title = stringResource(R.string.intake_section_more), icon = Icons.AutoMirrored.Filled.PlaylistAdd, tone = Tone.NEUTRAL) {
                NumberField(
                    label = stringResource(R.string.intake_subscription),
                    value = state.subscription,
                    onValueChange = viewModel::setSubscription
                )
                DateRow(
                    label = stringResource(R.string.intake_report_date),
                    value = PersianDate.format(state.reportDate),
                    onClick = { pickingDate = true }
                )
            }
        }
    }

    if (pickingDate) {
        JalaliDatePickerDialog(
            initialMillis = state.reportDate,
            onDismiss = { pickingDate = false },
            onPicked = {
                viewModel.setReportDate(it)
                pickingDate = false
            }
        )
    }
}
