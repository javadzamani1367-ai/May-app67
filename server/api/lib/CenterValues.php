<?php
declare(strict_types=1);

/**
 * مقدار هر خانه آمار از سه منبع: سامانه، فایل اکسل، و آنچه کاربر نوشت.
 *
 * - کاربر انتخاب کرده (choice_json): file، system یا own، همان برنده است
 * - کاربر نوشته (values_json، با choice own): همان
 * - وگرنه سامانه، و اگر سامانه چیزی نداشت فایل
 *
 * اگر سامانه و فایل هر دو مقدار دارند و یکی نیستند و کسی هنوز انتخاب نکرده، خانه
 * «ناسازگار» است: مقدار سامانه نشان داده می‌شود ولی مدیر باید یکی را برگزیند —
 * یا مقدار سومی بنویسد.
 */
final class CenterValues
{
    /** یکسان‌سازی هر مقدار ورودی به نوع ستون؛ خالی تهی می‌شود. */
    public static function normalize(string $type, mixed $raw): mixed
    {
        if ($raw === null) {
            return null;
        }
        if (is_string($raw)) {
            $raw = self::cleanText($raw);
            if ($raw === '' || $raw === '-' || $raw === '—') {
                return null;
            }
        }
        switch ($type) {
            case 'int':
            case 'money':
            case 'kw':
                $number = self::number($raw);
                if ($number === null) {
                    return null;
                }
                return $type === 'kw' ? round($number, 3) : (abs($number - round($number)) < 0.0001 ? (int) round($number) : $number);
            case 'date':
                return self::date($raw);
            case 'tick':
                if (is_int($raw) || is_float($raw)) {
                    return $raw > 0 ? 1 : null;
                }
                return in_array(mb_strtolower((string) $raw), ['0', 'خیر', 'ندارد', 'نه', 'no', 'false'], true) ? null : 1;
            case 'sheba':
                $sheba = strtoupper((string) preg_replace('/[\s\-]/u', '', Jalali::latin((string) $raw)));
                return preg_match('/^\d{24}$/', $sheba) === 1 ? 'IR' . $sheba : $sheba;
            default:
                if (is_float($raw) && floor($raw) === $raw && abs($raw) < 1e15) {
                    return (string) (int) $raw;
                }
                return (string) $raw;
        }
    }

    /** دو مقدار یکی‌اند؟ عدد با رواداری، متن بی فاصله و نیم‌فاصله، تاریخ به روز. */
    public static function same(string $type, mixed $a, mixed $b): bool
    {
        if ($a === null || $b === null) {
            return $a === $b;
        }
        return match ($type) {
            'int', 'money', 'kw' => abs((float) $a - (float) $b) < 0.01 + 0.0005 * max(abs((float) $a), abs((float) $b)),
            'date' => Jalali::format((int) $a, false) === Jalali::format((int) $b, false),
            'tick' => (int) $a === (int) $b,
            default => self::loose((string) $a) === self::loose((string) $b),
        };
    }

    /**
     * مقدار نهایی یک خانه. @return array{value:mixed, from:string, conflict:bool}
     *
     * @param array<string, mixed> $values نوشته کاربر
     * @param array<string, mixed> $import از فایل
     * @param array<string, string> $choices انتخاب کاربر
     */
    public static function effective(string $key, string $type, mixed $system, array $values, array $import, array $choices): array
    {
        $choice = $choices[$key] ?? null;
        $file = $import[$key] ?? null;
        if ($choice === 'own' || ($choice === null && array_key_exists($key, $values))) {
            return ['value' => $values[$key] ?? null, 'from' => 'own', 'conflict' => false];
        }
        if ($choice === 'file') {
            return ['value' => $file, 'from' => 'file', 'conflict' => false];
        }
        if ($choice === 'system') {
            return ['value' => $system, 'from' => 'system', 'conflict' => false];
        }
        if ($system !== null && $file !== null) {
            return ['value' => $system, 'from' => 'system', 'conflict' => !self::same($type, $system, $file)];
        }
        return $system !== null ? ['value' => $system, 'from' => 'system', 'conflict' => false]
            : ['value' => $file, 'from' => $file === null ? 'none' : 'file', 'conflict' => false];
    }

    /** نمایش یک مقدار با رقم فارسی، برای پنل، Word و اپ. */
    public static function display(string $type, mixed $value): string
    {
        if ($value === null || $value === '') {
            return '';
        }
        return match ($type) {
            'date' => Jalali::format((int) $value, false),
            'money', 'int' => Jalali::digits(number_format((float) $value)),
            'kw' => Jalali::digits(rtrim(rtrim(number_format((float) $value, 2), '0'), '.')),
            'tick' => '✓',
            default => Jalali::digits((string) $value),
        };
    }

    /** عدد از متن: رقم فارسی، جداکننده هزارگان، «ریال». */
    public static function number(mixed $raw): ?float
    {
        if (is_int($raw) || is_float($raw)) {
            return (float) $raw;
        }
        $text = Jalali::latin((string) $raw);
        $text = strtr($text, ['٫' => '.', '٬' => '', ',' => '', '،' => '', ' ' => '', 'ریال' => '', '/' => '.']);
        return is_numeric($text) ? (float) $text : null;
    }

    /** تاریخ: میلی‌ثانیه، عدد تاریخ اکسل، یا متن شمسی «۱۴۰۴/۰۵/۱۲». */
    public static function date(mixed $raw): ?int
    {
        if (is_int($raw) || is_float($raw)) {
            if ($raw > 100_000_000_000) {
                return (int) $raw;
            }
            if ($raw > 20_000 && $raw < 80_000) {
                // شماره روز اکسل (از ۱۸۹۹/۱۲/۳۰)، ظهر به وقت ایران تا روز جابه‌جا نشود.
                return ((int) $raw - 25569) * 86_400_000 + 12 * 3_600_000 - 12_600_000;
            }
            $raw = (string) (int) $raw;
        }
        $text = Jalali::latin(trim((string) $raw));
        if (preg_match('/^(\d{4})(\d{2})(\d{2})$/', $text, $m) === 1) {
            $text = "$m[1]/$m[2]/$m[3]";
        }
        if (preg_match('/^(\d{2})[\/\-.](\d{1,2})[\/\-.](\d{1,2})$/', $text, $m) === 1) {
            $text = '14' . $m[1] . "/$m[2]/$m[3]";
        }
        $parts = Jalali::parse($text);
        return $parts === null ? null : Jalali::startOfDay(...$parts) + 12 * 3_600_000;
    }

    /** ی و ک عربی، نیم‌فاصله و فاصله‌های چندتایی. */
    public static function cleanText(string $text): string
    {
        $text = strtr($text, ['ي' => 'ی', 'ك' => 'ک', 'ى' => 'ی', "\u{00A0}" => ' ', "\r" => ' ', "\n" => ' ', "\t" => ' ']);
        return trim((string) preg_replace('/ {2,}/u', ' ', $text));
    }

    /** برای مقایسه: بی فاصله، نیم‌فاصله، خط تیره و رقم فارسی. */
    public static function loose(string $text): string
    {
        $text = Jalali::latin(self::cleanText($text));
        return mb_strtolower((string) preg_replace('/[\s\x{200C}\x{200F}\x{200E}\-_\/\.،,()]+/u', '', $text));
    }

    /** @return array<string, mixed> */
    public static function decode(?string $json): array
    {
        if ($json === null || $json === '') {
            return [];
        }
        $data = json_decode($json, true);
        return is_array($data) ? $data : [];
    }

    public static function encode(array $data): ?string
    {
        $data = array_filter($data, static fn ($v) => $v !== null);
        return $data === [] ? null : json_encode($data, JSON_UNESCAPED_UNICODE);
    }
}
