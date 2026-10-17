<?php
declare(strict_types=1);

/**
 * کد رهگیری پرونده‌ای که مدیر یا مجری روی سرور ثبت می‌کند — همان قالب گوشی
 * (CLAUDE.md بخش ۶، `util/TrackingCode.kt`): `M-401-050614-482917`.
 *
 * بی شماره اشتراک، کد موقت گوشی `T` و شماره روزانه خود آن گوشی را دارد. سرور به
 * جای آن `S` می‌گذارد تا هرگز با کد موقت یک گوشی یکی نشود؛ گوشی کارشناس وقتی
 * شماره اشتراک ثبت شد، کد نهایی را از همین ناحیه و تاریخ می‌سازد.
 */
final class CaseCodes
{
    public const LETTERS = [2 => 'N', 3 => 'M', 4 => 'H', 5 => 'T', 6 => 'F'];

    /** YYMMDD شمسی یک لحظه، به وقت ایران. */
    public static function stamp(int $millis): string
    {
        $t = (new DateTimeImmutable('@' . intdiv($millis, 1000)))->setTimezone(new DateTimeZone(Jalali::ZONE));
        [$y, $m, $d] = Jalali::fromGregorian((int) $t->format('Y'), (int) $t->format('n'), (int) $t->format('j'));
        return sprintf('%02d%02d%02d', $y % 100, $m, $d);
    }

    /** شش رقم آخر شماره اشتراک، با صفر از چپ؛ تهی اگر رقمی نیست. */
    public static function tail(?string $subscription): ?string
    {
        $digits = preg_replace('/\D/', '', Jalali::latin((string) $subscription));
        if ($digits === '') {
            return null;
        }
        return strlen($digits) >= 6 ? substr($digits, -6) : str_pad($digits, 6, '0', STR_PAD_LEFT);
    }

    /** کد نهایی، یا تهی (سامانه سراغ کد خودش را دارد؛ بی شماره اشتراک کد نهایی نیست). */
    public static function final(int $type, string $area, int $reportDate, ?string $subscription): ?string
    {
        $letter = self::LETTERS[$type] ?? null;
        $tail = self::tail($subscription);
        return $letter === null || $tail === null ? null
            : implode('-', [$letter, $area, self::stamp($reportDate), $tail]);
    }

    /** کد موقت سرور: `M-401-050614-S0042`. */
    public static function temporary(int $type, string $area, int $reportDate, int $sequence): ?string
    {
        $letter = self::LETTERS[$type] ?? null;
        return $letter === null ? null
            : implode('-', [$letter, $area, self::stamp($reportDate), 'S' . sprintf('%04d', $sequence % 10000)]);
    }

    /**
     * شماره ثبت سرور برای پرونده‌ای که هنوز کد رهگیری ندارد: `IN-1405-000042`.
     * همان شمارنده اتمی کدهای اپ میدانی، با پیشوند خودش.
     *
     * @return array{0:string,1:int}
     */
    public static function nextIntake(int $millis): array
    {
        $year = FieldCodes::jalaliYear($millis);
        Db::run(
            'INSERT INTO code_counters (prefix, year, last) VALUES (?, ?, LAST_INSERT_ID(1))
             ON DUPLICATE KEY UPDATE last = LAST_INSERT_ID(last + 1)',
            ['IN', $year]
        );
        $number = (int) Db::conn()->lastInsertId();
        return [FieldCodes::format('IN', $year, $number), $number];
    }
}
