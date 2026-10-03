package ir.ilam.inspection.field.ui.form

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Sound recording for the hum of the fans — one of the signs that matters
 * most and that no photo shows. AAC in an MP4 container, which every phone
 * and every computer at the office plays.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    val recording: Boolean get() = recorder != null

    fun start(file: File): Boolean = runCatching {
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        created.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(44_100)
            setMaxDuration(MAX_MILLIS)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = created
        target = file
        true
    }.getOrElse {
        release()
        false
    }

    /** Stops and returns the finished file, or null if nothing usable was recorded. */
    fun stop(): File? {
        val file = target
        val ok = runCatching { recorder?.stop() }.isSuccess
        release()
        return file?.takeIf { ok && it.length() > 0 }
    }

    fun release() {
        runCatching { recorder?.release() }
        recorder = null
        target = null
    }

    companion object {
        const val MAX_MILLIS = 180_000
    }
}
