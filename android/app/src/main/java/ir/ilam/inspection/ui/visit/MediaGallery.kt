package ir.ilam.inspection.ui.visit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The evidence as a grid of pictures, two to a row: large enough to recognise
 * a meter from a rack of miners at a glance, which a list of dates never was.
 * Each tile says what it is; a tile with no caption says that too, in amber,
 * because an uncaptioned photo in an official report is a question waiting to
 * be asked.
 */
@Composable
fun MediaGrid(media: List<MediaEntity>, files: FileStore, onOpen: (MediaEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        media.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { item ->
                    MediaTile(item, files, onClick = { onOpen(item) }, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MediaTile(media: MediaEntity, files: FileStore, onClick: () -> Unit, modifier: Modifier) {
    val isVideo = media.type == MediaType.VIDEO.code
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
    ) {
        MediaPreview(media, files, edge = 360, modifier = Modifier.fillMaxSize())
        // A scrim so the caption reads on any photo, bright sky or dark room.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column {
                Text(
                    text = media.caption?.takeIf { it.isNotBlank() } ?: stringResource(R.string.media_no_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = PersianDate.formatWithTime(media.capturedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        Row(modifier = Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isVideo) {
                StatusBadge(text = stringResource(R.string.media_video_badge), tone = Tone.DANGER, icon = Icons.Filled.Videocam, solid = true)
            }
            if (media.caption.isNullOrBlank()) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = Tavan.colors.warning.strong,
                    modifier = Modifier.size(22.dp).background(Color.White, MaterialTheme.shapes.extraSmall).padding(2.dp)
                )
            }
        }
    }
}

/** The picture itself, decoded off the main thread and sized for where it is shown. */
@Composable
fun MediaPreview(media: MediaEntity, files: FileStore, edge: Int, modifier: Modifier = Modifier) {
    val isVideo = media.type == MediaType.VIDEO.code
    val bitmap by produceState<Bitmap?>(initialValue = null, media.id, edge) {
        value = withContext(Dispatchers.IO) {
            val file = files.resolve(media.filePath)
            if (isVideo) Thumbnails.forVideo(file) else Thumbnails.forPhoto(file, edge)
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val frame = bitmap
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = media.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.ImageNotSupported,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp)
            )
        }
        if (isVideo) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
