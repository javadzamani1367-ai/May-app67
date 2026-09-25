package ir.roozban.ai.tools

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

class ParsingTest {
    @Test
    fun `parses a complete answer`() {
        val r = ResponseParser.parse(
            """{"actions":[{"tool":"complete_task","args":{"task":"#2"}},{"tool":"create_task","args":{"title":"نان","when":null,"repeat":null,"important":true,"urgent":false,"minutes":20,"project":null}}],"reply":"باشه \"حتما\""}""",
        )
        assertThat(r.damaged).isFalse()
        assertThat(r.reply).isEqualTo("باشه \"حتما\"")
        assertThat(r.actions.map { it.tool }).containsExactly("complete_task", "create_task").inOrder()
        val create = r.actions[1]
        assertThat(create.text("title")).isEqualTo("نان")
        assertThat(create.text("when")).isNull()
        assertThat(create.flag("important")).isTrue()
        assertThat(create.number("minutes")).isEqualTo(20)
    }

    @Test
    fun `salvages actions when the reply was cut off`() {
        val r = ResponseParser.parse("""{"actions":[{"tool":"complete_task","args":{"task":"#1"}}],"reply":"انجام شد و""")
        assertThat(r.damaged).isTrue()
        assertThat(r.actions.single().text("task")).isEqualTo("#1")
        assertThat(r.reply).isEqualTo("انجام شد و")
    }

    @Test
    fun `plain text is kept as the reply`() {
        val r = ResponseParser.parse("سلام، چطوری؟")
        assertThat(r.actions).isEmpty()
        assertThat(r.reply).isEqualTo("سلام، چطوری؟")
    }

    @Test
    fun `text around the json is ignored`() {
        val r = ResponseParser.parse("""سلام {"actions":[],"reply":"hi"} <|im_end|>""")
        assertThat(r.reply).isEqualTo("hi")
        assertThat(r.damaged).isFalse()
    }

    @Test
    fun `reply streams piece by piece, escapes included`() {
        val answer = """{"actions":[{"tool":"complete_task","args":{"task":"#1"}}],"reply":"سلام \"دوست\"\nخوبی؟ \u0041"}"""
        val s = ReplyStream()
        val seen = StringBuilder()
        answer.chunked(3).forEach { seen.append(s.feed(it)) }
        assertThat(s.text).isEqualTo("سلام \"دوست\"\nخوبی؟ A")
        assertThat(seen.toString()).isEqualTo(s.text)
        assertThat(ResponseParser.parse(answer).reply).isEqualTo(s.text)
    }

    @Test
    fun `reply stream decodes escapes split across pieces`() {
        val s = ReplyStream()
        listOf("{\"actions\":[],\"re", "ply\":\"a\\", "\"b\\u06", "33c\"}").forEach { s.feed(it) }
        assertThat(s.text).isEqualTo("a\"bسc")
    }

    @Test
    fun `every prompt example is a clean answer with known tools and all arguments`() {
        PromptBuilder.EXAMPLES.forEach { ex ->
            val r = ResponseParser.parse(ex.answer)
            assertThat(r.damaged).isFalse()
            assertThat(r.reply).isNotEmpty()
            r.actions.forEach { call ->
                val spec = Tools.get(call.tool)
                assertThat(spec).isNotNull()
                assertThat(call.args.keys.toList()).isEqualTo(spec!!.args.map { it.name })
            }
        }
    }

    @Test
    fun `example times are copied from their messages`() {
        Examples.all.forEach { (_, message, answer) ->
            ResponseParser.parse(answer).actions.forEach { call ->
                listOf("when", "day", "reminder", "repeat").mapNotNull { call.text(it) }.forEach { t ->
                    assertWithMessage(message).that(Matcher.normalize(message)).contains(Matcher.normalize(t))
                }
            }
        }
    }
}
