package ir.roozban.core.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Many manufacturers kill background apps beyond stock Android, which silences alarms.
 * For each brand: the settings screens to open (tried in order, first that exists wins)
 * and the Persian steps to show the user.
 */
object OemBatteryGuide {

    enum class Brand(val displayName: String) {
        XIAOMI("شیائومی / ردمی / پوکو"),
        SAMSUNG("سامسونگ"),
        HUAWEI("هواوی / آنر"),
        OPPO("اوپو / ریلمی / وان‌پلاس"),
        VIVO("ویوو"),
        OTHER("سایر برندها"),
    }

    data class Guide(val brand: Brand, val steps: List<String>, val autostart: Intent?)

    fun brand(manufacturer: String = Build.MANUFACTURER): Brand = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> Brand.XIAOMI
        "samsung" -> Brand.SAMSUNG
        "huawei", "honor" -> Brand.HUAWEI
        "oppo", "realme", "oneplus" -> Brand.OPPO
        "vivo", "iqoo" -> Brand.VIVO
        else -> Brand.OTHER
    }

    fun steps(brand: Brand): List<String> = when (brand) {
        Brand.XIAOMI -> listOf(
            "در صفحه‌ی «شروع خودکار» (Autostart)، روزبان را روشن کنید.",
            "در تنظیمات برنامه ← «صرفه‌جویی باتری»، گزینه‌ی «بدون محدودیت» را انتخاب کنید.",
            "در فهرست برنامه‌های اخیر، کارت روزبان را پایین بکشید تا قفل شود.",
        )
        Brand.SAMSUNG -> listOf(
            "تنظیمات ← باتری ← محدودیت‌های پس‌زمینه را باز کنید.",
            "روزبان را به «برنامه‌هایی که هرگز به خواب نمی‌روند» اضافه کنید.",
            "گزینه‌ی «به خواب بردن برنامه‌های استفاده‌نشده» را برای روزبان خاموش کنید.",
        )
        Brand.HUAWEI -> listOf(
            "تنظیمات ← باتری ← راه‌اندازی برنامه را باز کنید.",
            "مدیریت خودکار روزبان را خاموش و هر سه گزینه‌ی راه‌اندازی خودکار، ثانویه و اجرا در پس‌زمینه را روشن کنید.",
        )
        Brand.OPPO -> listOf(
            "تنظیمات ← باتری ← روزبان را باز کنید.",
            "«اجازه‌ی فعالیت در پس‌زمینه» و «شروع خودکار» را روشن کنید.",
        )
        Brand.VIVO -> listOf(
            "تنظیمات ← باتری ← مصرف زیاد در پس‌زمینه را باز کنید و روزبان را مجاز کنید.",
            "در i Manager ← مدیریت برنامه ← شروع خودکار، روزبان را روشن کنید.",
        )
        Brand.OTHER -> listOf(
            "تنظیمات ← برنامه‌ها ← روزبان ← باتری را باز کنید.",
            "گزینه‌ی «بدون محدودیت» یا «بهینه‌نشده» را انتخاب کنید.",
        )
    }

    private fun candidates(brand: Brand): List<ComponentName> = when (brand) {
        Brand.XIAOMI -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        Brand.SAMSUNG -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        Brand.HUAWEI -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        Brand.OPPO -> listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        Brand.VIVO -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )
        Brand.OTHER -> emptyList()
    }

    fun guide(context: Context, brand: Brand = brand()): Guide {
        val autostart = candidates(brand)
            .map { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .firstOrNull { context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null }
        return Guide(brand, steps(brand), autostart)
    }
}
