package ir.roozban.ai.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EditLearningTest {
    @Test
    fun `a word replaced by a similar one is a correction`() {
        val l = EditLearning.learn("پاتشاه به شهر آمد", "پادشاه به شهر آمد")
        assertThat(l.corrections).containsExactly("پاتشاه", "پادشاه")
        assertThat(l.ownWords).isEmpty()
    }

    @Test
    fun `words typed in are the user's own, a rewrite is not a correction`() {
        val l = EditLearning.learn("جلسه با آقای کریمی", "جلسه با آقای کاظمی‌نژاد و تیم فروش")
        assertThat(l.corrections).isEmpty()
        assertThat(l.ownWords).containsAtLeast("کاظمی‌نژاد".replace("‌", ""), "تیم", "فروش")
    }

    @Test
    fun `unchanged text teaches nothing`() {
        val l = EditLearning.learn("امروز هوا خوب است", "امروز هوا خوب است")
        assertThat(l.corrections).isEmpty()
        assertThat(l.ownWords).isEmpty()
    }
}
