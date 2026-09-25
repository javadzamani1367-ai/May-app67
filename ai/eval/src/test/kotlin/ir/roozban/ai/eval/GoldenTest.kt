package ir.roozban.ai.eval

import com.google.common.truth.Truth.assertWithMessage
import ir.roozban.ai.tools.ActionPlanner
import ir.roozban.ai.tools.ResponseParser
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/** Every case's reference answer must pass its own expectations: keeps the set and judge honest. */
class GoldenTest {
    private val cases = EvalCases.load(File(System.getProperty("user.dir")).resolve("../../tools/eval/cases.jsonl"))

    @TestFactory
    fun golden() = cases.map { case ->
        DynamicTest.dynamicTest(case.id) {
            val plan = ActionPlanner(Scenario.context(), case.input).plan(ResponseParser.parse(case.golden!!))
            assertWithMessage(case.id).that(Judge.check(case, plan)).isEmpty()
            assertWithMessage("${case.id} grammar").that(ir.roozban.ai.tools.GrammarCheck.problems(Scenario.context(), case.input, case.golden!!)).isEmpty()
            // For checking against llama.cpp's validator: the golden answer must fit the case's grammar.
            File("build/gbnf-cases").apply { mkdirs() }.let { dir ->
                dir.resolve("${case.id}.gbnf").writeText(ir.roozban.ai.tools.MessageGrammar.grammar(Scenario.context(), case.input))
                dir.resolve("${case.id}.json").writeText(case.golden!!)
            }
        }
    }
}
