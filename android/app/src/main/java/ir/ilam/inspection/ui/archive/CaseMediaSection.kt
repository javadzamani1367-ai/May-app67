package ir.ilam.inspection.ui.archive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.ui.visit.MediaDetailDialog
import ir.ilam.inspection.ui.visit.MediaGrid
import ir.ilam.inspection.util.PersianNumbers

/** The photo appendix of the report, as a gallery; a tap shows one large with what is known about it. */
@Composable
fun CaseMediaSection(detail: ReportDetail) {
    val files = LocalContext.current.container.fileStore
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    SectionCard(
        title = stringResource(R.string.case_media_title),
        icon = Icons.Filled.PhotoLibrary,
        tone = Tone.INFO,
        trailing = {
            if (detail.media.isNotEmpty()) StatusBadge(PersianNumbers.toPersian(detail.media.size), tone = Tone.INFO)
        }
    ) {
        if (detail.media.isEmpty()) {
            Text(
                stringResource(R.string.media_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            MediaGrid(media = detail.media, files = files, onOpen = { opened = it.id })
        }
    }
    detail.media.firstOrNull { it.id == opened }?.let { media ->
        MediaDetailDialog(
            media = media,
            files = files,
            standardCaptions = emptyList(),
            onCaptionChange = {},
            onRemove = {},
            onDismiss = { opened = null },
            editable = false
        )
    }
}
