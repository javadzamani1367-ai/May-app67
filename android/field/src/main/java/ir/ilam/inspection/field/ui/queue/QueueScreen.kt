package ir.ilam.inspection.field.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.ServerStatus
import ir.ilam.inspection.field.data.SyncState
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.field.data.db.QueueCounts
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.sync.SyncOutcome
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.GroupHeader
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.launch

/**
 * Everything that has left the phone or is waiting to: each item's own state
 * (waiting, sent, error with the reason), its tracking code once the server
 * gave one, and where the office has taken it.
 */
@Composable
fun QueueScreen(onBack: () -> Unit, onSignInAgain: () -> Unit, onOpenDraft: (FieldItemEntity) -> Unit) {
    val container = LocalContext.current.field
    val items by container.database.dao().observeQueue().collectAsState(initial = emptyList())
    val drafts by container.database.dao().observeDrafts().collectAsState(initial = emptyList())
    val counts by container.database.dao().observeCounts().collectAsState(initial = QueueCounts(0, 0, 0))
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<SyncOutcome?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.field_queue), onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = Spacing.screen)) {
            PrimaryButton(
                text = stringResource(R.string.field_send_now),
                onClick = {
                    scope.launch {
                        busy = true
                        outcome = container.sync.run()
                        busy = false
                        if (outcome == SyncOutcome.SIGN_IN_NEEDED) onSignInAgain()
                    }
                },
                busy = busy,
                icon = Icons.Filled.CloudUpload,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
            )
            outcome?.let { Outcome(it) }
            Text(
                stringResource(
                    R.string.field_queue_summary,
                    PersianNumbers.toPersian(counts.pending),
                    PersianNumbers.toPersian(counts.sent),
                    PersianNumbers.toPersian(counts.failed)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm)
            )
        }
        if (items.isEmpty() && drafts.isEmpty()) {
            EmptyState(message = stringResource(R.string.field_queue_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
                // Unfinished work first: a draft is never sent until it is finished,
                // and a tap reopens it where it was left.
                if (drafts.isNotEmpty()) {
                    item { GroupHeader(stringResource(R.string.field_drafts)) }
                    items(drafts, key = { it.id }) { QueueRow(it, onClick = { onOpenDraft(it) }) }
                    item { GroupHeader(stringResource(R.string.field_queue)) }
                }
                items(items, key = { it.id }) { QueueRow(it) }
            }
        }
    }
}

@Composable
private fun Outcome(outcome: SyncOutcome) {
    val (text, color) = when (outcome) {
        SyncOutcome.DONE -> R.string.field_sync_done to Tavan.colors.success.strong
        SyncOutcome.OFFLINE -> R.string.field_sync_offline to Tavan.colors.warning.strong
        SyncOutcome.SIGN_IN_NEEDED, SyncOutcome.NOT_SIGNED_IN -> R.string.field_sync_sign_in to Tavan.colors.danger.strong
    }
    Text(stringResource(text), color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
}

@Composable
private fun QueueRow(item: FieldItemEntity, onClick: (() -> Unit)? = null) {
    val state = SyncState.of(item.syncState)
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kindLabel(FieldKind.of(item.kind)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusBadge(text = syncLabel(state), tone = syncTone(state))
            }
            Text(
                text = item.trackingCode ?: stringResource(R.string.field_code_waiting),
                style = if (item.trackingCode != null) {
                    MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                PersianDate.formatWithTime(item.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ServerStatus.of(item.serverStatus)?.let {
                Text(statusLabel(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            item.syncError?.let { error ->
                Text(
                    text = if (error == "file_incomplete") stringResource(R.string.field_error_files) else error,
                    color = Tavan.colors.danger.strong,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** The queue in one line on the home screen; tapping it opens the queue. */
@Composable
fun QueueSummary(counts: QueueCounts, onOpen: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), onClick = onOpen) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.field_queue), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.field_queue_summary,
                    PersianNumbers.toPersian(counts.pending),
                    PersianNumbers.toPersian(counts.sent),
                    PersianNumbers.toPersian(counts.failed)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (counts.failed > 0) Tavan.colors.danger.strong else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
