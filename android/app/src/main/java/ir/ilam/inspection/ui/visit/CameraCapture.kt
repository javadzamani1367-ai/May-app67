package ir.ilam.inspection.ui.visit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ir.ilam.inspection.R
import java.io.File
import java.util.concurrent.Executors

private const val TAG = "CameraCapture"
private const val MAX_VIDEO_MILLIS = 90_000L
private const val MAX_VIDEO_BYTES = 60L * 1024 * 1024

/**
 * CameraX capture surface. Photos land in a temporary file that the caller
 * downscales and stamps; video is recorded at 720p and capped at 90 seconds.
 */
@SuppressLint("MissingPermission")
@Composable
fun CameraCapture(
    photoTarget: () -> File,
    videoTarget: () -> File,
    onPhoto: (File) -> Unit,
    onVideo: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingActive by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    videoCapture
                )
            }.onFailure { Log.e(TAG, "camera bind failed", it) }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            recording?.stop()
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val target = photoTarget()
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(target).build(),
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onPhoto(target)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "photo capture failed", exception)
                            }
                        }
                    )
                },
                enabled = !recordingActive,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.media_take_photo))
            }
            Button(
                onClick = {
                    if (recordingActive) {
                        recording?.stop()
                        recording = null
                        recordingActive = false
                    } else {
                        val target = videoTarget()
                        recording = startRecording(context, videoCapture, target, executor) {
                            recordingActive = false
                            onVideo(target)
                        }
                        recordingActive = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.media_record_video))
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    target: File,
    executor: java.util.concurrent.Executor,
    onFinished: () -> Unit
): Recording {
    val options = FileOutputOptions.Builder(target)
        .setFileSizeLimit(MAX_VIDEO_BYTES)
        .setDurationLimitMillis(MAX_VIDEO_MILLIS)
        .build()
    return videoCapture.output
        .prepareRecording(context, options)
        .withAudioEnabled()
        .start(executor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) Log.e(TAG, "video capture error ${event.error}")
                onFinished()
            }
        }
}

/** The permissions the capture surface needs before it can be shown. */
val CAPTURE_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
