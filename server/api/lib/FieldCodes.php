<?php
declare(strict_types=1);

/**
 * کد رهگیری موردهای میدانی: `RZ-1405-000123`.
 *
 * کد را سرور می‌دهد، نه گوشی: گوشی‌ها آفلاین کار می‌کنند و دو گوشی که هرکدام
 * شماره بعدی را خودشان حدس بزنند، یک کد را به دو گزارش می‌دهند. شمارنده هر
 * پیشوند در هر سال شمسی جداست و با یک دستور اتمی جلو می‌رود.
 */
final class FieldCodes
{
    public static function format(string $prefix, int $jalaliYear, int $number): string
    {
        return sprintf('%s-%04d-%06d', $prefix, $jalaliYear, $number);
    }

    /** سال شمسی یک لحظه، به وقت ایران. */
    public static function jalaliYear(int $millis): int
    {
        $date = (new DateTimeImmutable('@' . intdiv($millis, 1000)))
            ->setTimezone(new DateTimeZone(Jalali::ZONE));
        return Jalali::fromGregorian((int) $date->format('Y'), (int) $date->format('n'), (int) $date->format('j'))[0];
    }

    /**
     * کد بعدی. INSERT ... ON DUPLICATE KEY با LAST_INSERT_ID(expr) شمارنده را در
     * همان یک دستور جلو می‌برد و مقدارش را برمی‌گرداند، پس دو درخواست هم‌زمان
     * هرگز یک عدد نمی‌گیرند — بدون قفل جدول.
     */
    public static function next(int $kind, int $createdAt): string
    {
        $prefix = Field::KINDS[$kind]['prefix'];
        $year = self::jalaliYear($createdAt);
        Db::run(
            'INSERT INTO code_counters (prefix, year, last) VALUES (?, ?, LAST_INSERT_ID(1))
             ON DUPLICATE KEY UPDATE last = LAST_INSERT_ID(last + 1)',
            [$prefix, $year]
        );
        $number = (int) Db::conn()->lastInsertId();
        return self::format($prefix, $year, $number);
    }
}
