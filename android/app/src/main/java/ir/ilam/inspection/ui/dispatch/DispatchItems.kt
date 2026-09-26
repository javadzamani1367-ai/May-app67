package ir.ilam.inspection.ui.dispatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.model.AttachmentCategory
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.attachmentCategoryLabel
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.FileStore

/**
 * What the chosen unit will receive. Photos carry their own preview, because
 * picking by timestamp alone means guessing. Documents are grouped by category;
 * a manager sees every category — including the ones still empty, so it is
 * visible what the file is missing — while an expert sees only what exists.
 */
@Composable
fun DispatchItems(
    detail: ReportDetail,
    state: DispatchState,
    files: FileStore,
    isManager: Boolean,
    onToggleReportForm: () -> Unit,
    onToggleFullBundle: () -> Unit,
    onToggleMedia: (String) -> Unit,
    onToggleAttachment: (String) -> Unit
) {
    SectionCard(
        title = stringResource(R.string.dispatch_items),
        subtitle = stringResource(R.string.dispatch_items_hint),
        icon = Icons.Filled.Checklist,
        tone = Tone.ACCENT
    ) {
        Column {
            CheckRow(
                label = stringResource(R.string.dispatch_report_form),
                checked = state.includeReportForm,
                onToggle = onToggleReportForm
            )

            if (isManager) {
                CheckRow(
                    label = stringResource(R.string.dispatch_full_bundle),
                    checked = state.fullBundle,
                    onToggle = onToggleFullBundle
                )
            }

            // Videos travel as their own files beside the report; a report
            // cannot contain one, and before this a ticked video simply never
            // left the phone.
            val media = detail.photos + detail.videos
            if (media.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.dispatch_media_group),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    media.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                SelectableMediaTile(
                                    media = item,
                                    label = mediaLabel(item),
                                    files = files,
                                    checked = item.id in state.mediaIds,
                                    onToggle = { onToggleMedia(item.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }

            if (isManager) {
                Text(
                    text = stringResource(R.string.dispatch_all_categories),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            val grouped = detail.attachments.groupBy { AttachmentCategory.of(it.category) }
            val categories = if (isManager) {
                AttachmentCategory.entries.toList()
            } else {
                AttachmentCategory.entries.filter { grouped.containsKey(it) }
            }
            categories.forEach { category ->
                Text(
                    text = attachmentCategoryLabel(category),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
                val rows = grouped[category].orEmpty()
                if (rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dispatch_category_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                rows.forEach { attachment ->
                    CheckRow(
                        label = attachment.title?.takeIf { it.isNotBlank() }
                            ?: attachmentCategoryLabel(category),
                        checked = attachment.id in state.attachmentIds,
                        onToggle = { onToggleAttachment(attachment.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun mediaLabel(media: MediaEntity): String = media.caption?.takeIf { it.isNotBlank() }
    ?: stringResource(
        if (media.type == MediaType.VIDEO.code) R.string.dispatch_video_unnamed else R.string.dispatch_photo_unnamed
    )

/** A tickable line, tinted when ticked so a long list shows at a glance what is going. */
@Composable
internal fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val accent = Tavan.colors.accent
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.small,
        color = if (checked) accent.container else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = accent.strong)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) accent.onContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
