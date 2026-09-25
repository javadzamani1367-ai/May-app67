package ir.roozban.ai.runtime

/** JNI entry points of `libroozban_llm.so`. Used only inside the `:ai` process. */
internal object LlamaNative {
    /** Null when the library loaded; otherwise why not (e.g. an unsupported ABI). */
    val loadError: String? = try {
        System.loadLibrary("roozban_llm")
        null
    } catch (e: UnsatisfiedLinkError) {
        e.message ?: "native library missing"
    }

    interface PieceSink {
        /** A complete UTF-8 piece; return false to stop. */
        fun onPiece(bytes: ByteArray): Boolean

        /** 0..100 while the prompt is being read. */
        fun onProgress(percent: Int)
    }

    external fun nativeInit(libDir: String): Boolean

    external fun nativeLastError(): String

    external fun nativeLoad(path: String, contextTokens: Int, threads: Int, batchTokens: Int): Long

    external fun nativeFree(handle: Long)

    external fun nativeCountTokens(handle: Long, text: String): Int

    external fun nativeCancel(handle: Long)

    /** 1 = restored from [cachePath], 0 = computed (and saved), -1 = error. */
    external fun nativeWarmUp(handle: Long, prefix: String, cachePath: String, sink: PieceSink): Int

    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        grammar: String?,
        temperature: Float,
        topP: Float,
        minP: Float,
        seed: Int,
        maxTokens: Int,
        sink: PieceSink,
    ): Int
}
