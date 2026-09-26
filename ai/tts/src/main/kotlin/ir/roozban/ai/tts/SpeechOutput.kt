package ir.roozban.ai.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.ai.core.audio.SpeechText
import ir.roozban.ai.models.ModelCatalog
import ir.roozban.ai.models.VoiceStore
import ir.roozban.ai.runtime.ModelManager
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.SpeechSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What is being read aloud; [id] tells a screen whether it is its own text. */
sealed interface SpeakState {
    data object Idle : SpeakState

    data class Preparing(val id: String) : SpeakState

    data class Speaking(val id: String) : SpeakState

    /** No voice: the phone has no Persian voice and none was downloaded. */
    data object NoVoice : SpeakState
}

/** A voice the user can pick. [key] is what [SpeechSettings.voice] stores. */
data class VoiceOption(val key: String, val name: String, val system: Boolean)

/**
 * Reading aloud, fully on the phone. Two kinds of voices:
 * - Roozban's downloadable Persian Piper voices, run with sherpa-onnx;
 * - the phone's own text-to-speech engine, when it has an offline Persian voice.
 * The chosen voice is used when available, otherwise the first that is. Text is read sentence
 * by sentence (the next one is synthesized while the current one plays), so there is no length
 * limit and speech starts quickly.
 */
@Singleton
class SpeechOutput @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val models: ModelManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<SpeakState>(SpeakState.Idle)
    val state: StateFlow<SpeakState> = _state.asStateFlow()

    private var job: Job? = null
    private var releaseJob: Job? = null
    private val piperLock = Mutex()
    private var piper: Pair<String, OfflineTts>? = null
    private var system: TextToSpeech? = null
    private val audio = context.getSystemService(AudioManager::class.java)

    /** Reads [text]; anything already being read stops first. [voiceKey] overrides the setting (previews). */
    fun speak(text: String, id: String = DEFAULT_ID, voiceKey: String? = null) {
        val chunks = SpeechText.chunks(text)
        stop()
        if (chunks.isEmpty()) return
        job = scope.launch {
            _state.value = SpeakState.Preparing(id)
            val prefs = settings.current().speech
            val focus = requestFocus()
            try {
                when (val voice = resolve(voiceKey ?: prefs.voice)) {
                    null -> {
                        _state.value = SpeakState.NoVoice
                        return@launch
                    }
                    is Resolved.Piper -> speakPiper(voice.id, chunks, prefs.rate, id)
                    is Resolved.System -> speakSystem(voice.name, chunks, prefs.rate, id)
                }
                _state.value = SpeakState.Idle
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "speech failed", e)
                _state.value = SpeakState.Idle
            } finally {
                abandonFocus(focus)
                scheduleRelease()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        system?.stop()
        if (_state.value !is SpeakState.NoVoice) _state.value = SpeakState.Idle
    }

    fun clearNoVoice() {
        if (_state.value is SpeakState.NoVoice) _state.value = SpeakState.Idle
    }

    /** Offline Persian voices of the phone's engine (network voices are left out: text stays on the phone). */
    suspend fun systemVoices(): List<VoiceOption> {
        val tts = systemEngine() ?: return emptyList()
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language in PERSIAN && !it.isNetworkConnectionRequired && Voice.FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedBy { it.name }
        if (voices.isNotEmpty()) return voices.mapIndexed { i, v -> VoiceOption("system:${v.name}", "صدای گوشی ${persian(i + 1)}", system = true) }
        val lang = runCatching { tts.isLanguageAvailable(Locale("fa", "IR")) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        return if (lang >= TextToSpeech.LANG_AVAILABLE) listOf(VoiceOption("system:", "صدای گوشی", system = true)) else emptyList()
    }

    /** Roozban voices on the phone. */
    fun piperVoices(): List<VoiceOption> = ModelCatalog.voices.filter { it.id in models.voices.installed() }
        .map { VoiceOption("piper:${it.id}", it.name, system = false) }

    private sealed interface Resolved {
        data class Piper(val id: String) : Resolved

        data class System(val name: String) : Resolved
    }

    private suspend fun resolve(key: String?): Resolved? {
        val installed = models.voices.installed()
        when {
            key?.startsWith("piper:") == true -> key.removePrefix("piper:").takeIf { it in installed }?.let { return Resolved.Piper(it) }
            key?.startsWith("system:") == true -> {
                val name = key.removePrefix("system:")
                if (systemVoices().any { it.key == key }) return Resolved.System(name)
            }
        }
        ModelCatalog.voices.firstOrNull { it.id in installed }?.let { return Resolved.Piper(it.id) }
        return systemVoices().firstOrNull()?.let { Resolved.System(it.key.removePrefix("system:")) }
    }

    // --- Piper (sherpa-onnx) ---

    private suspend fun piperEngine(id: String): OfflineTts = piperLock.withLock {
        releaseJob?.cancel()
        piper?.let { (loaded, tts) -> if (loaded == id) return@withLock tts else tts.release() }
        piper = null
        val store: VoiceStore = models.voices
        val dir = store.modelDir(id)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(dir, VoiceStore.MODEL).path,
                    tokens = File(dir, VoiceStore.TOKENS).path,
                    dataDir = store.espeakDir.path,
                ),
                numThreads = THREADS,
            ),
        )
        val tts = withContext(Dispatchers.IO) { OfflineTts(config = config) }
        piper = id to tts
        tts
    }

    private suspend fun speakPiper(voiceId: String, chunks: List<String>, rate: Float, id: String) = coroutineScope {
        val tts = piperEngine(voiceId)
        val sampleRate = tts.sampleRate()
        val track = AudioTrack.Builder()
            .setAudioAttributes(ATTRIBUTES)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4)
            .build()
        // Synthesis runs one sentence ahead of playback.
        val pieces = Channel<FloatArray>(capacity = 1)
        launch(Dispatchers.Default) {
            try {
                for (chunk in chunks) {
                    ensureActive()
                    pieces.send(tts.generate(chunk, sid = 0, speed = rate).samples)
                }
            } finally {
                pieces.close()
            }
        }
        var frames = 0L
        try {
            var started = false
            for (samples in pieces) {
                if (!started) {
                    track.play()
                    _state.value = SpeakState.Speaking(id)
                    started = true
                }
                var offset = 0
                while (offset < samples.size) {
                    ensureActive()
                    val n = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (n <= 0) break
                    offset += n
                }
                frames += samples.size
                // A short pause between sentences.
                val gap = FloatArray(sampleRate / 6)
                track.write(gap, 0, gap.size, AudioTrack.WRITE_BLOCKING)
                frames += gap.size
            }
            // Let the last buffer play out.
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < frames) {
                ensureActive()
                delay(50)
            }
        } finally {
            runCatching {
                track.pause()
                track.flush()
                track.release()
            }
        }
    }

    // --- The phone's engine ---

    private suspend fun systemEngine(): TextToSpeech? {
        system?.let { return it }
        return withContext(Dispatchers.Main) {
            system ?: suspendCancellableCoroutine { cont ->
                var engine: TextToSpeech? = null
                engine = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        system = engine
                        if (cont.isActive) cont.resume(engine)
                    } else {
                        engine?.shutdown()
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
    }

    private suspend fun speakSystem(voiceName: String, chunks: List<String>, rate: Float, id: String) {
        val tts = systemEngine() ?: return
        val voice = runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == voiceName }
        if (voice != null) tts.voice = voice else tts.language = Locale("fa", "IR")
        tts.setSpeechRate(rate)
        val done = CompletableDeferred<Unit>()
        val last = "$id-${chunks.size - 1}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = SpeakState.Speaking(id)
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == last) done.complete(Unit)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                done.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                done.complete(Unit)
            }
        })
        val params = Bundle()
        chunks.forEachIndexed { i, chunk -> tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, "$id-$i") }
        try {
            done.await()
        } finally {
            if (!done.isCompleted) tts.stop()
        }
    }

    // --- Audio focus and cleanup ---

    private fun requestFocus(): AudioFocusRequest? {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(ATTRIBUTES).build()
        return runCatching { audio?.requestAudioFocus(request); request }.getOrNull()
    }

    private fun abandonFocus(request: AudioFocusRequest?) {
        if (request != null) runCatching { audio?.abandonAudioFocusRequest(request) }
    }

    /** The voice model takes memory: it is freed a minute after the last sentence. */
    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(RELEASE_AFTER_MS)
            piperLock.withLock {
                piper?.second?.release()
                piper = null
            }
        }
    }

    private fun persian(n: Int) = n.toString().map { '۰' + (it - '0') }.joinToString("")

    companion object {
        const val DEFAULT_ID = "speech"
        private const val TAG = "RoozbanSpeech"
        private const val THREADS = 2
        private const val RELEASE_AFTER_MS = 60_000L
        private val PERSIAN = setOf("fa", "fas", "per")
        private val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
