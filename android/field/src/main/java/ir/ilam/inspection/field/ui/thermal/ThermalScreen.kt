package ir.ilam.inspection.field.ui.thermal

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.thermal.Temperatures
import ir.ilam.inspection.field.thermal.TrackRecorder
import ir.ilam.inspection.field.ui.form.AddressField
import ir.ilam.inspection.field.ui.form.FormHub
import ir.ilam.inspection.field.ui.form.FormPage
import ir.ilam.inspection.field.ui.form.rememberFormPage
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A thermal inspection with the HIKMICRO camera, as a page of tiles in the
 * order the work is done: start the route, take the pictures with HIKMICRO
 * Viewer, stop the route, bring the files in, then say for each what it shows.
 */
@Composable
fun ThermalScreen(itemId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.field
    val viewModel: ThermalViewModel = viewModel(key = "thermal-${itemId ?: "new"}") { ThermalViewModel(container, itemId, createSavedStateHandle()) }
    val track by TrackRecorder.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pageState = rememberFormPage()
    // The app may be closed in the background (a call, another app): write the last edit now.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }
    val hubScroll = rememberScrollState()
    val item = viewModel.item
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editing by remember { mutableStateOf<FieldFileEntity?>(null) }
    val title = stringResource(R.string.field_thermal)
    val back = { pageState.value = null }

    if (item == null) {
        Column(Modifier.fillMaxSize()) { TavanTopBar(title = title, onBack = onClose) }
        return
    }
    val payload = viewModel.payload
    val recording = track.running && track.itemId == item.id
    val otherRecording = track.running && track.itemId != item.id
    val originals = viewModel.originals

    when (pageState.value) {
        null -> FormHub(
            title = title,
            sections = thermalSections(viewModel, recording, track.points),
            missing = viewModel.missing(recording).map { missingText(it) },
            busy = viewModel.busy,
            onOpen = { pageState.value = it },
            onFinish = { viewModel.finish(context, onClose) },
            onDiscard = {
                if (recording) TrackRecorder.stop(context)
                viewModel.discard(onClose)
            },
            onClose = onClose,
            editor = viewModel,
            scroll = hubScroll,
            header = { if (otherRecording) Warning(stringResource(R.string.thermal_other_recording)) }
        )
        PAGE_ROUTE -> FormPage(title = stringResource(R.string.thermal_route_title), onBack = back) {
            if (otherRecording) Warning(stringResource(R.string.thermal_other_recording))
            RouteCard(
                recording = recording,
                track = track,
                startedAt = payload.startedAt,
                endedAt = payload.endedAt,
                points = payload.points,
                skewSeconds = payload.clockSkewSeconds,
                onStart = { if (!otherRecording) viewModel.startRoute(context) },
                onStop = { viewModel.stopRoute(context) }
            )
        }
        PAGE_FIND -> FormPage(title = stringResource(R.string.thermal_files_title), onBack = back) {
            if (payload.startedAt != null) HikmicroCard(viewModel)
            else Warning(stringResource(R.string.thermal_hub_find_locked))
        }
        PAGE_FILES -> FormPage(title = stringResource(R.string.thermal_list_title, PersianNumbers.toPersian(originals.size)), onBack = back) {
            SectionCard(
                title = stringResource(R.string.thermal_list_title, PersianNumbers.toPersian(originals.size)),
                subtitle = stringResource(R.string.thermal_list_hint),
                icon = Icons.Filled.Thermostat,
                tone = Tone.DANGER
            ) {
                Column {
                    originals.forEach { file ->
                        val info = payload.files[file.id]
                        ThermalFileRow(
                            file = file,
                            name = info?.name.orEmpty(),
                            temperatures = info?.temperatures ?: Temperatures(),
                            selected = file.id in selected,
                            onToggle = { selected = if (file.id in selected) selected - file.id else selected + file.id },
                            onTemperatures = { editing = file },
                            onRemove = {
                                selected = selected - file.id
                                viewModel.removeThermal(file)
                            }
                        )
                    }
                    val chosen = originals.filter { it.id in selected }
                    if (chosen.isNotEmpty()) {
                        val first = chosen.first()
                        AssignPanel(
                            count = chosen.size,
                            initialType = first.assetType,
                            initialPlate = first.plate.orEmpty(),
                            initialNote = first.note.orEmpty(),
                            busy = viewModel.busy
                        ) { type, plate, note ->
                            viewModel.assign(selected, type, plate, note)
                            selected = emptySet()
                        }
                    } else {
                        Text(stringResource(R.string.thermal_select_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                    }
                }
            }
        }
        PAGE_ADDRESS -> FormPage(title = stringResource(R.string.report_place), onBack = back) {
            // No position of its own until it is finished: the first located file, or where the route is now.
            val anchor = originals.firstOrNull { it.latitude != null && !it.locationUncertain }
            AddressField(item.address.orEmpty(), anchor?.latitude ?: track.latitude.takeIf { recording },
                anchor?.longitude ?: track.longitude.takeIf { recording }, viewModel::setAddress)
        }
        PAGE_EXPORT -> FormPage(title = stringResource(R.string.thermal_export), onBack = back) {
            Text(stringResource(R.string.thermal_export_hint), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Spacing.md))
            PrimaryButton(
                text = stringResource(R.string.thermal_export),
                onClick = {
                    scope.launch {
                        val points = withContext(Dispatchers.IO) { viewModel.trackPoints() }
                        ThermalShare.share(context, item, payload, points, originals)
                    }
                },
                enabled = originals.isNotEmpty(),
                icon = Icons.Filled.Share,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    editing?.let { file ->
        TemperatureDialog(
            file = file,
            initial = viewModel.payload.files[file.id]?.temperatures ?: Temperatures(),
            onDismiss = { editing = null },
            onSave = {
                viewModel.setTemperatures(file.id, it)
                editing = null
            }
        )
    }
}

@Composable
private fun missingText(key: String): String = when {
    key == ThermalViewModel.KEY_RECORDING -> stringResource(R.string.thermal_missing_recording)
    key == ThermalViewModel.KEY_NO_FILES -> stringResource(R.string.thermal_missing_files)
    key.startsWith(ThermalViewModel.KEY_UNASSIGNED) ->
        stringResource(R.string.thermal_missing_unassigned, PersianNumbers.toPersian(key.substringAfter(':')))
    else -> key
}
