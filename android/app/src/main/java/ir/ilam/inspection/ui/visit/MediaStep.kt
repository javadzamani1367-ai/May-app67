package ir.ilam.inspection.ui.visit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.MediaCaptions
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.AutoSave
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.data.repo.SnippetFields
import ir.ilam.inspection.ui.common.SnippetField

/** Step 5 — photos, video and the narrative that closes the visit. */
@Composable
fun MediaStep(detail: ReportDetail, viewModel: VisitViewModel) {
    val context = LocalContext.current
    val appContainer = context.container
    val report = detail.report
    val photoCaptions = stringArrayResource(R.array.photo_captions).toList()
    val videoCaptions = stringArrayResource(R.array.video_captions).toList()

    var capturing by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val photos = detail.media.filter { it.type == MediaType.IMAGE.code }
    val videos = detail.media.filter { it.type == MediaType.VIDEO.code }

    val photoLimit = stringResource(R.string.media_photo_limit, PersianNumbers.toPersian(MediaCaptions.MAX_PHOTOS))
    val videoLimit = stringResource(R.string.media_video_limit, PersianNumbers.toPersian(MediaCaptions.MAX_VIDEOS))
    val importing = stringResource(R.string.media_importing)
    val importFailed = stringResource(R.string.media_import_failed)
    val photoSaved = stringResource(R.string.media_photo_saved, PersianNumbers.toPersian(photos.size + 1))

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MediaCaptions.MAX_PHOTOS)
    ) { uris ->
        if (uris.isNotEmpty()) {
            notice = importing
            viewModel.importFromGallery(uris) { added, rejected ->
                notice = when {
                    rejected > 0 -> importFailed
                    added > 0 -> photoSaved
                    else -> null
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == true) {
            capturing = true
        } else {
            notice = null
            viewModel.reportPermissionRefused()
        }
    }

    // The camera fills the screen, and a component that fills its height cannot
    // live inside a scrolling column: Compose measures it with unbounded height
    // and throws. Its own window gives it real bounds.
    if (capturing) {
        Dialog(
            onDismissRequest = { capturing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CameraCapture(
                canTakePhoto = photos.size < MediaCaptions.MAX_PHOTOS,
                canRecordVideo = videos.size < MediaCaptions.MAX_VIDEOS,
                photoTarget = { appContainer.fileStore.newMediaFile(report.id, "raw.jpg") },
                videoTarget = { appContainer.fileStore.newMediaFile(report.id, "mp4") },
                onPhoto = { raw -> viewModel.storeCapturedPhoto(raw) },
                onVideo = { file -> viewModel.addMedia(file, MediaType.VIDEO, System.currentTimeMillis()) },
                onClose = { capturing = false }
            )
        }
    }

    SectionCard(title = stringResource(R.string.media_title)) {
        Column {
            Text(
                text = stringResource(
                    R.string.media_photos_count,
                    PersianNumbers.toPersian(photos.size),
                    PersianNumbers.toPersian(MediaCaptions.MAX_PHOTOS)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.media_videos_count,
                    PersianNumbers.toPersian(videos.size),
                    PersianNumbers.toPersian(MediaCaptions.MAX_VIDEOS)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            notice?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (photos.size >= MediaCaptions.MAX_PHOTOS) {
                Text(
                    text = photoLimit,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (videos.size >= MediaCaptions.MAX_VIDEOS) {
                Text(
                    text = videoLimit,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            detail.media.forEach { media ->
                val standard = if (media.type == MediaType.VIDEO.code) videoCaptions else photoCaptions
                val used = detail.media.filter { it.id != media.id }.map { it.caption }
                MediaRow(
                    media = media,
                    files = appContainer.fileStore,
                    standardCaptions = MediaCaptions.available(standard, used),
                    onCaptionChange = { viewModel.setCaption(media, it) },
                    onRemove = { viewModel.removeMedia(media) }
                )
            }
            if (detail.media.isEmpty()) {
                Text(
                    text = stringResource(R.string.media_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val granted = CAPTURE_PERMISSIONS.all {
                            ContextCompat.checkSelfPermission(context, it) ==
                                PackageManager.PERMISSION_GRANTED
                        }
                        if (granted) capturing = true else permissionLauncher.launch(CAPTURE_PERMISSIONS)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.media_take_photo))
                }
                OutlinedButton(
                    onClick = {
                        galleryPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.media_pick_gallery))
                }
            }
        }
    }

    NarrativeSection(detail, viewModel)
}

@Composable
private fun NarrativeSection(detail: ReportDetail, viewModel: VisitViewModel) {
    val report = detail.report
    var description by remember(report.id) { mutableStateOf(report.description.orEmpty()) }
    var actions by remember(report.id) { mutableStateOf(report.actionsTaken.orEmpty()) }

    AutoSave(listOf(description, actions)) {
        viewModel.setNarrative(description = description, actionsTaken = actions)
    }

    SectionCard(title = stringResource(R.string.field_description)) {
        Column {
            SnippetField(
                label = stringResource(R.string.field_description),
                value = description,
                onValueChange = { description = it },
                fieldKey = SnippetFields.VISIT_DESCRIPTION,
                multiline = true
            )
            SnippetField(
                label = stringResource(R.string.field_actions_taken),
                value = actions,
                onValueChange = { actions = it },
                fieldKey = SnippetFields.VISIT_ACTIONS,
                multiline = true
            )
        }
    }
}
