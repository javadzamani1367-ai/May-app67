package ir.ilam.inspection.ui.fielditems

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.history.ItemHistorySection
import ir.ilam.inspection.sync.FieldManage
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.location.MiniMap
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One field item, everything the office needs to decide on it: who sent it,
 * where, what was found, the photos, what has happened to it so far — and the
 * one button that moves it on (review, refer to an expert, reject, close…).
 */
@Composable
fun FieldItemScreen(id: String, viewModel: FieldItemsViewModel, onBack: () -> Unit) {
    val state by viewModel.detail.collectAsStateWithLifecycle()
    var acting by remember { mutableStateOf(false) }
    var handing by remember { mutableStateOf(false) }
    LaunchedEffect(id) { viewModel.open(id) }
    val item = state.detail?.takeIf { it.row.id == id }

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = item?.row?.trackingCode?.ifBlank { null } ?: stringResource(R.string.fi_title),
            subtitle = item?.let { stringResource(fieldKindLabel(it.row.kind)) },
            onBack = onBack
        )
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        if (state.saved) {
            Text(stringResource(R.string.fi_saved), color = Tavan.colors.success.strong, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm))
        }
        if (item == null) return@Column
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)
        ) {
            Overview(item)
            if (item.latitude != null && item.longitude != null) LocationCard(item.latitude, item.longitude, item.accuracy)
            if (item.details.isNotEmpty() || item.resultName != null) {
                SectionCard(title = stringResource(R.string.fi_details), icon = Icons.Filled.FactCheck, tone = Tone.INFO) {
                    Column {
                        item.details.forEach { (label, value) -> ValueRow(label, value) }
                        item.resultName?.let { ValueRow(stringResource(R.string.fi_result), it, emphasize = true) }
                        item.resultNote?.let { ValueRow(stringResource(R.string.fi_result_note), it) }
                    }
                }
            }
            if (item.photos.isNotEmpty()) Photos(item.photos, viewModel)
            if (item.row.kind in FieldManage.INSPECTIONS) ItemHistorySection(item.row.id)
            Timeline(item.events)
        }
        BottomActionBar {
            if (item.next.isEmpty()) {
                Text(stringResource(if (item.mergedInto != null) R.string.fi_merged else R.string.fi_no_next),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            } else {
                PrimaryButton(stringResource(R.string.fi_action_title), onClick = { acting = true }, enabled = !state.busy,
                    icon = Icons.AutoMirrored.Filled.Send, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.fh_button), onClick = { handing = true }, enabled = !state.busy,
                    modifier = Modifier.weight(1f))
            }
        }
    }

    if (handing && item != null) {
        FieldHandDialog(item, onDismiss = { handing = false },
            onHand = { executor, note -> viewModel.hand(item.row.id, executor, note, onDone = { handing = false }) },
            onFollowUp = { note -> viewModel.followUp(item.row.id, note, onDone = { handing = false }) })
    }

    if (acting && item != null) {
        FieldActionDialog(item, onDismiss = { acting = false }) { status, note, expert, due ->
            viewModel.move(item.row.id, status, note, expert, due, onDone = { acting = false })
        }
    }
}

@Composable
private fun Overview(item: FieldManage.Detail) {
    val row = item.row
    SectionCard(
        title = stringResource(fieldKindLabel(row.kind)),
        icon = fieldKindIcon(row.kind),
        tone = fieldKindTone(row.kind),
        trailing = {
            if (row.overdue) StatusBadge(stringResource(R.string.fi_overdue), Tone.DANGER, solid = true)
            else StatusBadge(stringResource(fieldStatusLabel(row.status)), fieldStatusTone(row.status), solid = true)
        }
    ) {
        Column {
            ValueRow(stringResource(R.string.fi_code), row.trackingCode, ltr = true, emphasize = true)
            ValueRow(stringResource(R.string.fi_created), PersianDate.formatWithTime(row.createdAt))
            ValueRow(stringResource(R.string.fi_reporter_label),
                row.reporter + item.reporterCode.takeIf { it.isNotBlank() }?.let { " (" + PersianNumbers.toPersian(it) + ")" }.orEmpty())
            if (row.area.isNotBlank()) ValueRow(stringResource(R.string.fh_area), row.area)
            row.handler?.let { ValueRow(stringResource(R.string.fh_handler), it) }
            row.expert?.let { ValueRow(stringResource(R.string.fi_expert_label), it) }
            row.dueAt?.let { ValueRow(stringResource(R.string.fi_due_label), PersianDate.format(it)) }
            ValueRow(stringResource(R.string.fi_address), row.address)
            ValueRow(stringResource(R.string.fi_plate), row.plate, ltr = true)
            ValueRow(stringResource(R.string.fi_description), item.description)
            ValueRow(stringResource(R.string.fi_position_note), item.positionNote)
            if (item.fileCount > 0) ValueRow(stringResource(R.string.fi_files), PersianNumbers.toPersian(item.fileCount))
        }
    }
}

@Composable
private fun LocationCard(lat: Double, lon: Double, accuracy: Double?) {
    val context = LocalContext.current
    val navigate = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Unit
    }
    SectionCard(title = stringResource(R.string.fi_location), icon = Icons.Filled.Place, tone = Tone.INFO) {
        Column {
            MiniMap(lat, lon, accuracy, Tavan.colors.info.strong, onClick = navigate,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium))
            SecondaryButton(stringResource(R.string.fi_navigate), navigate, icon = Icons.Filled.TravelExplore,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun Photos(photos: List<FieldManage.Photo>, viewModel: FieldItemsViewModel) {
    SectionCard(title = stringResource(R.string.fi_photos, PersianNumbers.toPersian(photos.size)), icon = Icons.Filled.PhotoLibrary,
        tone = Tone.NEUTRAL) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            photos.forEach { photo ->
                val bitmap by produceState<android.graphics.Bitmap?>(null, photo.id) {
                    value = withContext(Dispatchers.IO) { viewModel.photo(photo.id)?.let { Thumbnails.forPhoto(it, 480) } }
                }
                bitmap?.let {
                    Image(it.asImageBitmap(), contentDescription = photo.note.ifBlank { null }, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(150.dp).clip(MaterialTheme.shapes.medium))
                } ?: Text(stringResource(R.string.fi_photo_loading), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.size(150.dp).padding(Spacing.sm))
            }
        }
    }
}

@Composable
private fun Timeline(events: List<FieldManage.Event>) {
    if (events.isEmpty()) return
    SectionCard(title = stringResource(R.string.fi_history), icon = Icons.Filled.History, tone = Tone.NEUTRAL) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            events.asReversed().forEach { event ->
                Column {
                    Text(event.what, style = MaterialTheme.typography.titleSmall)
                    Text(
                        PersianDate.formatWithTime(event.at) + (event.who?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    event.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
