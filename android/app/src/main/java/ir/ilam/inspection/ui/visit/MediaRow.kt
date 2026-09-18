package ir.ilam.inspection.ui.visit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val THUMB_DP = 76

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

@Composable
private fun MediaThumbnail(media: MediaEntity, files: FileStore) {
    val isVideo = media.type == ir.ilam.inspection.data.model.MediaType.VIDEO.code
    val bitmap by produceState<Bitmap?>(initialValue = null, media.id) {
        value = withContext(Dispatchers.IO) {
            val file = files.resolve(media.filePath)
            if (isVideo) Thumbnails.forVideo(file) else Thumbnails.forPhoto(file, THUMB_DP * 3)
        }
    }

    Box(
        modifier = Modifier
            .size(THUMB_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = media.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMB_DP.dp)
            )
        }
        if (isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
