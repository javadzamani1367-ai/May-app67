<?php
declare(strict_types=1);

/**
 * واحدهای مقصد و مراحل اقدام هر کدام.
 *
 * واحد فقط «دیدم» نمی‌گوید: در هر پاسخ می‌نویسد کار به کدام مرحله رسیده، با
 * توضیح و مدرک. آخرین مرحله هر واحد یعنی پرونده در آن واحد بسته شد. مدیر
 * می‌تواند نام و شمار مراحل را در پنل عوض کند؛ تا نکرده، همین پیش‌فرض‌ها.
 */
final class UnitStages
{
    public const NAMES = ['واحد فروش', 'واحد حراست', 'واحد حقوقی', 'برق شهرستان'];

    /** برای کد کاربری ساخته‌شده: 401-sales و… */
    public const SLUGS = ['sales', 'security', 'legal', 'power'];

    public const DEFAULTS = [
        0 => ['دریافت و بررسی', 'محاسبه انرژی و خسارت', 'صدور صورتحساب', 'ابلاغ به مشترک', 'وصول یا ارجاع به حقوقی', 'مختومه'],
        1 => ['دریافت و بررسی', 'هماهنگی با مراجع انتظامی', 'اقدام انجام شد', 'مختومه'],
        2 => ['دریافت و بررسی', 'تنظیم شکوائیه', 'طرح در مرجع قضایی', 'صدور رأی', 'اجرای حکم', 'مختومه'],
        3 => ['دریافت و بررسی', 'قطع انشعاب یا جمع‌آوری تجهیزات', 'تعویض یا آزمایش کنتور', 'اصلاح انشعاب', 'مختومه'],
    ];

    /** @var array<int, list<string>>|null */
    private static ?array $cache = null;

    /** @return list<string> */
    public static function of(int $unit): array
    {
        return self::all()[$unit] ?? [];
    }

    /** @return array<int, list<string>> */
    public static function all(): array
    {
        if (self::$cache !== null) {
            return self::$cache;
        }
        $stages = self::DEFAULTS;
        $custom = [];
        foreach (Db::all('SELECT unit, position, name FROM unit_stages ORDER BY unit, position') as $row) {
            $custom[(int) $row['unit']][] = (string) $row['name'];
        }
        foreach ($custom as $unit => $names) {
            if (isset($stages[$unit]) && count($names) >= 2) {
                $stages[$unit] = $names;
            }
        }
        return self::$cache = $stages;
    }

    /** نام مرحله، یا تهی. */
    public static function name(int $unit, ?int $stage): ?string
    {
        return $stage === null ? null : (self::of($unit)[$stage] ?? null);
    }

    public static function isFinal(int $unit, ?int $stage): bool
    {
        return $stage !== null && $stage >= count(self::of($unit)) - 1;
    }

    /** درصد پیشرفت یک ارسال (۰ تا ۱۰۰). */
    public static function progress(int $unit, ?int $stage): int
    {
        $count = count(self::of($unit));
        return $stage === null || $count < 2 ? 0 : (int) round(min($stage, $count - 1) / ($count - 1) * 100);
    }

    /** مهلت این ارسال گذشته و واحد هنوز نه پاسخی داده نه پرونده را بسته. */
    public static function overdue(array $dispatch, ?int $now = null): bool
    {
        $deadline = $dispatch['deadline_at'] ?? null;
        if ($deadline === null) {
            return false;
        }
        $stage = ($dispatch['stage'] ?? null) === null ? null : (int) $dispatch['stage'];
        return !self::isFinal((int) $dispatch['unit'], $stage) && (int) $dispatch['status'] !== 2
            && (int) $deadline < ($now ?? Db::now());
    }

    /**
     * مراحل تازه یک واحد از پنل: هر سطر یک مرحله، دست‌کم دو. خطا به فارسی یا تهی.
     */
    public static function save(int $unit, string $text): ?string
    {
        if (!isset(self::DEFAULTS[$unit])) {
            return 'واحد نامعتبر است.';
        }
        $names = array_values(array_filter(array_map(static fn ($l) => mb_substr(trim($l), 0, 120),
            preg_split('/\R/u', $text) ?: []), static fn ($l) => $l !== ''));
        if (count($names) < 2 || count($names) > 12) {
            return 'بین ۲ تا ۱۲ مرحله بنویسید؛ آخرین مرحله یعنی پرونده بسته شد.';
        }
        Db::run('DELETE FROM unit_stages WHERE unit = ?', [$unit]);
        foreach ($names as $i => $name) {
            Db::run('INSERT INTO unit_stages (unit, position, name) VALUES (?, ?, ?)', [$unit, $i, $name]);
        }
        self::$cache = null;
        return null;
    }
}
