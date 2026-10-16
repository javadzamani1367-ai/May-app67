package ir.ilam.inspection.field.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * A gallery video re-encoded for sending: 720p, H.264 at about 2 Mbit/s, AAC
 * sound, and no longer than [MAX_SECONDS] (as the app's own clips). A two-
 * minute 4K clip of several hundred megabytes comes out at a few tens.
 */
@OptIn(UnstableApi::class)
object VideoShrinker {

    const val MAX_SECONDS = 90L
    private const val HEIGHT = 720
    private const val BITRATE = 2_000_000

    /** True when [target] holds the shrunk video. Runs on the main thread, as Transformer requires. */
    suspend fun shrink(context: Context, uri: Uri, target: File): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(BITRATE).build())
                        .build()
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(target.length() > 0)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        target.delete()
                        if (continuation.isActive) continuation.resume(false)
                    }
                })
                .build()
            val item = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setEndPositionMs(MAX_SECONDS * 1000).build()
                )
                .build()
            val edited = EditedMediaItem.Builder(item)
                .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(HEIGHT))))
                .build()
            runCatching { transformer.start(edited, target.absolutePath) }.onFailure {
                if (continuation.isActive) continuation.resume(false)
            }
            continuation.invokeOnCancellation {
                runCatching { transformer.cancel() }
                target.delete()
            }
        }
    }
}
