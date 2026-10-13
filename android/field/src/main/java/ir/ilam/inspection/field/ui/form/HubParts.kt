package ir.ilam.inspection.field.ui.form

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.FileRole
import ir.ilam.inspection.field.data.db.FieldFileEntity
import ir.ilam.inspection.field.data.db.FieldItemEntity
import ir.ilam.inspection.ui.location.LivePositionPage
import ir.ilam.inspection.ui.location.formatMetres
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * Which page of a form is open; null is the hub. Survives the camera and
 * permission dialogs, and the phone's back button closes the page before it
 * closes the form.
 */
@Composable
fun rememberFormPage(): MutableState<String?> {
    val page = rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = page.value != null) { page.value = null }
    return page
}

/** The position tile every form has: recorded with its accuracy, picked on the map, or still missing. */
@Composable
fun positionSection(item: FieldItemEntity, required: Boolean = true, title: String = stringResource(R.string.hub_position_title)) =
    HubSection(
        key = PAGE_POSITION,
        title = title,
        summary = when {
            item.latitude == null || item.longitude == null ->
                if (required) stringResource(R.string.hub_position_missing) else stringResource(R.string.hub_empty)
            item.accuracy != null -> stringResource(R.string.hub_position_done, formatMetres(item.accuracy))
            else -> stringResource(R.string.hub_position_picked)
        },
        icon = Icons.Filled.MyLocation,
        tone = Tone.INFO,
        status = when {
            item.latitude != null && item.longitude != null -> SectionStatus.DONE
            required -> SectionStatus.MISSING
            else -> SectionStatus.OPTIONAL
        }
    )

/** The photos, videos and sound tile, counted the way the grid shows them: one per camera shot. */
@Composable
fun mediaSection(files: List<FieldFileEntity>, title: String = stringResource(R.string.form_media_title)): HubSection {
    val photos = files.filter { it.role == FileRole.PHOTO || it.role == FileRole.PHOTO_STAMPED }
        .distinctBy { it.capturedAt ?: it.id }.size
    val videos = files.count { it.role == FileRole.VIDEO }
    val audio = files.count { it.role == FileRole.AUDIO }
    val any = photos + videos + audio > 0
    return HubSection(
        key = PAGE_MEDIA,
        title = title,
        summary = if (any) stringResource(R.string.hub_media_summary, PersianNumbers.toPersian(photos),
            PersianNumbers.toPersian(videos), PersianNumbers.toPersian(audio)) else stringResource(R.string.hub_empty),
        icon = Icons.Filled.PhotoLibrary,
        tone = Tone.INFO,
        status = if (any) SectionStatus.DONE else SectionStatus.OPTIONAL
    )
}

/** The form's own position, on the live map page. */
@Composable
fun ItemPositionPage(editor: ItemEditor, item: FieldItemEntity, onBack: () -> Unit, title: String = stringResource(R.string.hub_position_title)) {
    LivePositionPage(
        title = title,
        recordedLatitude = item.latitude,
        recordedLongitude = item.longitude,
        onRecordFix = { fix, _ -> editor.setMeasured(fix) },
        onRecordPoint = { lat, lon -> editor.setPosition(lat, lon, null) },
        onBack = onBack
    )
}

/** Photos, video and sound on a page of their own. */
@Composable
fun MediaPage(editor: ItemEditor, stampCode: String, onBack: () -> Unit, allowVideo: Boolean = true, allowAudio: Boolean = true) {
    FormPage(title = stringResource(R.string.form_media_title), onBack = onBack) {
        MediaSection(editor, stampCode = stampCode, allowVideo = allowVideo, allowAudio = allowAudio)
    }
}

/** A line of text, cut to fit a tile. */
fun preview(text: String?, max: Int = 90): String? =
    text?.trim()?.replace('\n', ' ')?.takeIf { it.isNotEmpty() }?.let { if (it.length > max) it.take(max) + "…" else it }

const val PAGE_POSITION = "position"
const val PAGE_MEDIA = "media"
