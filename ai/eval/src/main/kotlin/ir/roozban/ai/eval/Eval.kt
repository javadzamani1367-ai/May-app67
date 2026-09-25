package ir.roozban.ai.eval

import ir.roozban.ai.core.ChatTemplate
import ir.roozban.ai.tools.ActionPlanner
import ir.roozban.ai.tools.MessageGrammar
import ir.roozban.ai.tools.PromptBuilder
import ir.roozban.ai.tools.ResponseParser
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Runs the eval set against a llama.cpp server (`llama-server -m model.gguf`) with the app's own
 * prompt, grammar, parser and planner, and writes a Markdown report.
 *
 * Arguments: --server URL --template CHATML|CHATML_NO_THINK|GEMMA --cases FILE --out FILE --min 0.0..1.0
 */
fun main(args: Array<String>) {
    val opts = args.toList().chunked(2).associate { it[0].removePrefix("--") to it.getOrElse(1) { "" } }
    val server = opts["server"] ?: "http://127.0.0.1:8080"
    val template = ChatTemplate.valueOf(opts["template"] ?: "CHATML_NO_THINK")
    val cases = EvalCases.load(File(opts["cases"] ?: "tools/eval/cases.jsonl"))
    val out = File(opts["out"] ?: "build/eval-report.md")
    val min = opts["min"]?.toDouble() ?: 0.0
    val label = opts["label"] ?: template.name

    val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    val context = Scenario.context()
    val builder = PromptBuilder(template, contextTokens = 4096)
    val rows = mutableListOf<String>()
    var passed = 0
    var totalMillis = 0L
    cases.forEach { case ->
        val prompt = runBlocking { builder.build(context, emptyList(), case.input) }
        val body = buildJsonObject {
            put("prompt", prompt)
            put("grammar", MessageGrammar.grammar(context, case.input))
            put("n_predict", PromptBuilder.ANSWER_TOKENS)
            put("temperature", 0.0)
            put("cache_prompt", true)
        }.toString()
        val start = System.nanoTime()
        val response = http.send(
            HttpRequest.newBuilder(URI("$server/completion")).timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val millis = (System.nanoTime() - start) / 1_000_000
        totalMillis += millis
        val content = (Json.parseToJsonElement(response.body()).jsonObject["content"] as? JsonPrimitive)?.content.orEmpty()
        val plan = ActionPlanner(context, case.input).plan(ResponseParser.parse(content))
        val problems = Judge.check(case, plan)
        if (problems.isEmpty()) passed++
        val mark = if (problems.isEmpty()) "✅" else "❌"
        println("$mark ${case.id} (${millis}ms) ${problems.joinToString("; ")}")
        if (problems.isNotEmpty()) println("    output: $content")
        rows += "| $mark | ${case.id} | ${case.input} | ${problems.joinToString("<br>").ifEmpty { "—" }.replace("|", "\\|")} | `${content.replace("|", "\\|").take(300)}` | $millis |"
    }
    val accuracy = passed.toDouble() / cases.size
    val summary = "**$label**: $passed/${cases.size} passed (${"%.0f".format(accuracy * 100)}%), average ${totalMillis / cases.size.coerceAtLeast(1)} ms per case"
    out.parentFile?.mkdirs()
    out.writeText(
        buildString {
            appendLine("# Assistant eval — $label")
            appendLine()
            appendLine(summary)
            appendLine()
            appendLine("| | case | input | problems | output | ms |")
            appendLine("|---|---|---|---|---|---|")
            rows.forEach(::appendLine)
        },
    )
    println(summary)
    if (accuracy < min) exitProcess(1)
}
