package ir.ilam.inspection.field.ui.feeder

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.ui.form.CrewSection
import ir.ilam.inspection.field.ui.form.MvFeederSection
import ir.ilam.inspection.field.ui.form.PlaceBlock
import ir.ilam.inspection.field.ui.form.PlateThenMap
import ir.ilam.inspection.field.ui.form.FormHub
import ir.ilam.inspection.field.ui.form.FormPage
import ir.ilam.inspection.field.ui.form.ItemPositionPage
import ir.ilam.inspection.field.ui.form.MediaPage
import ir.ilam.inspection.field.ui.form.MAP_PANEL
import ir.ilam.inspection.field.ui.form.PAGE_MEDIA
import ir.ilam.inspection.field.ui.form.PAGE_POSITION
import ir.ilam.inspection.field.ui.form.rememberFormPage
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.location.rememberFresh
import ir.ilam.inspection.ui.location.rememberLiveLocation
import ir.ilam.inspection.ui.theme.Tone

/**
 * Feeder amperage at a transformer panel. The form's page shows the panel as
 * the user stands in front of it, each box coloured by its state; a box, or
 * its tile, opens that reading on a page of its own, and "next" walks on in
 * the order the readings are taken, so B is never read for A.
 */
@Composable
fun FeederScreen(itemId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.field
    val viewModel: FeederViewModel = viewModel(key = "feeder-${itemId ?: "new"}") { FeederViewModel(container, itemId, createSavedStateHandle()) }
    val reading = rememberLiveLocation()
    val fresh = rememberFresh(reading)
    val pageState = rememberFormPage()
    // The app may be closed in the background (a call, another app): write the last edit now.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }
    val hubScroll = rememberScrollState()
    val item = viewModel.item
    val payload = viewModel.payload
    val title = stringResource(R.string.field_feeder)
    val back = { pageState.value = null }

    if (item == null) {
        Column(Modifier.fillMaxSize()) { TavanTopBar(title = title, onBack = onClose) }
        return
    }
    val page = pageState.value
    val step = page?.let(::stepOf)
    when {
        page == null -> FormHub(
            title = title,
            sections = feederSections(viewModel),
            missing = viewModel.missing().map { missingText(it) }.distinct(),
            busy = viewModel.busy,
            onOpen = { viewModel.mapOffered.clear(); pageState.value = it },
            onFinish = { viewModel.finish(context, onClose) },
            onDiscard = { viewModel.discard(onClose) },
            onClose = onClose,
            editor = viewModel,
            scroll = hubScroll,
            header = {
                PanelDiagram(null, payload.feeders.map { it.state }) { pageState.value = keyOf(it) }
            }
        )
        page == PAGE_POSITION -> {
            BackHandler { pageState.value = PAGE_PANEL }
            // The panel is read standing at it: the point must be where the user is.
            ItemPositionPage(viewModel, item, onBack = { pageState.value = PAGE_PANEL },
                title = stringResource(R.string.feeder_position_title),
                presenceLimit = container.prefs.presenceLimitM.toDouble())
        }
        page == PAGE_MEDIA -> MediaPage(viewModel, stampCode = item.plate.orEmpty(), onBack = back,
            allowVideo = false, allowAudio = false)
        page == PAGE_CREW -> FormPage(title = stringResource(R.string.crew_and_feeder_title), onBack = back) {
            CrewSection(payload.crew, viewModel.roster, viewModel::setCrew)
            MvFeederSection(payload.mvFeeder, viewModel.roster, viewModel::setMvFeeder)
        }
        page == PAGE_PANEL -> FormPage(title = stringResource(R.string.place_panel_title), onBack = {
            // Plate typed, no position yet: leaving brings the map up once, to record it standing there.
            val located = item.latitude != null && item.longitude != null
            if (!located && !item.plate.isNullOrBlank() && viewModel.mapOffered.add(MAP_PANEL)) pageState.value = PAGE_POSITION
            else back()
        }) {
            // Plate, then the map at once: the panel's position is recorded standing at it.
            PlateThenMap(
                title = stringResource(R.string.feeder_panel_title),
                plateLabel = stringResource(R.string.feeder_plate),
                plate = item.plate.orEmpty(),
                onPlate = viewModel::setPlate,
                hasPosition = item.latitude != null && item.longitude != null,
                positionAt = item.positionAt,
                onOpenMap = { pageState.value = PAGE_POSITION },
                plateError = if (item.plate.isNullOrBlank()) stringResource(R.string.feeder_plate_required) else null
            )
            PlaceBlock(viewModel, item, reading, fresh, onOpenMap = { pageState.value = PAGE_POSITION },
                onAddress = viewModel::setAddress, title = stringResource(R.string.feeder_position_title),
                showMapButton = false)
        }
        page == PAGE_CHECK -> FormPage(title = stringResource(R.string.feeder_check_title), onBack = back) {
            CheckStep(viewModel.verdict(), viewModel.tolerance)
            AppTextField(stringResource(R.string.feeder_note), payload.note, viewModel::setNote, singleLine = false, minLines = 2)
        }
        step != null -> {
            val next = FeederStep.entries.getOrNull(step.ordinal + 1)
            FormPage(
                title = stringResource(stepTitle(step)),
                onBack = back,
                bottom = {
                    SecondaryButton(
                        text = stringResource(R.string.hub_list),
                        onClick = back,
                        icon = Icons.AutoMirrored.Filled.List,
                        modifier = Modifier.weight(0.8f)
                    )
                    if (next != null) {
                        PrimaryButton(
                            text = stringResource(R.string.hub_next_named, stringResource(stepTitle(next))),
                            onClick = { pageState.value = keyOf(next) },
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            ) {
                PanelDiagram(step, payload.feeders.map { it.state }) { pageState.value = keyOf(it) }
                if (step == FeederStep.MAIN) {
                    MainSwitchStep(payload.main, viewModel::setMain)
                } else {
                    val index = step.ordinal - FeederStep.A.ordinal
                    FeederStepCard(FEEDER_TITLES[index], FEEDER_HINTS[index], payload.feeders[index]) {
                        viewModel.setFeeder(index, it)
                    }
                }
            }
        }
    }
}

@Composable
internal fun missingText(key: String): String {
    val feeder = key.substringAfter(':', "")
    return when {
        key == FeederViewModel.KEY_POSITION -> stringResource(R.string.missing_position)
        key == FeederViewModel.KEY_CREW -> stringResource(R.string.crew_missing)
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

internal const val PAGE_PANEL = "panel"
internal const val PAGE_CREW = "crew"
internal const val PAGE_CHECK = "check"

/** The page key of a step of the reading: "main", "A" … "D". */
internal fun keyOf(step: FeederStep): String = when (step) {
    FeederStep.PANEL -> PAGE_PANEL
    FeederStep.CHECK -> PAGE_CHECK
    FeederStep.MAIN -> "main"
    else -> step.name
}

private fun stepOf(key: String): FeederStep? = when (key) {
    "main" -> FeederStep.MAIN
    "A" -> FeederStep.A
    "B" -> FeederStep.B
    "C" -> FeederStep.C
    "D" -> FeederStep.D
    else -> null
}

internal fun stepTitle(step: FeederStep): Int = when (step) {
    FeederStep.PANEL -> R.string.feeder_panel_title
    FeederStep.MAIN -> R.string.feeder_main_title
    FeederStep.A -> R.string.feeder_a
    FeederStep.B -> R.string.feeder_b
    FeederStep.C -> R.string.feeder_c
    FeederStep.D -> R.string.feeder_d
    FeederStep.CHECK -> R.string.feeder_check_title
}

internal val FEEDER_TITLES = listOf(R.string.feeder_a, R.string.feeder_b, R.string.feeder_c, R.string.feeder_d)
internal val FEEDER_HINTS = listOf(R.string.feeder_a_hint, R.string.feeder_b_hint, R.string.feeder_c_hint, R.string.feeder_d_hint)
