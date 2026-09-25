package ir.roozban.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One tool call as the model wrote it. */
data class ToolCall(val tool: String, val args: Map<String, JsonElement>) {
    fun text(name: String): String? = (args[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    fun number(name: String): Int? = (args[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }

    fun flag(name: String): Boolean? = (args[name] as? JsonPrimitive)?.let { it.booleanOrNull }
}

data class AssistantResponse(
    val actions: List<ToolCall>,
    val reply: String,
    /** The output was cut off or malformed; whatever could be read is kept. */
    val damaged: Boolean = false,
)

object ResponseParser {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * Parses the model's answer. Grammar-constrained output always parses; otherwise (cut off
     * by the token limit, or an engine without grammar) the complete actions are salvaged and
     * the reply is read as far as it goes.
     */
    fun parse(output: String): AssistantResponse {
        val start = output.indexOf('{')
        if (start < 0) return AssistantResponse(emptyList(), output.trim(), damaged = output.isNotBlank())
        val text = output.substring(start)
        runCatching { json.parseToJsonElement(text.substring(0, text.lastIndexOf('}') + 1)).jsonObject }
            .getOrNull()?.let { return fromObject(it, damaged = false) }

        // Salvage: the actions array closes before "reply".
        val actions = ACTIONS_END.find(text)?.let { m ->
            runCatching { json.parseToJsonElement(text.substring(0, m.range.first + 1) + "}").jsonObject }.getOrNull()
        }?.let { actionsOf(it) } ?: emptyList()
        return AssistantResponse(actions, ReplyStream().apply { feed(text) }.text, damaged = true)
    }

    private fun fromObject(obj: JsonObject, damaged: Boolean): AssistantResponse =
        AssistantResponse(actionsOf(obj), (obj["reply"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim(), damaged)

    private fun actionsOf(obj: JsonObject): List<ToolCall> =
        (obj["actions"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val tool = (o["tool"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val args = (o["args"] as? JsonObject)?.toMap() ?: emptyMap()
            ToolCall(tool, args)
        }

    private val ACTIONS_END = Regex("]\\s*,\\s*\"reply\"")
}

/**
 * Pulls the `reply` string out of the answer while it streams, so the chat shows the text as it
 * is written. Feed every generated piece; [text] is the decoded reply so far.
 */
class ReplyStream {
    private val raw = StringBuilder()
    private var start = -1
    private var scanned = 0
    private val out = StringBuilder()
    private var closed = false

    val text: String get() = out.toString()

    /** Returns the newly decoded reply text (possibly empty). */
    fun feed(piece: String): String {
        raw.append(piece)
        if (start < 0) {
            val m = REPLY_KEY.find(raw) ?: return ""
            start = m.range.last + 1
            scanned = start
        }
        if (closed) return ""
        val before = out.length
        var i = scanned
        while (i < raw.length) {
            val c = raw[i]
            if (c == '"') {
                closed = true
                i++
                break
            }
            if (c == '\\') {
                if (i + 1 >= raw.length) break
                val e = raw[i + 1]
                if (e == 'u') {
                    if (i + 6 > raw.length) break
                    raw.substring(i + 2, i + 6).toIntOrNull(16)?.let { out.append(it.toChar()) }
                    i += 6
                    continue
                }
                out.append(
                    when (e) {
                        'n' -> '\n'
                        't' -> '\t'
                        else -> e
                    },
                )
                i += 2
                continue
            }
            out.append(c)
            i++
        }
        scanned = i
        return out.substring(before)
    }

    private companion object {
        val REPLY_KEY = Regex("\"reply\"\\s*:\\s*\"")
    }
}
