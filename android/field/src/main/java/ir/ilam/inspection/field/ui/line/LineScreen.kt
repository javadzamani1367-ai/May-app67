package ir.ilam.inspection.field.ui.line

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.LinePayload
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.ui.feeder.PhaseInputs
import ir.ilam.inspection.field.ui.form.AddressField
import ir.ilam.inspection.field.ui.form.CrewSection
import ir.ilam.inspection.field.ui.form.ItemPositionPage
import ir.ilam.inspection.field.ui.form.MvFeederSection
import ir.ilam.inspection.field.ui.form.PlateThenMap
import ir.ilam.inspection.field.ui.form.FormHub
import ir.ilam.inspection.field.ui.form.FormPage
import ir.ilam.inspection.field.ui.form.MediaPage
import ir.ilam.inspection.field.ui.form.MAP_PANEL
import ir.ilam.inspection.field.ui.form.PAGE_MEDIA
import ir.ilam.inspection.field.ui.form.rememberFormPage
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.location.LivePositionPage
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * Pole-by-pole amperage of one feeder. The form's page has the feeder, its
 * reading at the panel, the poles, and the chart; each pole has a page of
 * its own, located from a position kept warm the whole walk.
 */
@Composable
fun LineScreen(itemId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.field
    val viewModel: LineViewModel = viewModel(key = "line-${itemId ?: "new"}") { LineViewModel(container, itemId, createSavedStateHandle()) }
    val pageState = rememberFormPage()
    // The app may be closed in the background (a call, another app): write the last edit now.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }
    val hubScroll = rememberScrollState()
    val item = viewModel.item
    val payload = viewModel.payload
    val title = stringResource(R.string.field_line)
    val back = { pageState.value = null }
    val toPoles = { pageState.value = PAGE_POLES }
    val openPole = { id: String -> viewModel.mapOffered.remove(id); pageState.value = POLE_PREFIX + id }
    // Back from a pole's map: the same visit of the pole page, so the map does not come up again.
    val returnToPole = { id: String -> pageState.value = POLE_PREFIX + id }

    if (item == null) {
        Column(Modifier.fillMaxSize()) { TavanTopBar(title = title, onBack = onClose) }
        return
    }
    val page = pageState.value
    when {
        page == null -> FormHub(
            title = title,
            sections = lineSections(viewModel),
            missing = viewModel.missing().map { lineMissingText(it) }.distinct(),
            busy = viewModel.busy,
            onOpen = { viewModel.mapOffered.clear(); pageState.value = it },
            onFinish = { viewModel.finish(context, onClose) },
            onDiscard = { viewModel.discard(onClose) },
            onClose = onClose,
            editor = viewModel,
            scroll = hubScroll,
            header = { LineHubHeader(viewModel) }
        )
        page == PAGE_CREW -> FormPage(title = stringResource(R.string.crew_and_feeder_title), onBack = back) {
            CrewSection(payload.crew, viewModel.roster, viewModel::setCrew)
            MvFeederSection(payload.mvFeeder, viewModel.roster, viewModel::setMvFeeder)
        }
        page == PAGE_PANEL_MAP -> {
            BackHandler { pageState.value = PAGE_FEEDER }
            // The panel is where the walk starts, and it is read standing at it.
            ItemPositionPage(viewModel, item, onBack = { pageState.value = PAGE_FEEDER },
                title = stringResource(R.string.line_panel_position), presenceLimit = container.prefs.presenceLimitM.toDouble())
        }
        page == PAGE_FEEDER -> FormPage(title = stringResource(R.string.line_feeder_title), onBack = {
            // Plate typed, no position yet: leaving brings the map up once, to record it standing there.
            val located = item.latitude != null && item.longitude != null
            if (!located && !item.plate.isNullOrBlank() && viewModel.mapOffered.add(MAP_PANEL)) pageState.value = PAGE_PANEL_MAP
            else back()
        }) {
            PlateThenMap(
                title = stringResource(R.string.line_panel_title),
                plateLabel = stringResource(R.string.line_panel_plate),
                plate = item.plate.orEmpty(),
                onPlate = viewModel::setPlate,
                hasPosition = item.latitude != null && item.longitude != null,
                positionAt = item.positionAt,
                onOpenMap = { pageState.value = PAGE_PANEL_MAP },
                plateError = if (item.plate.isNullOrBlank()) stringResource(R.string.missing_line_plate) else null
            )
            SectionCard(title = stringResource(R.string.line_feeder_title), icon = Icons.Filled.Badge, tone = Tone.BRAND) {
                Column {
                    ChoiceRow(
                        label = stringResource(R.string.line_feeder),
                        options = LinePayload.FEEDERS,
                        selected = payload.feeder,
                        optionLabel = { if (it == "other") stringResource(R.string.line_feeder_other) else it },
                        onSelect = viewModel::setFeeder
                    )
                    if (payload.feeder == "other") {
                        AppTextField(stringResource(R.string.line_feeder_name), payload.feederName, viewModel::setFeederName)
                    }
                    ChoiceRow(
                        label = stringResource(R.string.line_network),
                        options = LinePayload.NETWORKS,
                        selected = payload.network,
                        optionLabel = { stringResource(networkLabel(it)) },
                        onSelect = viewModel::setNetwork
                    )
                    AddressField(item.address.orEmpty(), item.latitude, item.longitude, viewModel::setAddress)
                    AppTextField(stringResource(R.string.line_note), payload.note, viewModel::setNote, singleLine = false, minLines = 2)
                }
            }
            // The feeder's own reading at the panel: the point the walk starts from.
            SectionCard(
                title = stringResource(R.string.line_start_title),
                subtitle = stringResource(R.string.line_start_hint),
                icon = Icons.Filled.PowerSettingsNew,
                tone = Tone.ACCENT
            ) {
                PhaseInputs(payload.start, viewModel::setStart)
            }
        }
        page == PAGE_POLES -> PolesPage(viewModel, onBack = back, onOpenPole = openPole)
        page == PAGE_ANALYSIS -> FormPage(title = stringResource(R.string.line_analysis_title), onBack = back) {
            LineFindings(viewModel.verdict())
        }
        page == PAGE_MEDIA -> MediaPage(viewModel, stampCode = item.plate.orEmpty(), onBack = back, allowVideo = true, allowAudio = false)
        page.orEmpty().startsWith(POLE_PREFIX) -> {
            val pole = viewModel.pole(page.orEmpty().removePrefix(POLE_PREFIX))
            if (pole == null) {
                LaunchedEffect(page) { toPoles() }
            } else {
                PolePage(viewModel, pole, onList = toPoles, onOpenPole = openPole,
                    onPickOnMap = { pageState.value = POLE_MAP_PREFIX + pole.id },
                    onOpenSubscriber = { pageState.value = SUB_PREFIX + pole.id + ":" + it })
            }
        }
        page.orEmpty().startsWith(POLE_MAP_PREFIX) -> {
            val id = page.orEmpty().removePrefix(POLE_MAP_PREFIX)
            val pole = viewModel.pole(id)
            BackHandler { returnToPole(id) }
            LivePositionPage(
                title = stringResource(R.string.line_pole_title, PersianNumbers.toPersian(viewModel.number(id))),
                recordedLatitude = pole?.latitude,
                recordedLongitude = pole?.longitude,
                onRecord = { viewModel.placePole(id, it) },
                onBack = { returnToPole(id) },
                // The user stands at the pole to measure it, so the point must be where they are.
                presenceLimit = container.prefs.presenceLimitM.toDouble()
            )
        }
        page.orEmpty().startsWith(SUB_PREFIX) || page.orEmpty().startsWith(SUB_MAP_PREFIX) -> {
            val onMap = page.orEmpty().startsWith(SUB_MAP_PREFIX)
            val (poleId, subId) = page.orEmpty().substringAfter(':').split(':').let { it.first() to it.getOrElse(1) { "" } }
            val pole = viewModel.pole(poleId)
            val subscriber = viewModel.subscriber(poleId, subId)
            val toSubscriber = { pageState.value = SUB_PREFIX + poleId + ":" + subId }
            if (pole == null || subscriber == null) {
                LaunchedEffect(page) { if (pole == null) toPoles() else openPole(poleId) }
            } else if (onMap) {
                BackHandler(onBack = toSubscriber)
                LivePositionPage(
                    title = stringResource(R.string.sub_map_title, PersianNumbers.toPersian(pole.subscribers.indexOf(subscriber) + 1)),
                    recordedLatitude = subscriber.latitude,
                    recordedLongitude = subscriber.longitude,
                    onRecord = { viewModel.placeSubscriber(poleId, subId, it) },
                    onBack = toSubscriber
                )
            } else {
                BackHandler { openPole(poleId) }
                SubscriberPage(viewModel, pole, subscriber, onBack = { openPole(poleId) },
                    onOpenMap = { pageState.value = SUB_MAP_PREFIX + poleId + ":" + subId })
            }
        }
    }
}

internal const val PAGE_FEEDER = "feeder"
internal const val PAGE_CREW = "crew"
internal const val PAGE_PANEL_MAP = "panel_map"
internal const val PAGE_POLES = "poles"
internal const val PAGE_ANALYSIS = "analysis"
private const val POLE_PREFIX = "pole:"
private const val POLE_MAP_PREFIX = "polemap:"
private const val SUB_PREFIX = "sub:"
private const val SUB_MAP_PREFIX = "submap:"

fun networkLabel(key: String): Int = when (key) {
    "abc" -> R.string.line_network_abc
    "underground" -> R.string.line_network_underground
    else -> R.string.line_network_overhead
}
