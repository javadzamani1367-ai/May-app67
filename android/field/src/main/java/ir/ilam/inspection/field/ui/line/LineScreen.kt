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
import ir.ilam.inspection.field.ui.form.FormHub
import ir.ilam.inspection.field.ui.form.FormPage
import ir.ilam.inspection.field.ui.form.MediaPage
import ir.ilam.inspection.field.ui.form.PAGE_MEDIA
import ir.ilam.inspection.field.ui.form.rememberFormPage
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ChoiceRow
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.location.LivePositionPage
import ir.ilam.inspection.ui.location.rememberFresh
import ir.ilam.inspection.ui.location.rememberLiveLocation
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
    val reading = rememberLiveLocation()
    val fresh = rememberFresh(reading)
    val pageState = rememberFormPage()
    // The app may be closed in the background (a call, another app): write the last edit now.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }
    val hubScroll = rememberScrollState()
    val item = viewModel.item
    val payload = viewModel.payload
    val title = stringResource(R.string.field_line)
    val back = { pageState.value = null }
    val toPoles = { pageState.value = PAGE_POLES }
    val openPole = { id: String -> pageState.value = POLE_PREFIX + id }

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
            onOpen = { pageState.value = it },
            onFinish = { viewModel.finish(context, onClose) },
            onDiscard = { viewModel.discard(onClose) },
            onClose = onClose,
            editor = viewModel,
            scroll = hubScroll,
            header = { LineHubHeader(viewModel) }
        )
        page == PAGE_FEEDER -> FormPage(title = stringResource(R.string.line_feeder_title), onBack = back) {
            SectionCard(title = stringResource(R.string.line_feeder_title), icon = Icons.Filled.Badge, tone = Tone.BRAND) {
                Column {
                    AppTextField(stringResource(R.string.line_panel_plate), item.plate.orEmpty(), viewModel::setPlate, ltr = true,
                        error = if (item.plate.isNullOrBlank()) stringResource(R.string.missing_line_plate) else null)
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
        }
        page == PAGE_START -> FormPage(title = stringResource(R.string.line_start_title), onBack = back) {
            SectionCard(
                title = stringResource(R.string.line_start_title),
                subtitle = stringResource(R.string.line_start_hint),
                icon = Icons.Filled.PowerSettingsNew,
                tone = Tone.ACCENT
            ) {
                PhaseInputs(payload.start, viewModel::setStart)
            }
        }
        page == PAGE_POLES -> PolesPage(viewModel, here = reading?.fix?.takeIf { fresh }, onBack = back, onOpenPole = openPole)
        page == PAGE_ANALYSIS -> FormPage(title = stringResource(R.string.line_analysis_title), onBack = back) {
            LineFindings(viewModel.verdict())
        }
        page == PAGE_MEDIA -> MediaPage(viewModel, stampCode = item.plate.orEmpty(), onBack = back, allowVideo = true, allowAudio = false)
        page.orEmpty().startsWith(POLE_PREFIX) -> {
            val pole = viewModel.pole(page.orEmpty().removePrefix(POLE_PREFIX))
            if (pole == null) {
                LaunchedEffect(page) { toPoles() }
            } else {
                BackHandler { toPoles() }
                PolePage(viewModel, pole, reading, fresh, onList = toPoles, onOpenPole = openPole,
                    onPickOnMap = { pageState.value = POLE_MAP_PREFIX + pole.id })
            }
        }
        page.orEmpty().startsWith(POLE_MAP_PREFIX) -> {
            val id = page.orEmpty().removePrefix(POLE_MAP_PREFIX)
            val pole = viewModel.pole(id)
            BackHandler { openPole(id) }
            LivePositionPage(
                title = stringResource(R.string.line_pole_title, PersianNumbers.toPersian(viewModel.number(id))),
                recordedLatitude = pole?.latitude,
                recordedLongitude = pole?.longitude,
                onRecordFix = { fix, _ -> viewModel.locatePole(id, fix.latitude, fix.longitude, fix.accuracy) },
                onRecordPoint = { lat, lon -> viewModel.locatePole(id, lat, lon, null) },
                onBack = { openPole(id) }
            )
        }
    }
}

internal const val PAGE_FEEDER = "feeder"
internal const val PAGE_START = "start"
internal const val PAGE_POLES = "poles"
internal const val PAGE_ANALYSIS = "analysis"
private const val POLE_PREFIX = "pole:"
private const val POLE_MAP_PREFIX = "polemap:"

fun networkLabel(key: String): Int = when (key) {
    "abc" -> R.string.line_network_abc
    "underground" -> R.string.line_network_underground
    else -> R.string.line_network_overhead
}
