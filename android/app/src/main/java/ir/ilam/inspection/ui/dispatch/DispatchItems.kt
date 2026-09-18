package ir.ilam.inspection.ui.dispatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
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
import ir.ilam.inspection.ui.common.MediaThumbnail
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.attachmentCategoryLabel
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate

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
    SectionCard(title = stringResource(R.string.dispatch_items)) {
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

            detail.photos.forEach { photo ->
                PhotoRow(
                    photo = photo,
                    files = files,
                    checked = photo.id in state.mediaIds,
                    onToggle = { onToggleMedia(photo.id) }
                )
            }

            // Videos travel as their own files; a report cannot contain one,
            // and before this a ticked video simply never left the phone.
            detail.videos.forEach { video ->
                PhotoRow(
                    photo = video,
                    files = files,
                    checked = video.id in state.mediaIds,
                    onToggle = { onToggleMedia(video.id) }
                )
            }

            if (isManager) {
                Text(
                    text = stringResource(R.string.dispatch_all_categories),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp)
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

/** A photo row: preview, its caption, and when it was taken. */
@Composable
private fun PhotoRow(
    photo: MediaEntity,
    files: FileStore,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        MediaThumbnail(media = photo, files = files, sizeDp = 56)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = photo.caption?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.dispatch_photo_unnamed),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = PersianDate.formatWithTime(photo.capturedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
