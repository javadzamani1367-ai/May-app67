package ir.ilam.inspection.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A small preview of a captured file. Shared by the media step and the dispatch
 * picker: in both places the expert has to recognise a photo, and a date alone
 * does not tell one photo from another.
 */
@Composable
fun MediaThumbnail(media: MediaEntity, files: FileStore, sizeDp: Int = 76) {
    val isVideo = media.type == MediaType.VIDEO.code
    val bitmap by produceState<Bitmap?>(initialValue = null, media.id) {
        value = withContext(Dispatchers.IO) {
            val file = files.resolve(media.filePath)
            if (isVideo) Thumbnails.forVideo(file) else Thumbnails.forPhoto(file, sizeDp * 3)
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = media.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp.dp)
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
