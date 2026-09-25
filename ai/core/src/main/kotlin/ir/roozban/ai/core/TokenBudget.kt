package ir.roozban.ai.core

/**
 * Rough token count for when the tokenizer is not at hand. Persian costs far more tokens per
 * character than English with these vocabularies, so the estimate leans high.
 */
object TokenEstimate {
    fun of(text: String): Int {
        var ascii = 0
        var other = 0
        text.forEach { if (it.code < 128) ascii++ else other++ }
        return (ascii + 3) / 4 + (other + 1) / 2 + 1
    }
}
