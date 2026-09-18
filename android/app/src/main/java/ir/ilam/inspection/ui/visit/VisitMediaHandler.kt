package ir.ilam.inspection.ui.visit

import android.net.Uri
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.model.MediaCaptions
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.util.PhotoStamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything that turns a frame or a gallery file into a recorded photo: the
 * downscale, the stamp, the per-case limits. Kept apart from the wizard's own
 * state so neither file grows past reading size.
 */
class VisitMediaHandler(
    private val container: AppContainer,
    private val reportId: String,
    private val scope: CoroutineScope
) {

    private val reports = container.reportRepository
    private val content = container.contentRepository

    private val _notice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun reportPermissionRefused() {
        _notice.value = R.string.camera_permission_needed
    }

    /**
     * A captured frame is downscaled, stamped and only then recorded, so the
     * expert never ends up with a photo that carries no context.
     */
    fun storeCapturedPhoto(raw: File) {
        scope.launch {
            val report = reports.detail(reportId)?.report ?: return@launch
            if (photoCount() >= MediaCaptions.MAX_PHOTOS) {
                raw.delete()
                _notice.value = R.string.media_photo_limit
                return@launch
            }
            val stored = container.fileStore.newMediaFile(reportId, "jpg")
            val capturedAt = System.currentTimeMillis()
            val quality = container.settingsRepository.mediaQuality()
            val ok = withContext(Dispatchers.IO) {
                container.mediaProcessor.processPhoto(
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
            if (!ok) {
                _notice.value = R.string.camera_failed
                return@launch
            }
            content.addMedia(
                reportId = reportId,
                file = stored,
                type = MediaType.IMAGE,
                capturedAt = capturedAt,
                latitude = report.latitude,
                longitude = report.longitude
            )
            _notice.value = R.string.media_photo_saved_generic
        }
    }

    /** Brings gallery files in through the same pipeline as captured ones. */
    fun importFromGallery(uris: List<Uri>, onDone: (added: Int, rejected: Int) -> Unit) {
        scope.launch {
            val report = reports.detail(reportId)?.report ?: return@launch
            val quality = container.settingsRepository.mediaQuality()
            var added = 0
            var rejected = 0
            for (uri in uris) {
                val imported = withContext(Dispatchers.IO) {
                    container.mediaImporter.import(
                        uri = uri,
                        reportId = reportId,
                        stamp = PhotoStamp(
                            trackingCode = report.displayCode.orEmpty(),
                            expertCode = report.expertCode.orEmpty(),
                            capturedAt = System.currentTimeMillis(),
                            latitude = report.latitude,
                            longitude = report.longitude
                        ),
                        quality = quality
                    )
                }
                if (imported == null) {
                    rejected++
                    continue
                }
                val type = if (imported.isVideo) MediaType.VIDEO else MediaType.IMAGE
                val full = if (imported.isVideo) {
                    videoCount() >= MediaCaptions.MAX_VIDEOS
                } else {
                    photoCount() >= MediaCaptions.MAX_PHOTOS
                }
                if (full) {
                    imported.file.delete()
                    rejected++
                    continue
                }
                content.addMedia(
                    reportId = reportId,
                    file = imported.file,
                    type = type,
                    capturedAt = imported.capturedAt,
                    latitude = report.latitude,
                    longitude = report.longitude
                )
                added++
            }
            onDone(added, rejected)
        }
    }

    private suspend fun photoCount(): Int =
        reports.detail(reportId)?.media?.count { it.type == MediaType.IMAGE.code } ?: 0

    private suspend fun videoCount(): Int =
        reports.detail(reportId)?.media?.count { it.type == MediaType.VIDEO.code } ?: 0


    private suspend fun photoCount(): Int =
        reports.detail(reportId)?.media?.count { it.type == MediaType.IMAGE.code } ?: 0

    private suspend fun videoCount(): Int =
        reports.detail(reportId)?.media?.count { it.type == MediaType.VIDEO.code } ?: 0
}
