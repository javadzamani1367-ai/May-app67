package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.ui.common.ConfirmDeleteButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * One piece of evidence, large: the picture, what is known about it — when,
 * where, how big — its caption, and the way to remove it.
 */
@Composable
fun MediaDetailDialog(
    media: MediaEntity,
    files: FileStore,
    standardCaptions: List<String>,
    onCaptionChange: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    /** False on a finished case's page: the evidence is shown, not changed there. */
    editable: Boolean = true
) {
    val context = LocalContext.current
    val isVideo = media.type == MediaType.VIDEO.code
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            Column {
                TavanTopBar(
                    title = media.caption?.takeIf { it.isNotBlank() } ?: stringResource(R.string.media_detail_title),
                    subtitle = PersianDate.formatWithTime(media.capturedAt),
                    onBack = onDismiss
                ) {
                    if (editable) {
                        ConfirmDeleteButton(
                            itemName = media.caption,
                            onConfirm = {
                                onRemove()
                                onDismiss()
                            }
                        )
                    }
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.screen)
                ) {
                    MediaPreview(
                        media = media,
                        files = files,
                        edge = 1280,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large)
                    )
                    if (isVideo) {
                        SecondaryButton(
                            text = stringResource(R.string.media_play),
                            onClick = { ShareUtil.view(context, files.resolve(media.filePath)) },
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                        )
                    }
                    if (editable) {
                        MediaCaptionField(
                            caption = media.caption,
                            standardCaptions = standardCaptions,
                            onCaptionChange = onCaptionChange
                        )
                    }
                    ValueRow(
                        label = stringResource(R.string.media_captured_at),
                        value = PersianDate.formatWithTime(media.capturedAt)
                    )
                    ValueRow(
                        label = stringResource(R.string.media_position),
                        value = if (media.latitude != null && media.longitude != null) {
                            formatCoordinates(media.latitude, media.longitude)
                        } else null,
                        ltr = true
                    )
                    ValueRow(
                        label = stringResource(R.string.media_size),
                        value = stringResource(
                            R.string.media_size_value,
                            PersianNumbers.toPersian((media.sizeBytes / 1024).toInt())
                        )
                    )
                }
            }
        }
    }
}
