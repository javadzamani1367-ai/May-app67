package ir.minerfinder.signature

import android.net.Uri
import ir.minerfinder.AppGraph
import ir.minerfinder.core.acoustic.AcousticFrame
import ir.minerfinder.core.signature.Signature
import ir.minerfinder.core.signature.SignatureFeatures
import ir.minerfinder.core.signature.SignatureRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** The best library match for what the microphone hears now. */
data class SignatureMatch(val signature: Signature, val score: Double)

/**
 * Stored signatures, recording new ones, live matching and file exchange.
 * Live matching compares a short running average of the spectral shape (about
 * two seconds), which is steadier than one frame.
 */
class SignatureLibrary(private val graph: AppGraph) {
    private val dao = graph.database.signatures()

    val signatures: StateFlow<List<Signature>> = dao.all()
        .map { list -> list.map(SignatureJson::fromEntity) }
        .stateIn(graph.scope, SharingStarted.Eagerly, emptyList())

    private val _match = MutableStateFlow<List<SignatureMatch>>(emptyList())
    /** All signatures scored against the current sound, best first. */
    val matches: StateFlow<List<SignatureMatch>> = _match.asStateFlow()

    private var average: DoubleArray? = null
    @Volatile private var recorder: SignatureRecorder? = null

    private val _recordedFrames = MutableStateFlow(0)
    val recordedFrames: StateFlow<Int> = _recordedFrames.asStateFlow()

    init {
        graph.scope.launch {
            graph.catalog.audio.frames.collect { frame ->
                if (frame == null) {
                    average = null
                    _match.value = emptyList()
                } else {
                    onFrame(frame)
                }
            }
        }
    }

    private fun onFrame(f: AcousticFrame) {
        val bands = SignatureFeatures.bands(f.cleanDb, f.floorDb, f.firstHz, f.binHz)
        recorder?.let {
            it.add(bands, f.series?.fundamentalHz, f.series?.partials?.map { p -> p.peak.freqHz } ?: emptyList())
            _recordedFrames.value = it.frames
        }
        val avg = average?.also { a -> for (i in a.indices) a[i] += 0.04 * (bands[i] - a[i]) } ?: bands.copyOf()
        average = avg
        val f0 = f.series?.fundamentalHz
        _match.value = signatures.value
            .map { SignatureMatch(it, SignatureFeatures.match(avg, f0, it)) }
            .sortedByDescending { it.score }
    }

    fun startRecording() {
        _recordedFrames.value = 0
        recorder = SignatureRecorder()
    }

    /** Stops recording and returns the signature, not yet saved; null if nothing was heard. */
    fun finishRecording(label: String, model: String, note: String): Signature? {
        val r = recorder ?: return null
        recorder = null
        return r.build(UUID.randomUUID().toString(), label, model, note, System.currentTimeMillis())
    }

    fun cancelRecording() {
        recorder = null
        _recordedFrames.value = 0
    }

    suspend fun save(signature: Signature) = dao.upsert(SignatureJson.toEntity(signature))

    suspend fun delete(id: String) = dao.delete(id)

    fun exportJson(only: Signature? = null): String = SignatureJson.write(only?.let { listOf(it) } ?: signatures.value)

    /** Imports a shared or picked file; returns how many signatures were added or updated. */
    suspend fun import(uri: Uri): Int {
        val text = graph.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalArgumentException("unreadable")
        return importText(text)
    }

    suspend fun importText(text: String): Int {
        val list = SignatureJson.read(text)
        list.forEach { save(it) }
        return list.size
    }
}

