package ir.ilam.inspection.field.ui.form

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.field
import ir.ilam.inspection.ui.capture.CAPTURE_PERMISSIONS
import ir.ilam.inspection.ui.capture.CameraCapture
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photos, short videos and sound. A camera photo keeps its original and gets a
 * stamped copy; the grid shows one tile per capture. [allowAudio] is for the
 * reports, where the hum of the fans is evidence.
 */
@Composable
fun MediaSection(editor: ItemEditor, stampCode: String, allowVideo: Boolean = true, allowAudio: Boolean = true) {
    val context = LocalContext.current
    var capturing by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.release() } }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) editor.importPhotos(context, uris)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refused = it[Manifest.permission.CAMERA] != true
        if (!refused) capturing = true
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refused = !granted
        if (granted) recording = recorder.start(editor.newFile("m4a"))
    }
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    if (capturing) {
        Dialog(onDismissRequest = { capturing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CameraCapture(
                canTakePhoto = true,
                canRecordVideo = allowVideo,
                photoTarget = { editor.newFile("jpg") },
                videoTarget = { editor.newFile("mp4") },
                onPhoto = { editor.addCameraPhoto(it, stampCode) },
                onVideo = { editor.addVideo(it) },
                onClose = { capturing = false }
            )
        }
    }

    val tiles = editor.files.filter { file ->
        // One tile per camera photo: the stamped copy stands for the pair.
        !(file.role == FileRole.PHOTO && editor.files.any {
            it.role == FileRole.PHOTO_STAMPED && it.capturedAt == file.capturedAt
        })
    }
    SectionCard(
        title = stringResource(R.string.form_media_title),
        subtitle = stringResource(if (allowAudio) R.string.form_media_hint_audio else R.string.form_media_hint),
        icon = Icons.Filled.PhotoLibrary,
        tone = Tone.INFO
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                PrimaryButton(
                    text = stringResource(if (allowVideo) R.string.form_camera_video else R.string.form_camera),
                    icon = Icons.Filled.PhotoCamera,
                    onClick = { if (CAPTURE_PERMISSIONS.all(::granted)) capturing = true else cameraPermission.launch(CAPTURE_PERMISSIONS) },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.form_gallery),
                    icon = Icons.Filled.AddPhotoAlternate,
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (allowAudio) {
                SecondaryButton(
                    text = stringResource(if (recording) R.string.form_audio_stop else R.string.form_audio_start),
                    icon = if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    onClick = {
                        if (recording) {
                            recorder.stop()?.let(editor::addAudio)
                            recording = false
                        } else if (granted(Manifest.permission.RECORD_AUDIO)) {
                            recording = recorder.start(editor.newFile("m4a"))
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                )
            }
            if (refused) {
                Text(stringResource(R.string.form_permission_refused), color = Tavan.colors.danger.strong,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
            }
            Text(
                stringResource(R.string.form_media_count, PersianNumbers.toPersian(tiles.size)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            MediaGrid(tiles, onRemove = editor::remove)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaGrid(tiles: List<FieldFileEntity>, onRemove: (FieldFileEntity) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        tiles.forEach { file -> MediaTile(file, onRemove = { onRemove(file) }) }
    }
}

@Composable
private fun MediaTile(file: FieldFileEntity, onRemove: () -> Unit) {
    val store = LocalContext.current.field.files
    val photo = file.role == FileRole.PHOTO || file.role == FileRole.PHOTO_STAMPED
    val bitmap by produceState<android.graphics.Bitmap?>(null, file.id) {
        value = withContext(Dispatchers.IO) {
            val local = file.path?.let { store.resolve(it) } ?: return@withContext null
            when {
                photo -> Thumbnails.forPhoto(local, 240)
                file.role == FileRole.VIDEO -> Thumbnails.forVideo(local)
                else -> null
            }
        }
    }
    Box(
        modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (bitmap == null) {
            Icon(
                if (file.role == FileRole.AUDIO) Icons.Filled.GraphicEq else Icons.Filled.Videocam,
                contentDescription = null,
                tint = Tavan.colors.info.strong,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )
        } else if (file.role == FileRole.VIDEO) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.form_remove),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small))
        }
    }
}

private const val MAX_PICK = 10
