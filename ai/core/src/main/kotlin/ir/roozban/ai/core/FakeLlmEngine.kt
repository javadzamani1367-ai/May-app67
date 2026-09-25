package ir.roozban.ai.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * A scripted engine for tests: [respond] maps a prompt to the text to "generate", which is emitted
 * in small pieces like a real model would.
 */
class FakeLlmEngine(
    private val pieceLength: Int = 4,
    private val pieceDelayMillis: Long = 0,
    var respond: (GenerationRequest) -> String = { "" },
) : LlmEngine {
    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    val requests = mutableListOf<GenerationRequest>()

    var failLoad: String? = null

    override suspend fun load(path: String, config: EngineConfig) {
        failLoad?.let {
            _state.value = EngineState.Failed(it)
            throw LlmException(it)
        }
        _state.value = EngineState.Ready(path, config)
    }

    override fun generate(request: GenerationRequest): Flow<String> = flow {
        if (_state.value !is EngineState.Ready) throw LlmException("no model loaded")
        requests += request
        respond(request).chunked(pieceLength).forEach {
            if (pieceDelayMillis > 0) delay(pieceDelayMillis)
            emit(it)
        }
    }

    override suspend fun countTokens(text: String): Int? = null

    override suspend fun unload() {
        _state.value = EngineState.Unloaded
    }
}
