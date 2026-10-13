package ir.ilam.inspection.field.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.ServerInfo
import ir.ilam.inspection.field.data.ServerStatus
import ir.ilam.inspection.field.data.SyncState
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.field
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * A sent item as the reporter follows it: its code, where it is on its way
 * through the office, why it was turned back if it was, and what came of it —
 * as much of the result as the manager lets reporters see.
 */
@Composable
fun ItemScreen(itemId: String, onBack: () -> Unit) {
    val dao = LocalContext.current.field.database.dao()
    val item by remember(itemId) { dao.observeItem(itemId) }.collectAsState(initial = null)
    val files by produceState(emptyList<FieldFileEntity>(), itemId) { value = dao.files(itemId) }
    val current = item
    val kind = current?.let { FieldKind.of(it.kind) }
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = kind?.let { kindLabel(it) } ?: "",
            subtitle = current?.trackingCode ?: stringResource(R.string.field_code_waiting),
            onBack = onBack
        )
        if (current == null || kind == null) return@Column
        val info = remember(current.serverInfo) { ServerInfo.fromJson(current.serverInfo) }
        val status = ServerStatus.of(current.serverStatus)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            Hero(current, kind)
            if (info?.note != null && (status == ServerStatus.REJECTED || status == ServerStatus.REVISIT)) {
                Answer(
                    title = stringResource(if (status == ServerStatus.REJECTED) R.string.item_rejected_title else R.string.item_revisit_title),
                    text = info.note,
                    at = info.noteAt,
                    tone = if (status == ServerStatus.REJECTED) Tone.DANGER else Tone.WARNING
                )
            }
            ResultCard(status, info)
            ItemTimeline(current, info)
            SectionCard(title = stringResource(R.string.item_details), icon = Icons.AutoMirrored.Filled.Assignment, tone = Tone.BRAND) {
                Column {
                    ValueRow(stringResource(R.string.item_created), PersianDate.formatWithTime(current.createdAt))
                    ValueRow(stringResource(R.string.item_address), current.address)
                    ValueRow(stringResource(R.string.item_plate), current.plate, ltr = true)
                    ValueRow(stringResource(R.string.item_description), current.description)
                    ValueRow(stringResource(R.string.item_files), PersianNumbers.toPersian(files.size))
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun Hero(item: FieldItemEntity, kind: FieldKind) {
    val sync = SyncState.of(item.syncState)
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ToneIcon(kindIcon(kind), kindTone(kind), size = 56.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.trackingCode ?: stringResource(R.string.field_code_waiting),
                    style = if (item.trackingCode != null) MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr)
                    else MaterialTheme.typography.bodyMedium
                )
                Text(PersianDate.formatWithTime(item.createdAt), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ServerStatus.of(item.serverStatus)?.let { StatusBadge(statusLabel(it), statusTone(it), solid = true) }
                ?: StatusBadge(syncLabel(sync), syncTone(sync))
        }
        item.syncError?.let { error ->
            Text(
                if (error == "file_incomplete") stringResource(R.string.field_error_files) else error,
                color = Tavan.colors.danger.strong,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)
            )
        }
    }
}

/** The office's words to the reporter, in the colour of what they mean. */
@Composable
private fun Answer(title: String, text: String, at: Long?, tone: Tone) {
    val colors = Tavan.colors.of(tone)
    SectionCard(title = title, icon = Icons.Filled.Campaign, tone = tone) {
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onContainer)
            at?.let {
                Text(PersianDate.formatWithTime(it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
    }
}

/**
 * What came of it. The outcome in large type; the expert's note and the case
 * figures only when the manager shows reporters the details; and when the
 * manager shows nothing, a plain line that there is a result.
 */
@Composable
private fun ResultCard(status: ServerStatus?, info: ServerInfo?) {
    if (info == null || !info.hasResult) {
        if (status == ServerStatus.RESULT || status == ServerStatus.CLOSED && info?.note == null) {
            SectionCard(title = stringResource(R.string.item_result_title), icon = Icons.Filled.FactCheck, tone = Tone.NEUTRAL) {
                Text(stringResource(R.string.item_result_hidden), style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val tone = when (info.outcome) {
        "confirmed", "case" -> Tone.SUCCESS
        "no_access" -> Tone.WARNING
        else -> Tone.INFO
    }
    SectionCard(title = stringResource(R.string.item_result_title), icon = Icons.Filled.FactCheck, tone = tone) {
        Column {
            Text(
                info.outcomeName ?: if (info.outcome == "case") stringResource(R.string.item_outcome_case) else "",
                style = MaterialTheme.typography.headlineSmall,
                color = Tavan.colors.of(tone).strong
            )
            info.resultAt?.let {
                Text(PersianDate.formatWithTime(it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            info.resultNote?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.sm)) }
            info.case?.let { case ->
                Text(stringResource(R.string.item_case_title), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Spacing.md))
                case.trackingCode?.let { ValueRow(stringResource(R.string.item_case_title), it, ltr = true) }
                case.devices?.let { Text(stringResource(R.string.item_case_devices, PersianNumbers.toPersian(it)),
                    style = MaterialTheme.typography.bodyLarge) }
                case.totalWatt?.takeIf { it > 0 }?.let {
                    Text(stringResource(R.string.item_case_power, PersianNumbers.toPersian(Math.round(it / 100.0) / 10.0)),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
