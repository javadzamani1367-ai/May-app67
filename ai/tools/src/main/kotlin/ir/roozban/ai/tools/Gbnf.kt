package ir.roozban.ai.tools

/**
 * Builds the GBNF grammar (llama.cpp) for the answer format:
 * `{"actions":[{"tool":"…","args":{…}},…],"reply":"…"}` — compact JSON, keys in fixed order.
 * [ArgType.Choice] arguments become a fixed set of JSON strings, which is how per-message
 * grammars pin task references and time words to what the app found (see [MessageGrammar]).
 */
object Gbnf {
    fun forTools(tools: List<ToolSpec> = Tools.all, maxActions: Int = Tools.MAX_ACTIONS): String = buildString {
        if (tools.isEmpty()) {
            appendLine("root ::= \"{\\\"actions\\\":[],\\\"reply\\\":\" str \"}\"")
        } else {
            appendLine("root ::= \"{\\\"actions\\\":[\" actions? \"],\\\"reply\\\":\" str \"}\"")
            appendLine("actions ::= action (\",\" action){0,${maxActions - 1}}")
            appendLine("action ::= " + tools.joinToString(" | ") { rule(it) })
            tools.forEach { tool ->
                val args = tool.args.joinToString(" \",\" ") { "${literal("\"${it.name}\":")} ${typeRule(it.type)}" }
                val body = if (tool.args.isEmpty()) "" else " $args"
                appendLine("${rule(tool)} ::= ${literal("{\"tool\":\"${tool.name}\",\"args\":{")}$body ${literal("}}")}")
            }
        }
        appendLine("str ::= \"\\\"\" chr* \"\\\"\"")
        appendLine("chr ::= [^\"\\\\\\x00-\\x1F] | \"\\\\\" [\"\\\\/nt]")
        appendLine("nstr ::= str | \"null\"")
        appendLine("int ::= \"0\" | [1-9] [0-9]{0,3}")
        appendLine("nint ::= int | \"null\"")
        appendLine("bool ::= \"true\" | \"false\"")
        appendLine("nbool ::= bool | \"null\"")
    }

    private fun rule(tool: ToolSpec) = "t-" + tool.name.replace('_', '-')

    private fun typeRule(type: ArgType): String = when (type) {
        is ArgType.Text -> if (type.nullable) "nstr" else "str"
        is ArgType.Number -> if (type.nullable) "nint" else "int"
        is ArgType.Flag -> if (type.nullable) "nbool" else "bool"
        is ArgType.Choice -> {
            val options = type.values.map { literal("\"" + jsonEscape(it) + "\"") } + if (type.nullable) listOf("\"null\"") else emptyList()
            "(" + options.joinToString(" | ") + ")"
        }
    }

    private fun jsonEscape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    /** A GBNF string literal. */
    internal fun literal(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
