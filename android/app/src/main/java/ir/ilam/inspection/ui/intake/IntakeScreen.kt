package ir.ilam.inspection.ui.intake

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.County
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.JalaliDatePickerDialog
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.util.PersianDate

/** Quick intake: the short form that puts a case into the pending queue. */
@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text(stringResource(R.string.intake_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            DropdownField(
                label = stringResource(R.string.intake_report_type),
                options = ReportType.entries.toList(),
                selected = state.type,
                optionLabel = { reportTypeLabel(it) },
                onSelect = viewModel::setType,
                error = state.typeError?.let { stringResource(it) }
            )
            if (state.needsManualCode) {
                AppTextField(
                    label = stringResource(R.string.intake_manual_tracking),
                    value = state.manualTrackingCode,
                    onValueChange = viewModel::setManualCode,
                    error = state.trackingError?.let { stringResource(it) }
                )
            }
            DropdownField(
                label = stringResource(R.string.intake_county),
                options = state.counties,
                selected = state.county,
                optionLabel = County::name,
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
            NumberField(
                label = stringResource(R.string.intake_subscription),
                value = state.subscription,
                onValueChange = viewModel::setSubscription
            )
            OutlinedButton(
                onClick = { pickingDate = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.intake_report_date) + ": " +
                        PersianDate.format(state.reportDate)
                )
            }
            Button(
                onClick = viewModel::submit,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                Text(stringResource(R.string.action_save))
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
