package ir.roozban.feature.assistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {
    @Test
    fun sizes() {
        assertThat(formatSize(396_705_472L)).isEqualTo("۳۹۶ مگابایت")
        assertThat(formatSize(1_107_409_472L)).isEqualTo("۱٫۱ گیگابایت")
        assertThat(formatSize(2_497_281_120L)).isEqualTo("۲٫۵ گیگابایت")
        assertThat(formatSize(10)).isEqualTo("۱ مگابایت")
    }
}
