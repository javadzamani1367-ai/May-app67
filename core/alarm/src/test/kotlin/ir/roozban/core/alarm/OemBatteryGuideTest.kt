package ir.roozban.core.alarm

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.alarm.OemBatteryGuide.Brand
import org.junit.Test

class OemBatteryGuideTest {
    @Test
    fun `manufacturers map to brands`() {
        assertThat(OemBatteryGuide.brand("Xiaomi")).isEqualTo(Brand.XIAOMI)
        assertThat(OemBatteryGuide.brand("POCO")).isEqualTo(Brand.XIAOMI)
        assertThat(OemBatteryGuide.brand("samsung")).isEqualTo(Brand.SAMSUNG)
        assertThat(OemBatteryGuide.brand("HONOR")).isEqualTo(Brand.HUAWEI)
        assertThat(OemBatteryGuide.brand("realme")).isEqualTo(Brand.OPPO)
        assertThat(OemBatteryGuide.brand("Google")).isEqualTo(Brand.OTHER)
    }

    @Test
    fun `every brand has steps`() {
        Brand.entries.forEach { assertThat(OemBatteryGuide.steps(it)).isNotEmpty() }
    }
}
