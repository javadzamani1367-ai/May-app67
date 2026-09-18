package ir.ilam.inspection.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.DispatchEntity
import ir.ilam.inspection.data.model.DispatchChannel
import ir.ilam.inspection.data.model.DispatchStatus
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.ui.common.dispatchUnitLabel
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import org.json.JSONArray

/**
 * One hand-off, as the record of it should read: which unit, when, how it
 * left, how many items went with it, by when an answer is due, and what came
 * back. Enough that nobody has to ask "did we ever send this, and to whom?".
 */
@Composable
fun DispatchHistoryRow(dispatch: DispatchEntity, showDivider: Boolean) {
    val status = DispatchStatus.of(dispatch.status)
    val overdue = status.isOverdue(dispatch.deadlineAt)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (showDivider) HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = dispatchUnitLabel(DispatchUnit.of(dispatch.unit)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    if (overdue) R.string.dispatch_status_overdue else statusLabel(status)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Text(
            text = listOfNotNull(
                PersianDate.formatWithTime(dispatch.dispatchedAt),
                stringResource(channelLabel(DispatchChannel.of(dispatch.channel))),
                stringResource(R.string.dispatch_item_count, itemCount(dispatch.includedItems))
            ).joinToString(" — "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.dispatch_deadline) + ": " + (
                dispatch.deadlineAt?.let { PersianDate.format(it) }
                    ?: stringResource(R.string.dispatch_deadline_none)
                ),
            style = MaterialTheme.typography.bodySmall,
            color = if (overdue) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        dispatch.note?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
        dispatch.answer?.takeIf { it.isNotBlank() }?.let { answer ->
            Text(
                text = stringResource(R.string.dispatch_answer) + ": " + answer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun statusLabel(status: DispatchStatus): Int = when (status) {
    DispatchStatus.SENT -> R.string.dispatch_status_sent
    DispatchStatus.SEEN -> R.string.dispatch_status_seen
    DispatchStatus.ANSWERED -> R.string.dispatch_status_answered
}

private fun channelLabel(channel: DispatchChannel): Int = when (channel) {
    DispatchChannel.SYSTEM -> R.string.dispatch_channel_system
    DispatchChannel.SOCIAL -> R.string.dispatch_channel_social
    DispatchChannel.OFFLINE_PACKAGE -> R.string.dispatch_channel_offline
}

/** The stored list is JSON; a malformed one counts as nothing rather than crashing. */
private fun itemCount(includedItems: String): String =
    PersianNumbers.toPersian(
        runCatching { JSONArray(includedItems).length() }.getOrDefault(0)
    )
