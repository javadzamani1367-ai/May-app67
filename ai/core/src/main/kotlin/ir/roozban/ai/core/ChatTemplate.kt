package ir.roozban.ai.core

enum class Role { SYSTEM, USER, ASSISTANT }

data class ChatMessage(val role: Role, val content: String)

/**
 * Renders a conversation into the prompt format a model was trained on. The rendered prompt ends
 * with the assistant's turn opened, ready for generation. The tokenizer adds BOS by itself.
 */
enum class ChatTemplate {
    /** Qwen2.5 / Qwen3-Instruct-2507: ChatML. */
    CHATML,

    /** Qwen3 hybrid models: ChatML with an empty think block, i.e. thinking switched off. */
    CHATML_NO_THINK,

    /** Gemma 2/3: no system role; the system text is prepended to the first user turn. */
    GEMMA,
    ;

    /**
     * [openAnswer] false renders only the given turns, e.g. a fixed prefix to pre-compute; the
     * full prompt then starts with exactly that text.
     */
    fun render(messages: List<ChatMessage>, openAnswer: Boolean = true): String = when (this) {
        CHATML, CHATML_NO_THINK -> buildString {
            messages.forEach {
                append("<|im_start|>").append(it.role.chatMl).append('\n')
                if (it.role == Role.ASSISTANT && this@ChatTemplate == CHATML_NO_THINK) append("<think>\n\n</think>\n\n")
                append(it.content).append("<|im_end|>\n")
            }
            if (openAnswer) {
                append("<|im_start|>assistant\n")
                if (this@ChatTemplate == CHATML_NO_THINK) append("<think>\n\n</think>\n\n")
            }
        }
        GEMMA -> buildString {
            val system = messages.filter { it.role == Role.SYSTEM }.joinToString("\n\n") { it.content }
            var pendingSystem = system.takeIf { it.isNotEmpty() }
            messages.filter { it.role != Role.SYSTEM }.forEach {
                val role = if (it.role == Role.USER) "user" else "model"
                append("<start_of_turn>").append(role).append('\n')
                if (it.role == Role.USER && pendingSystem != null) {
                    append(pendingSystem).append("\n\n")
                    pendingSystem = null
                }
                append(it.content).append("<end_of_turn>\n")
            }
            if (openAnswer) append("<start_of_turn>model\n")
        }
    }

    /** Text that ends a turn; generation stops there even without an end-of-generation token. */
    val stop: String
        get() = when (this) {
            CHATML, CHATML_NO_THINK -> "<|im_end|>"
            GEMMA -> "<end_of_turn>"
        }

    private val Role.chatMl get() = name.lowercase()
}
