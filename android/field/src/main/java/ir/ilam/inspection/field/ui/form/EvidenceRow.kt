package ir.ilam.inspection.field.ui.form

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.ui.capture.CAPTURE_PERMISSIONS
import ir.ilam.inspection.ui.capture.CameraCapture
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.PersianNumbers

/**
 * One kind of evidence for one part of a form — "photo of the clamp meter",
 * "video of the reading", "photo of the building" — from the camera or the
 * gallery. The files are the item's; this part keeps their capture [times],
 * so [onAdded] and [onRemoved] say which ones are its.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvidenceRow(
    editor: ItemEditor,
    title: String,
    times: List<Long>,
    video: Boolean,
    stampCode: String,
    place: Fix?,
    onAdded: (List<Long>) -> Unit,
    onRemoved: (Long) -> Unit
) {
    val context = LocalContext.current
    var capturing by remember { mutableStateOf(false) }
    val added by rememberUpdatedState(onAdded)
    val permissions = if (video) CAPTURE_PERMISSIONS else arrayOf(Manifest.permission.CAMERA)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { granted -> granted }) capturing = true
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) editor.importGallery(context, uris) { added(it) }
    }
    // A camera photo is a pair (original and stamped copy): its stamped copy stands for both.
    val tiles = editor.files.filter { file ->
        file.capturedAt in times && when (file.role) {
            FileRole.VIDEO -> video
            FileRole.PHOTO_STAMPED -> !video
            FileRole.PHOTO -> !video && editor.files.none { it.role == FileRole.PHOTO_STAMPED && it.capturedAt == file.capturedAt }
            else -> false
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Text(
            stringResource(R.string.evidence_count, title, PersianNumbers.toPersian(tiles.size)),
            style = MaterialTheme.typography.titleSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
            SecondaryButton(
                text = stringResource(R.string.evidence_camera),
                icon = if (video) Icons.Filled.Videocam else Icons.Filled.PhotoCamera,
                onClick = {
                    val granted = permissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (granted) capturing = true else permission.launch(permissions)
                },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = stringResource(R.string.form_gallery),
                icon = Icons.Filled.AddPhotoAlternate,
                onClick = {
                    val type = if (video) ActivityResultContracts.PickVisualMedia.VideoOnly
                    else ActivityResultContracts.PickVisualMedia.ImageOnly
                    gallery.launch(PickVisualMediaRequest(type))
                },
                modifier = Modifier.weight(1f)
            )
        }
        if (tiles.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                tiles.forEach { file ->
                    MediaTile(file, onRemove = {
                        editor.remove(file)
                        file.capturedAt?.let(onRemoved)
                    })
                }
            }
        }
    }
    if (capturing) {
        Dialog(onDismissRequest = { capturing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CameraCapture(
                canTakePhoto = !video,
                canRecordVideo = video,
                photoTarget = { editor.newFile("jpg") },
                videoTarget = { editor.newFile("mp4") },
                onPhoto = { raw -> editor.addCameraPhoto(raw, stampCode, place) { at -> added(listOf(at)) } },
                onVideo = { file -> editor.addVideo(file) { at -> added(listOf(at)) } },
                onClose = { capturing = false }
            )
        }
    }
}

private const val MAX_PICK = 10
