package ir.roozban.ai.tools

/**
 * Checks an answer against the per-message tool set the way the grammar would (tool allowed,
 * pinned choices respected). Used by tests to keep examples and eval answers producible.
 */
object GrammarCheck {
    fun problems(context: AssistantContext, message: String, answer: String): List<String> {
        val overdue = context.tasks.any { t -> t.due?.let { it.date < context.today } == true }
        val allowed = MessageGrammar.tools(Hints.find(context, message), message, overdue).associateBy { it.name }
        return ResponseParser.parse(answer).actions.flatMap { call ->
            val spec = allowed[call.tool] ?: return@flatMap listOf("${call.tool} not allowed (allowed: ${allowed.keys})")
            spec.args.mapNotNull { a ->
                val type = a.type as? ArgType.Choice ?: return@mapNotNull null
                val v = call.text(a.name)
                when {
                    v == null && type.nullable -> null
                    v != null && v in type.values -> null
                    else -> "${call.tool}.${a.name}=$v not in ${type.values}${if (type.nullable) "+null" else ""}"
                }
            }
        }
    }
}
