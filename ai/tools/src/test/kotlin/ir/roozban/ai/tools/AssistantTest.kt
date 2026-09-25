package ir.roozban.ai.tools

import com.google.common.truth.Truth.assertThat
import ir.roozban.ai.core.ChatTemplate
import ir.roozban.ai.core.EngineConfig
import ir.roozban.ai.core.FakeLlmEngine
import ir.roozban.ai.core.TokenEstimate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AssistantTest {
    private val f = Fixture()

    @Test
    fun `grammar has a rule per tool and the shared terminals`() {
        val g = Gbnf.forTools()
        assertThat(g).startsWith("root ::= \"{\\\"actions\\\":[\" actions? \"],\\\"reply\\\":\" str \"}\"")
        Tools.all.forEach { assertThat(g).contains("t-${it.name.replace('_', '-')} ::= \"{\\\"tool\\\":\\\"${it.name}\\\"") }
        assertThat(g).contains("(\"\\\"today\\\"\" | ")
        assertThat(g).contains("actions ::= action (\",\" action){0,5}")
        // Every rule referenced is defined.
        val defined = Regex("^([a-z0-9-]+) ::=", RegexOption.MULTILINE).findAll(g).map { it.groupValues[1] }.toSet()
        val bodies = g.lines().map { it.substringAfter("::=") }.joinToString(" ").replace(Regex("\"(\\\\.|[^\"\\\\])*\""), " ").replace(Regex("\\[[^]]*]"), " ")
        val used = Regex("[a-z][a-z0-9-]*").findAll(bodies).map { it.value }.toSet()
        assertThat(defined).containsAtLeastElementsIn(used)
        // For checking with llama.cpp's test-gbnf-validator (see docs/PHASE_4.md).
        java.io.File("build/gbnf").apply { mkdirs() }.resolve("tools.gbnf").writeText(g)
        PromptBuilder.EXAMPLES.forEachIndexed { i, ex -> java.io.File("build/gbnf/example$i.json").writeText(ex.answer) }
        java.io.File("build/gbnf/prefix.txt").writeText(PromptBuilder(ChatTemplate.CHATML_NO_THINK, 4096).prefix())
    }

    @Test
    fun `message grammar allows only sensible tools with pinned references and times`() = runTest {
        f.task("خرید نان")
        f.task("جلسه با علی")
        f.habit("ورزش")
        val overdue = false
        fun names(msg: String) = MessageGrammar.tools(Hints.find(ir.roozban.ai.tools.AssistantContext(f.clock.now, listOf(), listOf()), msg), msg, overdue).map { it.name }
        assertThat(names("فردا ساعت ۵ به مامان زنگ بزنم")).containsExactly("create_task")
        assertThat(names("فردا چی دارم؟")).containsExactly("create_task", "list_tasks")
        val ctx = f.context()
        val tools = MessageGrammar.tools(Hints.find(ctx, "جلسه با علی رو بنداز فردا"), "جلسه با علی رو بنداز فردا", overdue)
        assertThat(tools.map { it.name }).containsExactly("create_task", "complete_task", "reschedule")
        val reschedule = tools.first { it.name == "reschedule" }
        assertThat(reschedule.args.first { it.name == "task" }.type).isEqualTo(ArgType.Choice(listOf("#2")))
        assertThat(reschedule.args.first { it.name == "when" }.type).isEqualTo(ArgType.Choice(listOf("فردا")))
        assertThat(MessageGrammar.tools(Hints.find(ctx, "کار ۱ رو پاک کن"), "کار ۱ رو پاک کن", overdue).map { it.name }).contains("delete_task")
        assertThat(MessageGrammar.tools(Hints.find(ctx, "امروز ورزش کردم"), "امروز ورزش کردم", overdue).map { it.name }).contains("log_habit")
        val g = MessageGrammar.grammar(ctx, "جلسه با علی رو بنداز فردا")
        assertThat(g).contains("(\"\\\"#2\\\"\")")
    }

    @Test
    fun `prompt lists state, numbers tasks and fits the budget`() = runTest {
        f.task("خرید نان", f.at(0, 18))
        f.habit("ورزش")
        val builder = PromptBuilder(ChatTemplate.CHATML, contextTokens = 4096)
        val prompt = builder.build(f.context(), listOf(Turn("سلام", """{"actions":[],"reply":"سلام"}""")), "نان رو خریدم")
        assertThat(prompt).contains("#1 خرید نان\n")
        assertThat(prompt).contains("کار مرتبط در فهرست: #1 خرید نان\n")
        assertThat(prompt).contains("عادت‌ها: ورزش")
        assertThat(prompt).contains("پنجشنبه ۲ مهر ۱۴۰۵")
        assertThat(prompt).contains("- create_task {\"title\":text,\"when\":text|null")
        assertThat(prompt).endsWith("پیام: نان رو خریدم<|im_end|>\n<|im_start|>assistant\n")
        assertThat(prompt).startsWith(builder.prefix())
        assertThat(prompt).contains("<|im_start|>user\nپیام: سلام<|im_end|>")
        assertThat(TokenEstimate.of(prompt)).isAtMost(4096 - PromptBuilder.ANSWER_TOKENS)
    }

    @Test
    fun `a tight budget drops history first, then tasks`() = runTest {
        repeat(40) { f.task("کار شماره $it با یک عنوان نسبتا بلند برای آزمایش بودجه") }
        val history = List(4) { Turn("پیام قبلی $it", """{"actions":[],"reply":"پاسخ قبلی $it"}""") }
        val full = PromptBuilder(ChatTemplate.CHATML, 100_000).build(f.context(), history, "سلام")
        assertThat(full).contains("پیام قبلی 0")
        assertThat(full).contains("#40 ")
        val budget = TokenEstimate.of(PromptBuilder(ChatTemplate.CHATML, 4096).prefix()) + PromptBuilder.ANSWER_TOKENS + 400
        val tight = PromptBuilder(ChatTemplate.CHATML, budget).build(f.context(), history, "سلام")
        assertThat(tight).doesNotContain("پیام قبلی")
        assertThat(tight).doesNotContain("#40 ")
        assertThat(tight).contains("#1 ")
        assertThat(TokenEstimate.of(tight)).isAtMost(budget - PromptBuilder.ANSWER_TOKENS)
    }

    @Test
    fun `assistant streams the reply and plans the actions`() = runTest {
        f.task("خرید نان")
        val answer = """{"actions":[{"tool":"complete_task","args":{"task":"#1"}}],"reply":"آفرین، خرید نان تیک خورد."}<|im_end|>"""
        val engine = FakeLlmEngine(pieceLength = 5) { answer }
        engine.load("/m.gguf", EngineConfig())
        val events = Assistant(engine, ChatTemplate.CHATML, 4096).ask(f.context(), emptyList(), "نان رو خریدم").toList()
        val partials = events.filterIsInstance<AssistantEvent.Partial>().map { it.reply }
        assertThat(partials.size).isGreaterThan(2)
        assertThat(partials.last()).isEqualTo("آفرین، خرید نان تیک خورد.")
        val done = events.last() as AssistantEvent.Complete
        assertThat(done.raw).doesNotContain("<|im_end|>")
        assertThat(done.plan.actions.single().operation).isInstanceOf(Operation.CompleteTask::class.java)
        assertThat(done.plan.needsConfirmation).isFalse()
        val request = engine.requests.single()
        assertThat(request.grammar).isEqualTo(MessageGrammar.grammar(f.context(), "نان رو خریدم"))
        assertThat(request.sampling.temperature).isEqualTo(0f)
    }
}
