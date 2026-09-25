package ir.roozban.ai.models

import ir.roozban.ai.core.ChatTemplate
import ir.roozban.ai.core.DeviceTier

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

    fun get(id: String): ModelSpec? = all.firstOrNull { it.id == id }

    /** Models the device can run, the recommended one first. */
    fun availableFor(tier: DeviceTier): List<ModelSpec> =
        if (!tier.supported) emptyList() else all.filter { it.minTier <= tier && it.sizeBytes <= tier.maxModelBytes }
            .sortedBy { if (it.id == recommendedIdFor(tier)) 0 else 1 }

    fun recommendedIdFor(tier: DeviceTier): String? = when (tier) {
        DeviceTier.UNSUPPORTED -> null
        DeviceTier.LIGHT -> "qwen3-0.6b-q4km"
        DeviceTier.STANDARD, DeviceTier.HIGH -> "qwen3-1.7b-q4km"
    }
}
