package ir.roozban.ai.core

/**
 * What the device can run, by total RAM. Phones report a little less than the marketed size
 * (a "4 GB" phone shows about 3.6 GB), so the thresholds sit below the round numbers.
 */
enum class DeviceTier(val maxModelBytes: Long, val contextTokens: Int) {
    /** Under 3 GB: the assistant is off; quick add keeps working with the rule-based parser. */
    UNSUPPORTED(0, 0),

    /** 3–4 GB: the smallest model with a short context. */
    LIGHT(850L * 1024 * 1024, 4096),

    /** 4–8 GB: the default model. */
    STANDARD(1_600L * 1024 * 1024, 4096),

    /** 8 GB or more: larger models are offered too. */
    HIGH(3_000L * 1024 * 1024, 4096),
    ;

    val supported: Boolean get() = this != UNSUPPORTED

    companion object {
        const val MB = 1024L * 1024

        const val GB = 1024 * MB

        fun of(totalRamBytes: Long): DeviceTier = when {
            totalRamBytes < 2_800L * MB -> UNSUPPORTED
            totalRamBytes < 3_700L * 1024 * 1024 -> LIGHT
            totalRamBytes < 7_400L * MB -> STANDARD
            else -> HIGH
        }

        /** Big cores do the work; using every core slows llama.cpp down on phones. */
        fun threadsFor(cpuCount: Int): Int = (cpuCount / 2).coerceIn(2, 6)
    }
}
