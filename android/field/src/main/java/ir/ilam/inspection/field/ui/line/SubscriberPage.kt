package ir.ilam.inspection.field.ui.line

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.PoleReading
import ir.ilam.inspection.field.data.Subscriber
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.ui.feeder.PhaseInputs
import ir.ilam.inspection.field.ui.form.EvidenceRow
import ir.ilam.inspection.field.ui.form.FormPage
import ir.ilam.inspection.field.ui.form.LockGate
import ir.ilam.inspection.field.ui.form.ImportNote
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * One subscriber whose current was abnormal: the house on the map (it cannot
 * be sent without), the reading, who it is, its postal codes, and the photo
 * and video of the clamp meter and a photo of the building.
 */
@Composable
fun SubscriberPage(
    viewModel: LineViewModel,
    pole: PoleReading,
    subscriber: Subscriber,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onDeleted: () -> Unit
) {
    val prefs = LocalContext.current.field.prefs
    val number = pole.subscribers.indexOfFirst { it.id == subscriber.id } + 1
    var deleting by remember { mutableStateOf(false) }
    fun edit(transform: (Subscriber) -> Subscriber) = viewModel.updateSubscriber(pole.id, subscriber.id, transform)

    FormPage(
        title = stringResource(R.string.sub_page_title, fa(number)),
        subtitle = stringResource(R.string.line_pole_title, fa(viewModel.number(pole.id))),
        onBack = onBack
    ) {
        SectionCard(
            title = stringResource(R.string.sub_position),
            icon = Icons.Filled.Home,
            tone = if (subscriber.hasPosition) Tone.INFO else Tone.DANGER
        ) {
            Column {
                Text(
                    subscriber.positionAt?.let { stringResource(R.string.plate_map_recorded, PersianDate.formatWithTime(it)) }
                        ?: stringResource(if (subscriber.hasPosition) R.string.plate_map_recorded_plain else R.string.sub_position_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (subscriber.hasPosition) Tavan.colors.success.strong else Tavan.colors.danger.strong
                )
                PrimaryButton(
                    text = stringResource(if (subscriber.hasPosition) R.string.place_change_on_map else R.string.place_record_on_map),
                    onClick = onOpenMap,
                    icon = Icons.Filled.Map,
                    tone = if (subscriber.hasPosition) Tone.BRAND else Tone.SUCCESS,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                )
            }
        }
        LockGate(!subscriber.hasPosition, stringResource(R.string.gate_position), onTap = onOpenMap) {
            SectionCard(title = stringResource(R.string.sub_amps), icon = Icons.Filled.ElectricalServices, tone = Tone.ACCENT) {
                Column {
                    ChoiceRow(
                        label = stringResource(R.string.sub_phase),
                        options = listOf(false, true),
                        selected = subscriber.threePhase,
                        optionLabel = { stringResource(if (it) R.string.sub_phase_three else R.string.sub_phase_single) },
                        onSelect = { three -> edit { it.copy(threePhase = three) } }
                    )
                    if (subscriber.threePhase) {
                        PhaseInputs(subscriber.amps) { amps -> edit { it.copy(amps = amps) } }
                    } else {
                        NumberField(stringResource(R.string.sub_amp_single), subscriber.amps.r,
                            { v -> edit { it.copy(amps = it.amps.copy(r = v)) } }, decimal = true)
                    }
                    if (subscriber.ampsComplete) {
                        val limit = if (subscriber.threePhase) prefs.abnormalThreeA else prefs.abnormalSingleA
                        val abnormal = subscriber.abnormal(prefs.abnormalSingleA, prefs.abnormalThreeA)
                        Text(
                            stringResource(if (abnormal) R.string.sub_above_limit else R.string.sub_below_limit, fa(limit)),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (abnormal) Tavan.colors.warning.strong else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm)
                        )
                    }
                }
            }
            SectionCard(title = stringResource(R.string.sub_identity), icon = Icons.Filled.Badge, tone = Tone.BRAND) {
                Column {
                    AppTextField(stringResource(R.string.sub_name), subscriber.name, { v -> edit { it.copy(name = v) } })
                    AppTextField(stringResource(R.string.sub_file_number), subscriber.fileNumber, { v -> edit { it.copy(fileNumber = v) } }, ltr = true)
                    AppTextField(stringResource(R.string.sub_subscription), subscriber.subscription, { v -> edit { it.copy(subscription = v) } }, ltr = true)
                }
            }
            PostalCodes(subscriber.postalCodes) { codes -> edit { it.copy(postalCodes = codes) } }
            SubscriberEvidence(viewModel, pole, subscriber) { t -> viewModel.updateSubscriber(pole.id, subscriber.id, t) }
        }
        SecondaryButton(
            text = stringResource(R.string.sub_delete),
            onClick = { deleting = true },
            icon = Icons.Filled.DeleteOutline,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md)
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.sub_delete_title, fa(number))) },
            text = { Text(stringResource(R.string.sub_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.removeSubscriber(pole.id, subscriber.id)
                    onDeleted()
                }) { Text(stringResource(R.string.form_discard_yes)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

/** The three kinds of evidence, from the camera or the gallery (only from the last day). */
@Composable
private fun SubscriberEvidence(
    viewModel: LineViewModel,
    pole: PoleReading,
    subscriber: Subscriber,
    edit: ((Subscriber) -> Subscriber) -> Unit
) {
    val stamp = viewModel.item?.plate.orEmpty()
    // The stamp carries the house's position once it is marked, the pole's before that.
    val place = when {
        subscriber.latitude != null && subscriber.longitude != null ->
            Fix(subscriber.latitude, subscriber.longitude, subscriber.accuracy ?: 0.0)
        pole.latitude != null && pole.longitude != null -> Fix(pole.latitude, pole.longitude, pole.accuracy ?: 0.0)
        else -> null
    }
    SectionCard(
        title = stringResource(R.string.sub_evidence),
        subtitle = stringResource(R.string.sub_evidence_hint),
        icon = Icons.Filled.PhotoLibrary,
        tone = Tone.INFO
    ) {
        Column {
            EvidenceRow(viewModel, stringResource(R.string.sub_amp_photo), subscriber.ampPhotos, video = false, stamp, place,
                onAdded = { t -> edit { it.copy(ampPhotos = it.ampPhotos + t) } },
                onRemoved = { t -> edit { it.copy(ampPhotos = it.ampPhotos - t) } })
            EvidenceRow(viewModel, stringResource(R.string.sub_amp_video), subscriber.ampVideos, video = true, stamp, place,
                onAdded = { t -> edit { it.copy(ampVideos = it.ampVideos + t) } },
                onRemoved = { t -> edit { it.copy(ampVideos = it.ampVideos - t) } })
            EvidenceRow(viewModel, stringResource(R.string.sub_building_photo), subscriber.buildingPhotos, video = false, stamp, place,
                onAdded = { t -> edit { it.copy(buildingPhotos = it.buildingPhotos + t) } },
                onRemoved = { t -> edit { it.copy(buildingPhotos = it.buildingPhotos - t) } })
            ImportNote(viewModel)
        }
    }
}

private fun fa(value: Int) = PersianNumbers.toPersian(value)
