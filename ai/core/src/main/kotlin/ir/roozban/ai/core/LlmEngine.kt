package ir.roozban.ai.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A local language model. Implementations: the llama.cpp engine running in the `:ai` process and
 * [FakeLlmEngine] for tests. Only one model is loaded at a time.
 */
interface LlmEngine {
    val state: StateFlow<EngineState>

    /** Loads [path] (a GGUF file), replacing any loaded model. Throws [LlmException] on failure. */
    suspend fun load(path: String, config: EngineConfig)

    /**
     * Streams the generated text piece by piece. Cancelling the collection stops generation.
     * Throws [LlmException] when no model is loaded.
     */
    fun generate(request: GenerationRequest): Flow<String>

    /**
     * Pre-computes the fixed start of every prompt so each message only processes the rest.
     * With [cacheFile] the result is saved and later restored instead of recomputed.
     * Returns true when it was restored.
     */
    suspend fun warmUp(prefix: String, cacheFile: String?, onProgress: (Int) -> Unit = {}): Boolean {
        generate(GenerationRequest(prefix, maxTokens = 0, onPromptProgress = onProgress)).collect {}
        return false
    }

    /** Token count of [text] with the loaded model's tokenizer, or null when unavailable. */
    suspend fun countTokens(text: String): Int?

    suspend fun unload()
}

sealed interface EngineState {
    data object Unloaded : EngineState

    data class Loading(val path: String) : EngineState

    data class Ready(val path: String, val config: EngineConfig) : EngineState

    data class Failed(val message: String) : EngineState
}

data class EngineConfig(
    val contextTokens: Int = 4096,
    val threads: Int = 4,
    val batchTokens: Int = 512,
)

data class SamplingParams(
    val temperature: Float = 0.2f,
    val topP: Float = 0.9f,
    val minP: Float = 0.05f,
    val seed: Int = 42,
) {
    companion object {
        /** For tool calls: deterministic. */
        val Greedy = SamplingParams(temperature = 0f)
    }
}

data class GenerationRequest(
    /** The full prompt, already rendered with the model's chat template. */
    val prompt: String,
    /** GBNF grammar constraining the output, or null for free text. */
    val grammar: String? = null,
    val sampling: SamplingParams = SamplingParams.Greedy,
    val maxTokens: Int = 512,
    /** Called with 0..100 while the engine reads the prompt (before the first piece). */
    val onPromptProgress: ((Int) -> Unit)? = null,
)

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)
