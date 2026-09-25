package ir.roozban.ai.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import ir.roozban.ai.core.audio.Pcm
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Hosts the model in its own process (`:ai`), so a model that exhausts memory takes down only
 * this process, never the app. All native work runs on one worker thread.
 */
class LlmService : Service() {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "llm").apply { priority = Thread.NORM_PRIORITY } }
    private val handle = AtomicLong(0)
    private var speech = 0L
    private var speechPath: String? = null

    @Volatile
    private var available: Boolean? = null

    private fun <T> onWorker(block: () -> T): T = worker.submit(block).get()

    private fun ensureInit(): Boolean {
        available?.let { return it }
        val ok = LlamaNative.loadError == null && runCatching { LlamaNative.nativeInit(applicationInfo.nativeLibraryDir) }.getOrDefault(false)
        if (!ok) Log.w(TAG, "native engine unavailable: ${LlamaNative.loadError}")
        available = ok
        return ok
    }

    private val binder = object : ILlmService.Stub() {
        override fun available(): Boolean = onWorker { ensureInit() }

        override fun load(path: String, contextTokens: Int, threads: Int, batchTokens: Int): String? = onWorker {
            if (!ensureInit()) return@onWorker "unsupported device"
            freeModel()
            val h = LlamaNative.nativeLoad(path, contextTokens, threads, batchTokens)
            if (h == 0L) LlamaNative.nativeLastError() else null.also { handle.set(h) }
        }

        override fun countTokens(text: String): Int = onWorker {
            val h = handle.get()
            if (h == 0L) -1 else LlamaNative.nativeCountTokens(h, text)
        }

        override fun generate(
            prompt: String,
            grammar: String?,
            temperature: Float,
            topP: Float,
            minP: Float,
            seed: Int,
            maxTokens: Int,
            callback: ILlmCallback,
        ) {
            worker.execute {
                val h = handle.get()
                if (h == 0L) {
                    callback.safe { onError("no model loaded") }
                    return@execute
                }
                var alive = true
                val sink = object : LlamaNative.PieceSink {
                    override fun onPiece(bytes: ByteArray): Boolean {
                        try {
                            callback.onPiece(String(bytes, Charsets.UTF_8))
                        } catch (e: RemoteException) {
                            alive = false // The app went away: stop.
                        }
                        return alive
                    }

                    override fun onProgress(percent: Int) {
                        callback.safe { onProgress(percent) }
                    }
                }
                val start = android.os.SystemClock.elapsedRealtime()
                val produced = LlamaNative.nativeGenerate(h, prompt, grammar?.ifEmpty { null }, temperature, topP, minP, seed, maxTokens, sink)
                Log.i(TAG, "generated $produced tokens in ${android.os.SystemClock.elapsedRealtime() - start} ms")
                if (produced < 0) callback.safe { onError(LlamaNative.nativeLastError()) } else callback.safe { onDone(produced) }
            }
        }

        override fun warmUp(prefix: String, cachePath: String, callback: ILlmCallback) {
            worker.execute {
                val h = handle.get()
                if (h == 0L) {
                    callback.safe { onError("no model loaded") }
                    return@execute
                }
                val sink = object : LlamaNative.PieceSink {
                    override fun onPiece(bytes: ByteArray) = true

                    override fun onProgress(percent: Int) {
                        callback.safe { onProgress(percent) }
                    }
                }
                val start = android.os.SystemClock.elapsedRealtime()
                val rc = LlamaNative.nativeWarmUp(h, prefix, cachePath, sink)
                Log.i(TAG, "warm-up rc=$rc in ${android.os.SystemClock.elapsedRealtime() - start} ms")
                if (rc < 0) callback.safe { onError(LlamaNative.nativeLastError()) } else callback.safe { onDone(rc) }
            }
        }

        override fun transcribe(modelPath: String, pcmPath: String, prompt: String?, threads: Int): String? = onWorker {
            if (!ensureInit()) return@onWorker null.also { lastSpeechError = "unsupported device" }
            if (speechPath != modelPath) {
                if (speech != 0L) LlamaNative.nativeSpeechFree(speech)
                speech = LlamaNative.nativeSpeechLoad(modelPath)
                speechPath = if (speech != 0L) modelPath else null
                if (speech == 0L) return@onWorker null.also { lastSpeechError = LlamaNative.nativeLastError() }
            }
            val pcm = Pcm.readRaw(File(pcmPath))
            val start = android.os.SystemClock.elapsedRealtime()
            val text = LlamaNative.nativeTranscribe(speech, pcm, "fa", prompt.orEmpty(), threads)
            Log.i(TAG, "transcribed ${pcm.size / 16} ms of audio in ${android.os.SystemClock.elapsedRealtime() - start} ms")
            if (text == null) lastSpeechError = LlamaNative.nativeLastError()
            text
        }

        override fun lastError(): String = lastSpeechError ?: (if (LlamaNative.loadError == null) LlamaNative.nativeLastError() else "unsupported device")

        // Runs on the binder thread so it can interrupt the worker.
        override fun cancel() {
            val h = handle.get()
            if (h != 0L) LlamaNative.nativeCancel(h)
        }

        override fun unload() = onWorker { freeModel() }
    }

    @Volatile
    private var lastSpeechError: String? = null

    private fun freeModel() {
        val h = handle.getAndSet(0)
        if (h != 0L) LlamaNative.nativeFree(h)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        worker.execute {
            freeModel()
            if (speech != 0L) LlamaNative.nativeSpeechFree(speech)
            speech = 0L
        }
        worker.shutdown()
        super.onDestroy()
    }

    private inline fun ILlmCallback.safe(block: ILlmCallback.() -> Unit) {
        try {
            block()
        } catch (_: RemoteException) {
        }
    }

    private companion object {
        const val TAG = "LlmService"
    }
}
