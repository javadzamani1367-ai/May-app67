package ir.ilam.inspection.field.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.ServerInfo
import ir.ilam.inspection.field.data.ServerStatus
import ir.ilam.inspection.field.data.SyncState
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate

/** One stop on an item's way through the office. */
private data class Stop(val label: String, val reached: Boolean, val tone: Tone, val icon: ImageVector? = null, val at: Long? = null)

/**
 * Where the item has been and where it is now, as a line of stops: saved,
 * sent, received, under review, referred, result, closed. A rejection or a
 * request to go back ends the line in its own colour. The stop it is at now
 * is drawn larger; stops still ahead are grey.
 */
@Composable
fun ItemTimeline(item: FieldItemEntity, info: ServerInfo?) {
    val status = ServerStatus.of(item.serverStatus)
    val sync = SyncState.of(item.syncState)
    val sent = status != null || sync == SyncState.SENT
    val stops = buildList {
        add(Stop(stringResource(R.string.item_step_saved), true, Tone.SUCCESS, at = item.createdAt))
        add(Stop(
            if (sync == SyncState.ERROR) stringResource(R.string.field_state_error) else stringResource(R.string.item_step_sent),
            sent, if (sync == SyncState.ERROR) Tone.DANGER else Tone.SUCCESS, at = item.sentAt
        ))
        add(Stop(stringResource(R.string.item_step_received), status != null, Tone.SUCCESS))
        add(Stop(statusLabel(ServerStatus.REVIEWING), (status?.code ?: -1) >= ServerStatus.REVIEWING.code, Tone.SUCCESS))
        when (status) {
            ServerStatus.REJECTED -> add(Stop(statusLabel(ServerStatus.REJECTED), true, Tone.DANGER, Icons.Filled.Close, info?.noteAt))
            ServerStatus.REVISIT -> add(Stop(statusLabel(ServerStatus.REVISIT), true, Tone.WARNING, Icons.Filled.Refresh, info?.noteAt))
            else -> {
                val code = status?.code ?: -1
                val skipped = status == ServerStatus.CLOSED && info?.hasResult != true
                if (!skipped) {
                    add(Stop(statusLabel(ServerStatus.REFERRED), code >= ServerStatus.REFERRED.code, Tone.SUCCESS))
                    add(Stop(statusLabel(ServerStatus.RESULT), code >= ServerStatus.RESULT.code, Tone.SUCCESS, at = info?.resultAt))
                }
                add(Stop(statusLabel(ServerStatus.CLOSED), code == ServerStatus.CLOSED.code, Tone.SUCCESS))
            }
        }
    }
    val current = stops.indexOfLast { it.reached }
    SectionCard(title = stringResource(R.string.item_timeline), tone = Tone.ACCENT) {
        Column {
            stops.forEachIndexed { index, stop ->
                StopRow(stop, isCurrent = index == current, isLast = index == stops.lastIndex,
                    nextReached = stops.getOrNull(index + 1)?.reached == true)
            }
        }
    }
}

@Composable
private fun StopRow(stop: Stop, isCurrent: Boolean, isLast: Boolean, nextReached: Boolean) {
    val colors = Tavan.colors.of(stop.tone)
    val grey = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp).fillMaxHeight()) {
            val size = if (isCurrent) 28.dp else 22.dp
            Box(
                modifier = Modifier.size(size)
                    .background(if (stop.reached) colors.strong else MaterialTheme.colorScheme.surface, CircleShape)
                    .border(2.dp, if (stop.reached) colors.strong else grey, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (stop.reached) {
                    Icon(stop.icon ?: Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(size * 0.6f))
                }
            }
            if (!isLast) {
                Box(modifier = Modifier.width(3.dp).weight(1f).background(if (nextReached) colors.strong else grey))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = if (isLast) 0.dp else 14.dp, top = 1.dp)) {
            Text(
                stop.label,
                style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = when {
                    isCurrent -> colors.strong
                    stop.reached -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                }
            )
            stop.at?.takeIf { stop.reached }?.let {
                Text(PersianDate.formatWithTime(it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
