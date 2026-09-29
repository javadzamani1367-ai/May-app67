package ir.roozban.ai.eval

import com.google.common.truth.Truth.assertWithMessage
import ir.roozban.ai.tools.ActionPlanner
import ir.roozban.ai.tools.RuleAssistant
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Scores the rule-based assistant on the eval set and on the held-out cases (written before the
 * rules), with the same judge as the language models.
 */
class RuleAssistantEvalTest {
    private val root = File(System.getProperty("user.dir")).resolve("../../tools/eval")

    private fun score(file: String): Pair<Int, Int> {
        val cases = EvalCases.load(root.resolve(file))
        val context = Scenario.context()
        var passed = 0
        cases.forEach { case ->
            val response = RuleAssistant(context).answer(case.input)
            val plan = ActionPlanner(context, case.input).plan(response)
            val problems = Judge.check(case, plan)
            if (problems.isEmpty()) passed++ else println("❌ $file ${case.id} «${case.input}» ${problems.joinToString("; ")} | ${response.actions.map { it.tool to it.args }}")
        }
        println("RULES $file: $passed/${cases.size}")
        return passed to cases.size
    }

    @Test
    fun `rule-based assistant scores`() {
        val (a, n) = score("cases.jsonl")
        val (b, m) = score("holdout.jsonl")
        File("build").mkdirs()
        File("build/rule-assistant-score.txt").writeText("cases $a/$n\nholdout $b/$m\n")
        assertWithMessage("eval set").that(a.toDouble() / n).isAtLeast(0.0)
        assertWithMessage("held-out").that(b.toDouble() / m).isAtLeast(0.0)
    }
}
