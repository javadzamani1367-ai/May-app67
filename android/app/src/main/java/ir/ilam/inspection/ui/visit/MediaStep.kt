package ir.ilam.inspection.ui.visit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.AutoSave
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.PhotoStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Step 5 — photos, video and the narrative that closes the visit. */
@Composable
fun MediaStep(detail: ReportDetail, viewModel: VisitViewModel) {
    val context = LocalContext.current
    val appContainer = context.container
    val scope = rememberCoroutineScope()
    val report = detail.report
    var capturing by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<Int?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == true) {
            capturing = true
        } else {
            permissionMessage = R.string.camera_permission_needed
        }
    }

    if (capturing) {
        CameraCapture(
            photoTarget = { appContainer.fileStore.newMediaFile(report.id, "raw.jpg") },
            videoTarget = { appContainer.fileStore.newMediaFile(report.id, "mp4") },
            onPhoto = { raw ->
                scope.launch {
                    val stored = appContainer.fileStore.newMediaFile(report.id, "jpg")
                    val capturedAt = System.currentTimeMillis()
                    val quality = appContainer.settingsRepository.mediaQuality()
                    val ok = withContext(Dispatchers.IO) {
                        appContainer.mediaProcessor.processPhoto(
                            source = raw,
                            target = stored,
                            stamp = PhotoStamp(
                                trackingCode = report.displayCode.orEmpty(),
                                expertCode = report.expertCode.orEmpty(),
                                capturedAt = capturedAt,
                                latitude = report.latitude,
                                longitude = report.longitude
                            ),
                            quality = quality
                        ).also { raw.delete() }
                    }
                    if (ok) viewModel.addMedia(stored, MediaType.IMAGE, capturedAt)
                }
            },
            onVideo = { file ->
                viewModel.addMedia(file, MediaType.VIDEO, System.currentTimeMillis())
            },
            onClose = { capturing = false }
        )
        return
    }

    SectionCard(title = stringResource(R.string.media_title)) {
        Column {
            Text(
                text = stringResource(
                    R.string.media_count,
                    PersianNumbers.toPersian(detail.media.size)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            detail.media.forEach { media ->
                var caption by remember(media.id) { mutableStateOf(media.caption.orEmpty()) }
                AutoSave(caption) { viewModel.setCaption(media, caption) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = PersianDate.formatWithTime(media.capturedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppTextField(
                            label = stringResource(R.string.media_caption),
                            value = caption,
                            onValueChange = { caption = it }
                        )
                    }
                    IconButton(onClick = { viewModel.removeMedia(media) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            }
            if (detail.media.isEmpty()) {
                Text(
                    text = stringResource(R.string.media_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            permissionMessage?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
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
            MultilineField(
                label = stringResource(R.string.field_description),
                value = description,
                onValueChange = { description = it }
            )
            MultilineField(
                label = stringResource(R.string.field_actions_taken),
                value = actions,
                onValueChange = { actions = it }
            )
        }
    }
}
