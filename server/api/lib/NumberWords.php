<?php
declare(strict_types=1);

/** عدد به حروف فارسی، برای «مبلغ به حروف» در فرم‌های رسمی: ۲۰۵۰۰۰۰۰۰ → «دویست و پنج میلیون». */
final class NumberWords
{
    private const ONES = ['', 'یک', 'دو', 'سه', 'چهار', 'پنج', 'شش', 'هفت', 'هشت', 'نه', 'ده', 'یازده', 'دوازده', 'سیزده',
        'چهارده', 'پانزده', 'شانزده', 'هفده', 'هجده', 'نوزده'];
    private const TENS = ['', '', 'بیست', 'سی', 'چهل', 'پنجاه', 'شصت', 'هفتاد', 'هشتاد', 'نود'];
    private const HUNDREDS = ['', 'یکصد', 'دویست', 'سیصد', 'چهارصد', 'پانصد', 'ششصد', 'هفتصد', 'هشتصد', 'نهصد'];
    private const SCALES = ['', 'هزار', 'میلیون', 'میلیارد', 'هزار میلیارد'];

    public static function of(int $n): string
    {
        if ($n === 0) {
            return 'صفر';
        }
        if ($n < 0) {
            return 'منفی ' . self::of(-$n);
        }
        $parts = [];
        for ($scale = 0; $n > 0 && $scale < count(self::SCALES); $scale++, $n = intdiv($n, 1000)) {
            $group = $n % 1000;
            if ($group > 0) {
                $parts[] = trim(self::group($group) . ' ' . self::SCALES[$scale]);
            }
        }
        return implode(' و ', array_reverse($parts));
    }

    /** «… ریال» */
    public static function rial(float $amount): string
    {
        return self::of((int) round($amount)) . ' ریال';
    }

    private static function group(int $n): string
    {
        $words = [];
        if ($n >= 100) {
            $words[] = self::HUNDREDS[intdiv($n, 100)];
            $n %= 100;
        }
        if ($n >= 20) {
            $words[] = self::TENS[intdiv($n, 10)];
            $n %= 10;
        }
        if ($n > 0) {
            $words[] = self::ONES[$n];
        }
        return implode(' و ', $words);
    }
}
