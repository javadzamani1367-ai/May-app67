package ir.ilam.inspection.field.ui.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.Priority
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.ui.form.AddressField
import ir.ilam.inspection.field.ui.form.FormActions
import ir.ilam.inspection.field.ui.form.MediaSection
import ir.ilam.inspection.field.ui.form.MissingCard
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.location.PositionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

/**
 * A crypto-mining or illegal-power report, on one scrolling page. Everything
 * is saved as it is typed; the report is sent only when the user finishes it,
 * and only once it has a position and a description.
 */
@Composable
fun ReportScreen(kind: FieldKind, itemId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.field
    val viewModel: ReportViewModel = viewModel(key = "report-${itemId ?: "new"}-${kind.code}") {
        ReportViewModel(container, kind, itemId)
    }
    val item = viewModel.item
    val crypto = kind == FieldKind.CRYPTO

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = stringResource(if (crypto) R.string.field_crypto else R.string.field_illegal),
            subtitle = stringResource(R.string.form_autosaved),
            onBack = onClose
        )
        if (item == null) return@Column
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
        ) {
            PositionCard(
                latitude = item.latitude,
                longitude = item.longitude,
                accuracy = item.accuracy,
                onMeasured = { fix, _ -> viewModel.setMeasured(fix) },
                onPicked = { lat, lon -> viewModel.setPosition(lat, lon, null) },
                onTyped = { lat, lon -> viewModel.setPosition(lat, lon, null) }
            )
            SectionCard(title = stringResource(R.string.report_place), icon = Icons.Filled.Place, tone = Tone.INFO) {
                Column {
                    AddressField(item.address.orEmpty(), item.latitude, item.longitude, viewModel::setAddress)
                    AppTextField(stringResource(R.string.report_nearest_plate), item.plate.orEmpty(), viewModel::setPlate, ltr = true)
                }
            }
            SectionCard(
                title = stringResource(R.string.report_description),
                subtitle = stringResource(if (crypto) R.string.report_description_hint_crypto else R.string.report_description_hint_illegal),
                icon = Icons.AutoMirrored.Filled.Notes,
                tone = Tone.BRAND
            ) {
                MultilineField(stringResource(R.string.report_description_label), item.description.orEmpty(), viewModel::setDescription)
            }
            if (crypto) {
                CryptoDetails(viewModel.details, viewModel::edit)
            } else {
                IllegalDetails(viewModel.details, viewModel::edit)
            }
            TeamSection(viewModel.details, Priority.of(item.priority), viewModel::edit, viewModel::setPriority)
            MediaSection(viewModel, stampCode = stringResource(if (crypto) R.string.field_kind_crypto else R.string.field_kind_illegal))
            MissingCard(viewModel.missing().map {
                stringResource(if (it == MissingKey.POSITION) R.string.missing_position else R.string.missing_description)
            })
            FormActions(
                canFinish = viewModel.missing().isEmpty(),
                busy = viewModel.busy,
                onFinish = { viewModel.finish(context, onClose) },
                onDiscard = { viewModel.discard(onClose) }
            )
        }
    }
}
