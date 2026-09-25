package ir.roozban.ai.models

import ir.roozban.ai.core.ChatTemplate
import ir.roozban.ai.core.DeviceTier

enum class ModelKind {
    /** A language model (GGUF) for the assistant. */
    LLM,

    /** A whisper.cpp speech model for voice input. */
    SPEECH,
}

/** A downloadable model. [sha256] and [sizeBytes] are of the exact file at [url]. */
data class ModelSpec(
    val id: String,
    val name: String,
    /** One line in Persian for the picker. */
    val description: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val template: ChatTemplate,
    /** The smallest tier that can run it. */
    val minTier: DeviceTier,
    val license: String,
    val kind: ModelKind = ModelKind.LLM,
)

object ModelCatalog {
    private const val HF = "https://huggingface.co"

    val all: List<ModelSpec> = listOf(
        ModelSpec(
            id = "qwen3-1.7b-q4km",
            name = "Qwen3 1.7B",
            description = "پیشنهادی: تعادل خوب سرعت و دقت",
            url = "$HF/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            sizeBytes = 1_107_409_472L,
            sha256 = "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",
            template = ChatTemplate.CHATML_NO_THINK,
            minTier = DeviceTier.STANDARD,
            license = "Apache-2.0",
        ),
        ModelSpec(
            id = "qwen3-0.6b-q4km",
            name = "Qwen3 0.6B",
            description = "سبک و سریع، برای گوشی‌های ضعیف‌تر",
            url = "$HF/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
            sizeBytes = 396_705_472L,
            sha256 = "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a",
            template = ChatTemplate.CHATML_NO_THINK,
            minTier = DeviceTier.LIGHT,
            license = "Apache-2.0",
        ),
        ModelSpec(
            id = "gemma3-1b-q4km",
            name = "Gemma 3 1B",
            description = "سبک، فارسی روان‌تر در پاسخ‌ها",
            url = "$HF/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            sizeBytes = 806_058_272L,
            sha256 = "8270790f3ab69fdfe860b7b64008d9a19986d8df7e407bb018184caa08798ebd",
            template = ChatTemplate.GEMMA,
            minTier = DeviceTier.LIGHT,
            license = "Gemma",
        ),
        ModelSpec(
            id = "qwen3-4b-2507-q4km",
            name = "Qwen3 4B Instruct",
            description = "دقیق‌ترین، برای گوشی‌های ۸ گیگ به بالا",
            url = "$HF/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
            sizeBytes = 2_497_281_120L,
            sha256 = "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
            template = ChatTemplate.CHATML,
            minTier = DeviceTier.HIGH,
            license = "Apache-2.0",
        ),
        ModelSpec(
            id = "gemma3-4b-q4km",
            name = "Gemma 3 4B",
            description = "فارسی بهتر، برای گوشی‌های ۸ گیگ به بالا",
            url = "$HF/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
            sizeBytes = 2_489_894_016L,
            sha256 = "04a43a22e8d2003deda5acc262f68ec1005fa76c735a9962a8c77042a74a7d19",
            template = ChatTemplate.GEMMA,
            minTier = DeviceTier.HIGH,
            license = "Gemma",
        ),
    )

    private const val WHISPER = "$HF/ggerganov/whisper.cpp/resolve/main"

    /** Speech models: whisper.cpp, Persian quality grows with size. */
    val speech: List<ModelSpec> = listOf(
        ModelSpec(
            id = "whisper-small-q5",
            name = "Whisper Small",
            description = "پیشنهادی: دقت خوب فارسی با سرعت مناسب",
            url = "$WHISPER/ggml-small-q5_1.bin",
            sizeBytes = SPEECH_SMALL_SIZE,
            sha256 = SPEECH_SMALL_SHA,
            template = ChatTemplate.CHATML,
            minTier = DeviceTier.LIGHT,
            license = "MIT",
            kind = ModelKind.SPEECH,
        ),
        ModelSpec(
            id = "whisper-base-q5",
            name = "Whisper Base",
            description = "سبک و سریع، دقت کمتر",
            url = "$WHISPER/ggml-base-q5_1.bin",
            sizeBytes = SPEECH_BASE_SIZE,
            sha256 = SPEECH_BASE_SHA,
            template = ChatTemplate.CHATML,
            minTier = DeviceTier.LIGHT,
            license = "MIT",
            kind = ModelKind.SPEECH,
        ),
        ModelSpec(
            id = "whisper-turbo-q5",
            name = "Whisper Large v3 Turbo",
            description = "بهترین دقت فارسی، کندتر؛ برای گوشی‌های قوی",
            url = "$WHISPER/ggml-large-v3-turbo-q5_0.bin",
            sizeBytes = SPEECH_TURBO_SIZE,
            sha256 = SPEECH_TURBO_SHA,
            template = ChatTemplate.CHATML,
            minTier = DeviceTier.HIGH,
            license = "MIT",
            kind = ModelKind.SPEECH,
        ),
    )

    fun get(id: String): ModelSpec? = all.firstOrNull { it.id == id } ?: speech.firstOrNull { it.id == id }

    /** Assistant models the device can run, the recommended one first. */
    fun availableFor(tier: DeviceTier): List<ModelSpec> =
        if (!tier.supported) emptyList() else all.filter { it.minTier <= tier && it.sizeBytes <= tier.maxModelBytes }
            .sortedBy { if (it.id == recommendedIdFor(tier)) 0 else 1 }

    /** Speech models the device can run, the recommended one first. Voice input works on every tier. */
    fun speechFor(tier: DeviceTier): List<ModelSpec> =
        speech.filter { it.minTier <= tier || tier == DeviceTier.UNSUPPORTED && it.id == "whisper-base-q5" }
            .sortedBy { if (it.id == recommendedSpeechIdFor(tier)) 0 else 1 }

    fun recommendedSpeechIdFor(tier: DeviceTier): String = if (tier <= DeviceTier.LIGHT) "whisper-base-q5" else "whisper-small-q5"

    fun recommendedIdFor(tier: DeviceTier): String? = when (tier) {
        DeviceTier.UNSUPPORTED -> null
        DeviceTier.LIGHT -> "qwen3-0.6b-q4km"
        DeviceTier.STANDARD, DeviceTier.HIGH -> "qwen3-1.7b-q4km"
    }
}

// Filled from the files' LFS metadata (x-linked-size / x-linked-etag).
private const val SPEECH_SMALL_SIZE = 190_085_487L
private const val SPEECH_SMALL_SHA = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"
private const val SPEECH_BASE_SIZE = 59_707_625L
private const val SPEECH_BASE_SHA = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
private const val SPEECH_TURBO_SIZE = 574_041_195L
private const val SPEECH_TURBO_SHA = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2"
