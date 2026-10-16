package ir.ilam.inspection.field.ui.line

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.PoleReading
import ir.ilam.inspection.field.data.PoleState
import ir.ilam.inspection.field.data.SegmentKind
import ir.ilam.inspection.field.ui.feeder.PhaseInputs
import ir.ilam.inspection.field.ui.form.CountRow
import ir.ilam.inspection.field.ui.form.FormPage
import ir.ilam.inspection.field.ui.form.LockGate
import ir.ilam.inspection.field.ui.form.PlateThenMap
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import kotlin.math.abs

/**
 * One pole, on its own page, in the order the work is done standing at it:
 * the plate and straight after it the position on the map (the walk does
 * not go on without it), the clamp-meter readings with the change from the
 * pole before, the branches, and the subscribers found abnormal on them.
 */
@Composable
fun PolePage(
    viewModel: LineViewModel,
    pole: PoleReading,
    onList: () -> Unit,
    onOpenPole: (String) -> Unit,
    onPickOnMap: () -> Unit,
    onOpenSubscriber: (String) -> Unit,
    onNewSubscriber: (String) -> Unit
) {
    val number = viewModel.number(pole.id)
    val poles = viewModel.payload.poles
    val next = poles.getOrNull(number)
    var deleting by remember { mutableStateOf(false) }
    fun edit(transform: (PoleReading) -> PoleReading) = viewModel.updatePole(pole.id, transform)
    // The pole's position is required: without it, leaving opens the map instead.
    // (A pole put in by mistake is deleted with the button at the bottom.)
    val back = { if (pole.hasPosition) onList() else onPickOnMap() }

    FormPage(
        title = stringResource(R.string.line_pole_title, PersianNumbers.toPersian(number)),
        onBack = back,
        bottom = {
            SecondaryButton(
                text = stringResource(R.string.line_all_poles),
                onClick = back,
                icon = Icons.AutoMirrored.Filled.List,
                modifier = Modifier.weight(0.8f)
            )
            PrimaryButton(
                text = if (next != null) stringResource(R.string.line_next_pole)
                else stringResource(R.string.line_add_pole, PersianNumbers.toPersian(number + 1)),
                // The next pole waits for this one's position: the button opens the map until then.
                onClick = { if (pole.hasPosition) onOpenPole(next?.id ?: viewModel.addPole()) else onPickOnMap() },
                icon = if (next != null) Icons.AutoMirrored.Filled.ArrowForward else Icons.Filled.Add,
                modifier = Modifier.weight(1.2f)
            )
        }
    ) {
        PlateThenMap(
            title = stringResource(R.string.line_pole_position),
            plateLabel = stringResource(R.string.line_pole_plate),
            plate = pole.plate,
            onPlate = { v -> edit { it.copy(plate = v) } },
            hasPosition = pole.hasPosition,
            positionAt = pole.positionAt,
            onOpenMap = onPickOnMap,
            needsPlate = false
        )
        LockGate(!pole.hasPosition, stringResource(R.string.gate_position), onTap = onPickOnMap) {
            SectionCard(title = stringResource(R.string.line_pole_state), icon = Icons.Filled.ElectricalServices, tone = Tone.ACCENT) {
                Column {
                    ChoiceRow(
                        label = stringResource(R.string.line_pole_state),
                        options = PoleState.entries.toList(),
                        selected = pole.state,
                        optionLabel = {
                            stringResource(if (it == PoleState.MEASURED) R.string.line_state_measured else R.string.line_state_not_measured)
                        },
                        onSelect = { state -> edit { it.copy(state = state) } },
                        toneOf = { if (it == PoleState.NOT_MEASURED) Tone.WARNING else Tone.ACCENT }
                    )
                    if (pole.state == PoleState.MEASURED) {
                        PhaseInputs(pole.phases) { phases -> edit { it.copy(phases = phases) } }
                        NumberField(stringResource(R.string.line_neutral), pole.neutral, { v -> edit { it.copy(neutral = v) } }, decimal = true)
                        ChangeLine(viewModel, number)
                    } else {
                        AppTextField(stringResource(R.string.line_reason), pole.reason, { v -> edit { it.copy(reason = v) } })
                    }
                }
            }
            SectionCard(title = stringResource(R.string.line_branches), icon = Icons.AutoMirrored.Filled.CallSplit, tone = Tone.BRAND) {
                Column {
                    CountRow(stringResource(R.string.line_branches_single), pole.branches1) { count -> edit { it.copy(branches1 = count) } }
                    CountRow(stringResource(R.string.line_branches_three), pole.branches3) { count -> edit { it.copy(branches3 = count) } }
                    AppTextField(stringResource(R.string.line_pole_note), pole.note, { v -> edit { it.copy(note = v) } }, singleLine = false)
                    PoleCamera(viewModel, pole, stampCode = viewModel.item?.plate.orEmpty())
                }
            }
            PoleSubscribers(viewModel, pole, onOpen = onOpenSubscriber, onAdded = onNewSubscriber)
        }
        SecondaryButton(
            text = stringResource(R.string.line_delete_pole),
            onClick = { deleting = true },
            icon = Icons.Filled.DeleteOutline,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md)
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.line_delete_title, PersianNumbers.toPersian(number))) },
            text = { Text(stringResource(R.string.line_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.removePole(pole.id)
                    onList()
                }) { Text(stringResource(R.string.form_discard_yes)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

/** What the current did since the last measured point — read the moment the third phase is typed. */
@Composable
private fun ChangeLine(viewModel: LineViewModel, number: Int) {
    val verdict = viewModel.verdict()
    val segment = verdict.segments.firstOrNull { it.to.pole == number } ?: return
    val kind = verdict.kindOf(segment)
    val sign = if (segment.drop >= 0) stringResource(R.string.line_drop) else stringResource(R.string.line_rise)
    Text(
        stringResource(R.string.line_change_from, pointName(segment.from), "$sign ${amps(abs(segment.drop))}"),
        style = MaterialTheme.typography.titleSmall,
        color = when (kind) {
            SegmentKind.HEAVY -> Tavan.colors.danger.strong
            SegmentKind.RISE -> Tavan.colors.warning.strong
            SegmentKind.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = Spacing.sm)
    )
}
