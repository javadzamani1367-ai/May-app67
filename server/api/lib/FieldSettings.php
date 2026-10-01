<?php
declare(strict_types=1);

/**
 * تنظیماتی که مدیر برای اپ میدانی تعیین می‌کند. هر گوشی در همگام‌سازی همین‌ها
 * را می‌گیرد، پس عوض کردنشان نسخه تازه اپ نمی‌خواهد.
 */
final class FieldSettings
{
    /** مقدار پیش‌فرض هر کلید، و اینکه گوشی آن را می‌بیند یا فقط سرور. */
    public const DEFAULTS = [
        // اختلاف مجاز مجموع فیدرها با کلید کل: درصد، با کف مطلق برای بار کم.
        'amp_tolerance_pct' => 10,
        'amp_tolerance_min_a' => 5,
        // پاک کردن نسخه گوشی بعد از رسیدن کامل به سرور.
        'purge_after_sync' => 0,
        // اکیپ نام گزارش‌دهنده را ببیند یا فقط «گزارش همکار».
        'show_reporter_to_team' => 0,
        // آنچه گزارش‌دهنده از نتیجه می‌بیند: ۰ هیچ، ۱ فقط نتیجه، ۲ با جزئیات.
        'reporter_result_detail' => 1,
    ];

    /** کلیدهایی که به گوشی کاربر میدانی می‌روند. */
    private const FOR_PHONE = ['amp_tolerance_pct', 'amp_tolerance_min_a', 'purge_after_sync'];

    /** @return array<string, int> */
    public static function all(): array
    {
        $values = self::DEFAULTS;
        foreach (Db::all('SELECT k, v FROM field_settings') as $row) {
            if (array_key_exists($row['k'], $values)) {
                $values[$row['k']] = (int) $row['v'];
            }
        }
        return $values;
    }

    /** @return array<string, int> */
    public static function forPhone(): array
    {
        return array_intersect_key(self::all(), array_flip(self::FOR_PHONE));
    }

    public static function set(string $key, int $value): void
    {
        if (!array_key_exists($key, self::DEFAULTS)) {
            Response::fail(400, 'unknown_setting', 'این تنظیم شناخته نیست.');
        }
        Db::run(
            'INSERT INTO field_settings (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)',
            [$key, (string) $value]
        );
    }
}
