package ir.roozban.ai.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CoreTest {
    private val chat = listOf(
        ChatMessage(Role.SYSTEM, "sys"),
        ChatMessage(Role.USER, "سلام"),
        ChatMessage(Role.ASSISTANT, "{}"),
        ChatMessage(Role.USER, "بعدی"),
    )

    @Test
    fun `chatml renders roles and opens the assistant turn`() {
        assertThat(ChatTemplate.CHATML.render(chat)).isEqualTo(
            "<|im_start|>system\nsys<|im_end|>\n<|im_start|>user\nسلام<|im_end|>\n" +
                "<|im_start|>assistant\n{}<|im_end|>\n<|im_start|>user\nبعدی<|im_end|>\n<|im_start|>assistant\n",
        )
        assertThat(ChatTemplate.CHATML_NO_THINK.render(chat)).endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n")
    }

    @Test
    fun `gemma folds the system text into the first user turn`() {
        assertThat(ChatTemplate.GEMMA.render(chat)).isEqualTo(
            "<start_of_turn>user\nsys\n\nسلام<end_of_turn>\n<start_of_turn>model\n{}<end_of_turn>\n" +
                "<start_of_turn>user\nبعدی<end_of_turn>\n<start_of_turn>model\n",
        )
    }

    @Test
    fun `device tiers follow reported ram`() {
        val gb = DeviceTier.GB
        assertThat(DeviceTier.of(2 * gb)).isEqualTo(DeviceTier.UNSUPPORTED)
        assertThat(DeviceTier.of(3 * gb)).isEqualTo(DeviceTier.LIGHT)
        assertThat(DeviceTier.of(3_600L * DeviceTier.MB)).isEqualTo(DeviceTier.LIGHT)
        assertThat(DeviceTier.of(3_800L * DeviceTier.MB)).isEqualTo(DeviceTier.STANDARD) // a "4 GB" phone
        assertThat(DeviceTier.of(7_500L * DeviceTier.MB)).isEqualTo(DeviceTier.HIGH) // an "8 GB" phone
        assertThat(DeviceTier.threadsFor(8)).isEqualTo(4)
        assertThat(DeviceTier.threadsFor(2)).isEqualTo(2)
    }

    @Test
    fun `fake engine streams pieces and needs a model`() = runTest {
        val engine = FakeLlmEngine(pieceLength = 3) { "abcdefg" }
        assertThrows<LlmException> { engine.generate(GenerationRequest("p")).toList() }
        engine.load("/m.gguf", EngineConfig())
        assertThat(engine.generate(GenerationRequest("p")).toList()).containsExactly("abc", "def", "g").inOrder()
        assertThat(engine.requests.single().prompt).isEqualTo("p")
    }

    @Test
    fun `token estimate leans high for persian`() {
        assertThat(TokenEstimate.of("hello world")).isAtLeast(3)
        assertThat(TokenEstimate.of("سلام دنیا")).isAtLeast(4)
    }
}
