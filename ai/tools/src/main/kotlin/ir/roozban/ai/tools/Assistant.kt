package ir.roozban.ai.tools

import ir.roozban.ai.core.ChatTemplate
import ir.roozban.ai.core.GenerationRequest
import ir.roozban.ai.core.LlmEngine
import ir.roozban.ai.core.SamplingParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

sealed interface AssistantEvent {
    /** The model is reading the prompt: 0..100. */
    data class Reading(val percent: Int) : AssistantEvent

    /** The model has started writing its answer (actions come before the reply text). */
    data object Writing : AssistantEvent

    /** The reply text so far, while the model writes. */
    data class Partial(val reply: String) : AssistantEvent

    /** The whole answer: [raw] is kept as the turn's history; [plan] is ready to confirm or run. */
    data class Complete(val raw: String, val response: AssistantResponse, val plan: Plan) : AssistantEvent
}

/**
 * Requests the app answers itself, without the model (personal memory, see [MemoryIntent]).
 * [AssistantEvent.Complete.raw] is a plain reply so later prompts see an ordinary turn.
 */
fun answerLocally(context: AssistantContext, message: String): AssistantEvent.Complete? {
    val response = MemoryIntent.detect(context, message) ?: return null
    val raw = kotlinx.serialization.json.buildJsonObject {
        put("actions", kotlinx.serialization.json.JsonArray(emptyList()))
        put("reply", kotlinx.serialization.json.JsonPrimitive(response.reply))
    }.toString()
    return AssistantEvent.Complete(raw, response, ActionPlanner(context, message).plan(response))
}

/** One question to the model: prompt, constrained generation, streaming reply, then the plan. */
class Assistant(
    private val engine: LlmEngine,
    private val template: ChatTemplate,
    private val contextTokens: Int,
) {
    fun ask(context: AssistantContext, history: List<Turn>, message: String): Flow<AssistantEvent> = channelFlow {
        answerLocally(context, message)?.let {
            send(it)
            return@channelFlow
        }
        val builder = PromptBuilder(template, contextTokens) { engine.countTokens(it) ?: ir.roozban.ai.core.TokenEstimate.of(it) }
        val prompt = builder.build(context, history, message)
        val raw = StringBuilder()
        val reply = ReplyStream()
        send(AssistantEvent.Reading(0))
        val request = GenerationRequest(
            prompt, MessageGrammar.grammar(context, message), SamplingParams.Greedy, PromptBuilder.ANSWER_TOKENS,
            onPromptProgress = { trySend(AssistantEvent.Reading(it)) },
        )
        engine.generate(request).collect { piece ->
            if (raw.isEmpty()) send(AssistantEvent.Writing)
            raw.append(piece)
            if (reply.feed(piece).isNotEmpty()) send(AssistantEvent.Partial(reply.text))
        }
        val text = raw.toString().substringBefore(template.stop).trim()
        val response = ResponseParser.parse(text)
        send(AssistantEvent.Complete(text, response, ActionPlanner(context, message).plan(response)))
    }
}
