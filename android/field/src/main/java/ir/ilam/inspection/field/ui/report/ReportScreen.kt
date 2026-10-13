package ir.ilam.inspection.field.ui.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.Priority
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.ui.form.PlaceBlock
import ir.ilam.inspection.field.ui.form.FormHub
import ir.ilam.inspection.field.ui.form.FormPage
import ir.ilam.inspection.field.ui.form.ItemPositionPage
import ir.ilam.inspection.field.ui.form.MediaPage
import ir.ilam.inspection.field.ui.form.PAGE_MEDIA
import ir.ilam.inspection.field.ui.form.PAGE_POSITION
import ir.ilam.inspection.field.ui.form.rememberFormPage
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.location.rememberFresh
import ir.ilam.inspection.ui.location.rememberLiveLocation
import ir.ilam.inspection.ui.theme.Tone

/**
 * A crypto-mining, illegal-power or burnt-equipment report: one page of
 * tiles, each part on a page of its own. Everything is saved as it is typed;
 * the report is sent only when the user finishes it, and only once it has a
 * position and a description (and, for a burn, what burnt).
 */
@Composable
fun ReportScreen(kind: FieldKind, itemId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.field
    val viewModel: ReportViewModel = viewModel(key = "report-${itemId ?: "new"}-${kind.code}") {
        ReportViewModel(container, kind, itemId)
    }
    // The receiver starts now, so the position is ready by the time it is asked for.
    val reading = rememberLiveLocation()
    val fresh = rememberFresh(reading)
    val pageState = rememberFormPage()
    val hubScroll = rememberScrollState()
    val item = viewModel.item
    val title = stringResource(reportTitle(kind))
    val back = { pageState.value = null }

    if (item == null) {
        Column(Modifier.fillMaxSize()) { TavanTopBar(title = title, onBack = onClose) }
        return
    }
    when (pageState.value) {
        null -> FormHub(
            title = title,
            sections = reportSections(kind, item, viewModel.details, viewModel.files),
            missing = viewModel.missing().map { missingText(it) },
            busy = viewModel.busy,
            onOpen = { pageState.value = it },
            onFinish = { viewModel.finish(context, onClose) },
            onDiscard = { viewModel.discard(onClose) },
            onClose = onClose,
            scroll = hubScroll
        )
        PAGE_POSITION -> {
            BackHandler { pageState.value = PAGE_PLACE }
            ItemPositionPage(viewModel, item, onBack = { pageState.value = PAGE_PLACE })
        }
        PAGE_PLACE -> FormPage(title = stringResource(R.string.place_title), onBack = back) {
            PlaceBlock(viewModel, item, reading, fresh, onOpenMap = { pageState.value = PAGE_POSITION },
                onAddress = viewModel::setAddress) {
                AppTextField(stringResource(R.string.report_nearest_plate), item.plate.orEmpty(), viewModel::setPlate, ltr = true)
            }
            SectionCard(
                title = stringResource(R.string.report_description),
                subtitle = stringResource(descriptionHint(kind)),
                icon = Icons.AutoMirrored.Filled.Notes,
                tone = Tone.BRAND
            ) {
                MultilineField(stringResource(R.string.report_description_label), item.description.orEmpty(), viewModel::setDescription,
                    error = if (item.description.isNullOrBlank()) stringResource(R.string.missing_description) else null)
            }
        }
        PAGE_DETAILS -> FormPage(title = stringResource(detailsTitle(kind)), onBack = back) {
            when (kind) {
                FieldKind.CRYPTO -> CryptoDetails(viewModel.details, viewModel::edit)
                FieldKind.BURN -> {
                    BurnEquipment(viewModel.details, viewModel::edit)
                    BurnCircumstances(viewModel.details, viewModel::edit)
                }
                else -> IllegalDetails(viewModel.details, viewModel::edit)
            }
        }
        PAGE_TEAM -> FormPage(title = stringResource(R.string.report_team_title), onBack = back) {
            TeamSection(viewModel.details, Priority.of(item.priority), viewModel::edit, viewModel::setPriority)
        }
        PAGE_MEDIA -> MediaPage(viewModel, stampCode = stringResource(kindStamp(kind)), onBack = back)
    }
}

@Composable
private fun missingText(key: MissingKey): String = stringResource(
    when (key) {
        MissingKey.POSITION -> R.string.missing_position
        MissingKey.DESCRIPTION -> R.string.missing_description
        MissingKey.EQUIPMENT -> R.string.missing_equipment
    }
)

fun reportTitle(kind: FieldKind): Int = when (kind) {
    FieldKind.CRYPTO -> R.string.field_crypto
    FieldKind.BURN -> R.string.field_burn
    else -> R.string.field_illegal
}

fun descriptionHint(kind: FieldKind): Int = when (kind) {
    FieldKind.CRYPTO -> R.string.report_description_hint_crypto
    FieldKind.BURN -> R.string.report_description_hint_burn
    else -> R.string.report_description_hint_illegal
}

fun detailsTitle(kind: FieldKind): Int = when (kind) {
    FieldKind.CRYPTO -> R.string.report_crypto_details
    FieldKind.BURN -> R.string.burn_equipment_title
    else -> R.string.report_illegal_details
}

private fun kindStamp(kind: FieldKind): Int = when (kind) {
    FieldKind.CRYPTO -> R.string.field_kind_crypto
    FieldKind.BURN -> R.string.field_kind_burn
    else -> R.string.field_kind_illegal
}
