package ir.roozban.ai.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MemoryIntentTest {
    private val f = Fixture()

    @Test
    fun `personal statements are remembered, reminders are left to the model`() = runTest {
        val ctx = f.context()
        val said = MemoryIntent.detect(ctx, "یادت باشه که من صبح‌ها باشگاه می‌رم")!!
        assertThat(said.actions.single().tool).isEqualTo(Tools.rememberPreference.name)
        assertThat(said.actions.single().text("fact")).isEqualTo("من صبح‌ها باشگاه می‌رم")
        assertThat(MemoryIntent.detect(ctx, "به خاطر بسپار همیشه جلسه‌ها رو یک ساعت بیشتر در نظر بگیرم")).isNotNull()
        // Reminders and to-dos stay with the model.
        assertThat(MemoryIntent.detect(ctx, "یادت باشه فردا ساعت ۵ به مامان زنگ بزنم")).isNull()
        assertThat(MemoryIntent.detect(ctx, "یادت باشه نون بخرم")).isNull()
        assertThat(MemoryIntent.detect(ctx, "یادت باشه منزل رو تمیز کنم")).isNull()
        assertThat(MemoryIntent.detect(ctx, "فردا جلسه دارم")).isNull()
    }

    @Test
    fun `remember and forget run locally with undo`() = runTest {
        val remembered = answerLocally(f.context(), "یادت باشه من شب‌ها کار نمی‌کنم")!!
        val r = f.executor.execute(remembered.plan).single()
        assertThat(r.ok).isTrue()
        assertThat(f.memory.all().single().text).isEqualTo("من شب‌ها کار نمی‌کنم")
        assertThat(remembered.plan.needsConfirmation).isFalse()

        val forget = answerLocally(f.context(), "فراموش کن که شب‌ها کار نمی‌کنم")!!
        assertThat(forget.plan.needsConfirmation).isTrue()
        val done = f.executor.execute(forget.plan).single()
        assertThat(done.ok).isTrue()
        assertThat(f.memory.all()).isEmpty()
        done.undo!!()
        assertThat(f.memory.all()).hasSize(1)

        val unknown = answerLocally(f.context(), "فراموش کن که گربه دارم")!!
        assertThat(unknown.plan.actions.single().runnable).isFalse()
    }

    @Test
    fun `facts reach the prompt state only when there are some`() = runTest {
        assertThat(PromptBuilder.stateOf(f.context(), 10)).doesNotContain("درباره‌ی کاربر")
        f.executor.execute(answerLocally(f.context(), "یادت باشه من صبح‌ها باشگاه می‌رم")!!.plan)
        assertThat(PromptBuilder.stateOf(f.context(), 10)).contains("درباره‌ی کاربر: من صبح‌ها باشگاه می‌رم")
    }
}
