package ir.roozban.ai.tools

import ir.roozban.ai.core.ChatTemplate
import ir.roozban.ai.core.GenerationRequest
import ir.roozban.ai.core.LlmEngine
import ir.roozban.ai.core.SamplingParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface AssistantEvent {
    /** The reply text so far, while the model writes. */
    data class Partial(val reply: String) : AssistantEvent

    /** The whole answer: [raw] is kept as the turn's history; [plan] is ready to confirm or run. */
    data class Complete(val raw: String, val response: AssistantResponse, val plan: Plan) : AssistantEvent
}

/** One question to the model: prompt, constrained generation, streaming reply, then the plan. */
class Assistant(
    private val engine: LlmEngine,
    private val template: ChatTemplate,
    private val contextTokens: Int,
) {
    private val grammar = Gbnf.forTools()

    fun ask(context: AssistantContext, history: List<Turn>, message: String): Flow<AssistantEvent> = flow {
        val builder = PromptBuilder(template, contextTokens) { engine.countTokens(it) ?: ir.roozban.ai.core.TokenEstimate.of(it) }
        val prompt = builder.build(context, history, message)
        val raw = StringBuilder()
        val reply = ReplyStream()
        engine.generate(GenerationRequest(prompt, grammar, SamplingParams.Greedy, PromptBuilder.ANSWER_TOKENS)).collect { piece ->
            raw.append(piece)
            if (reply.feed(piece).isNotEmpty()) emit(AssistantEvent.Partial(reply.text))
        }
        val text = raw.toString().substringBefore(template.stop).trim()
        val response = ResponseParser.parse(text)
        emit(AssistantEvent.Complete(text, response, ActionPlanner(context).plan(response)))
    }
}
