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
            val plan = ActionPlanner(Scenario.context()).plan(ResponseParser.parse(case.golden!!))
            assertWithMessage(case.id).that(Judge.check(case, plan)).isEmpty()
        }
    }
}
