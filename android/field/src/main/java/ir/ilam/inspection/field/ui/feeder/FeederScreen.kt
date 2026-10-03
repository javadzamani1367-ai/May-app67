package ir.ilam.inspection.field.ui.feeder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.ui.form.FormActions
import ir.ilam.inspection.field.ui.form.MediaSection
import ir.ilam.inspection.field.ui.form.MissingCard
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.location.PositionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

/**
 * Feeder amperage at a transformer panel, step by step in the order the
 * readings are taken. The panel diagram stays on top the whole way, with the
 * box being filled in highlighted, so the user never reads B for A.
 */
@Composable
fun FeederScreen(itemId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.field
    val viewModel: FeederViewModel = viewModel(key = "feeder-${itemId ?: "new"}") { FeederViewModel(container, itemId) }
    val item = viewModel.item
    val payload = viewModel.payload
    val step = viewModel.step

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.field_feeder), subtitle = stringResource(R.string.form_autosaved), onBack = onClose)
        if (item == null) return@Column
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
        ) {
            PanelDiagram(step, payload.feeders.map { it.state }) { viewModel.step = it }
            when (step) {
                FeederStep.PANEL -> {
                    SectionCard(title = stringResource(R.string.feeder_panel_title), icon = Icons.Filled.Badge, tone = Tone.BRAND) {
                        Column {
                            AppTextField(stringResource(R.string.feeder_plate), item.plate.orEmpty(), viewModel::setPlate, ltr = true,
                                error = if (item.plate.isNullOrBlank()) stringResource(R.string.feeder_plate_required) else null)
                            AppTextField(stringResource(R.string.report_address), item.address.orEmpty(), viewModel::setAddress,
                                singleLine = false, minLines = 2)
                        }
                    }
                    PositionCard(
                        latitude = item.latitude,
                        longitude = item.longitude,
                        accuracy = item.accuracy,
                        onMeasured = { fix, _ -> viewModel.setMeasured(fix) },
                        onPicked = { lat, lon -> viewModel.setPosition(lat, lon, null) },
                        onTyped = { lat, lon -> viewModel.setPosition(lat, lon, null) }
                    )
                    MediaSection(viewModel, stampCode = item.plate.orEmpty(), allowVideo = false, allowAudio = false)
                }
                FeederStep.MAIN -> MainSwitchStep(payload.main, viewModel::setMain)
                FeederStep.A, FeederStep.B, FeederStep.C, FeederStep.D -> {
                    val index = step.ordinal - FeederStep.A.ordinal
                    FeederStepCard(FEEDER_TITLES[index], FEEDER_HINTS[index], payload.feeders[index]) {
                        viewModel.setFeeder(index, it)
                    }
                }
                FeederStep.CHECK -> {
                    CheckStep(viewModel.verdict(), viewModel.tolerance)
                    AppTextField(stringResource(R.string.feeder_note), payload.note, viewModel::setNote, singleLine = false, minLines = 2)
                    MissingCard(viewModel.missing().map { missingText(it) })
                    FormActions(
                        canFinish = viewModel.missing().isEmpty(),
                        busy = viewModel.busy,
                        onFinish = { viewModel.finish(context, onClose) },
                        onDiscard = { viewModel.discard(onClose) }
                    )
                }
            }
            if (step != FeederStep.CHECK) {
                StepButtons(
                    canGoBack = step != FeederStep.PANEL,
                    canGoOn = viewModel.stepComplete(step),
                    onBack = { viewModel.step = FeederStep.entries[step.ordinal - 1] },
                    onNext = { viewModel.step = FeederStep.entries[step.ordinal + 1] }
                )
            }
        }
    }
}

@Composable
private fun StepButtons(canGoBack: Boolean, canGoOn: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        SecondaryButton(
            text = stringResource(R.string.form_previous),
            onClick = onBack,
            enabled = canGoBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            modifier = Modifier.weight(1f)
        )
        PrimaryButton(
            text = stringResource(R.string.form_next),
            onClick = onNext,
            enabled = canGoOn,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun missingText(key: String): String {
    val feeder = key.substringAfter(':', "")
    return when {
        key == FeederViewModel.KEY_POSITION -> stringResource(R.string.missing_position)
        key == FeederViewModel.KEY_PLATE -> stringResource(R.string.feeder_plate_required)
        key == FeederViewModel.KEY_MISMATCH -> stringResource(R.string.feeder_check_mismatch_block)
        key == "main" -> stringResource(R.string.feeder_missing_main)
        key == "main_range" -> stringResource(R.string.feeder_missing_main_range)
        key.startsWith("state:") -> stringResource(R.string.feeder_missing_state, feeder)
        key.startsWith("values:") -> stringResource(R.string.feeder_missing_values, feeder)
        key.startsWith("range:") -> stringResource(R.string.feeder_missing_range, feeder)
        key.startsWith("reason:") -> stringResource(R.string.feeder_missing_reason, feeder)
        else -> key
    }
}

private val FEEDER_TITLES = listOf(R.string.feeder_a, R.string.feeder_b, R.string.feeder_c, R.string.feeder_d)
private val FEEDER_HINTS = listOf(R.string.feeder_a_hint, R.string.feeder_b_hint, R.string.feeder_c_hint, R.string.feeder_d_hint)
