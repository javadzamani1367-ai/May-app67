package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import org.junit.jupiter.api.Test
import java.time.Instant

class EntitlementsTest {
    private val now = Instant.parse("2026-09-24T10:00:00Z")

    @Test
    fun `pro access by entitlement`() {
        assertThat(Entitlement.Free.allowsPro(now)).isFalse()
        assertThat(Entitlement.Trial(now.plusSeconds(60)).allowsPro(now)).isTrue()
        assertThat(Entitlement.Trial(now).allowsPro(now)).isFalse()
        assertThat(Entitlement.Pro(null).allowsPro(now)).isTrue()
        assertThat(Entitlement.Pro(now.minusSeconds(1)).allowsPro(now)).isFalse()
        assertThat(Entitlement.GraceOffline(now.plusSeconds(1)).allowsPro(now)).isTrue()
    }

    @Test
    fun `everything is open until licensing arrives`() {
        val e = ProvisionalEntitlements(TestClock(jalali("1405-07-02").atTime(10, 0)))
        ProFeature.entries.forEach { assertThat(e.access(it)).isEqualTo(Access.FULL) }
    }
}
