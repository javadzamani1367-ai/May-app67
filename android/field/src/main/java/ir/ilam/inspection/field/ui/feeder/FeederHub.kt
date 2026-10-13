package ir.ilam.inspection.field.ui.feeder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FeederState
import ir.ilam.inspection.field.data.PhaseReading
import ir.ilam.inspection.field.ui.form.HubSection
import ir.ilam.inspection.field.ui.form.SectionStatus
import ir.ilam.inspection.field.ui.form.mediaSection
import ir.ilam.inspection.field.ui.form.positionSection
import ir.ilam.inspection.field.ui.form.preview
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/** The panel reading's tiles, in the order the readings are taken. */
@Composable
fun feederSections(viewModel: FeederViewModel): List<HubSection> {
    val item = viewModel.item ?: return emptyList()
    val payload = viewModel.payload
    val tiles = mutableListOf(
        HubSection(
            key = PAGE_PANEL,
            title = stringResource(R.string.feeder_panel_title),
            summary = preview(listOfNotNull(item.plate?.takeIf { it.isNotBlank() }, item.address?.takeIf { it.isNotBlank() })
                .joinToString(" · ")) ?: stringResource(R.string.feeder_plate_required),
            icon = Icons.Filled.Badge,
            tone = Tone.BRAND,
            status = if (item.plate.isNullOrBlank()) SectionStatus.MISSING else SectionStatus.DONE
        ),
        positionSection(item, title = stringResource(R.string.feeder_position_title)),
        HubSection(
            key = keyOf(FeederStep.MAIN),
            title = stringResource(R.string.feeder_main_title),
            summary = if (payload.main.complete()) amps(payload.main) else stringResource(R.string.feeder_main_hint),
            icon = Icons.Filled.PowerSettingsNew,
            tone = Tone.ACCENT,
            status = if (viewModel.stepComplete(FeederStep.MAIN)) SectionStatus.DONE else SectionStatus.MISSING
        )
    )
    listOf(FeederStep.A, FeederStep.B, FeederStep.C, FeederStep.D).forEachIndexed { index, step ->
        val feeder = payload.feeders[index]
        tiles += HubSection(
            key = keyOf(step),
            title = stringResource(FEEDER_TITLES[index]),
            summary = when (feeder.state) {
                null -> stringResource(FEEDER_HINTS[index])
                FeederState.MEASURED -> if (feeder.phases.complete()) amps(feeder.phases)
                else stringResource(stateLabel(FeederState.MEASURED))
                FeederState.NOT_MEASURED -> stringResource(stateLabel(FeederState.NOT_MEASURED)) +
                    (preview(feeder.reason, 60)?.let { " — $it" } ?: "")
                FeederState.ABSENT -> stringResource(stateLabel(FeederState.ABSENT))
            },
            icon = Icons.Filled.ElectricalServices,
            tone = if (feeder.state == FeederState.NOT_MEASURED) Tone.WARNING else Tone.ACCENT,
            status = when {
                !viewModel.stepComplete(step) -> SectionStatus.MISSING
                feeder.state == FeederState.NOT_MEASURED -> SectionStatus.ATTENTION
                else -> SectionStatus.DONE
            }
        )
    }
    val verdict = viewModel.verdict()
    tiles += HubSection(
        key = PAGE_CHECK,
        title = stringResource(R.string.feeder_check_title),
        summary = when {
            verdict.phases.isEmpty() -> stringResource(R.string.feeder_check_pending)
            !verdict.possible -> stringResource(R.string.feeder_check_impossible)
            verdict.mismatched -> stringResource(R.string.feeder_check_mismatch_block)
            else -> stringResource(R.string.feeder_check_ok)
        },
        icon = Icons.Filled.Calculate,
        tone = if (verdict.mismatched) Tone.DANGER else Tone.SUCCESS,
        status = when {
            verdict.phases.isEmpty() -> SectionStatus.OPTIONAL
            verdict.mismatched -> SectionStatus.MISSING
            !verdict.possible -> SectionStatus.ATTENTION
            else -> SectionStatus.DONE
        }
    )
    tiles += mediaSection(viewModel.files)
    return tiles
}

@Composable
private fun amps(reading: PhaseReading): String = stringResource(
    R.string.feeder_amps,
    PersianNumbers.toPersian(reading.r), PersianNumbers.toPersian(reading.s), PersianNumbers.toPersian(reading.t)
)
