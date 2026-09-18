package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.ui.common.MediaThumbnail
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate

/**
 * One captured file: a preview so the expert can see what it is, the caption,
 * and the way to remove it.
 */
@Composable
fun MediaRow(
    media: MediaEntity,
    files: FileStore,
    standardCaptions: List<String>,
    onCaptionChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MediaThumbnail(media = media, files = files)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = PersianDate.formatWithTime(media.capturedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MediaCaptionField(
                caption = media.caption,
                standardCaptions = standardCaptions,
                onCaptionChange = onCaptionChange
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}
