<?php
declare(strict_types=1);

/**
 * تبدیل تاریخ میلادی به شمسی.
 *
 * این دقیقاً همان الگوریتم Borkowski است که در اپ اندروید
 * (`util/PersianDate.kt`) و در نرم‌افزار ویندوز پیاده شده. عمداً از یک
 * الگوریتم ساده‌تر استفاده نشده: دو الگوریتم متفاوت در یک سامانه یعنی روزی
 * تاریخ یک پرونده روی گوشی با تاریخ همان پرونده در پرتال فرق کند.
 *
 * ذخیره‌سازی همیشه میلی‌ثانیه یونیکس است؛ تبدیل فقط برای نمایش انجام می‌شود.
 */
final class Jalali
{
    /** تهران، تنها منطقه زمانی مدنی این سامانه. */
    public const ZONE = 'Asia/Tehran';

    private const BREAKS = [
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    ];

    /** @return array{leap:int, gy:int, march:int} */
    private static function cal(int $jy): array
    {
        $gy = $jy + 621;
        $leapJ = -14;
        $jp = self::BREAKS[0];
        $jump = 0;
        $count = count(self::BREAKS);
        for ($i = 1; $i < $count; $i++) {
            $jm = self::BREAKS[$i];
            $jump = $jm - $jp;
            if ($jy < $jm) {
                break;
            }
            $leapJ += intdiv($jump, 33) * 8 + intdiv($jump % 33, 4);
            $jp = $jm;
        }
        $n = $jy - $jp;
        $leapJ += intdiv($n, 33) * 8 + intdiv(($n % 33) + 3, 4);
        if ($jump % 33 === 4 && $jump - $n === 4) {
            $leapJ++;
        }

        $leapG = intdiv($gy, 4) - intdiv((intdiv($gy, 100) + 1) * 3, 4) - 150;
        $march = 20 + $leapJ - $leapG;

        if ($jump - $n < 6) {
            $n = $n - $jump + intdiv($jump + 4, 33) * 33;
        }
        $leap = ((($n + 1) % 33) - 1) % 4;
        if ($leap === -1) {
            $leap = 4;
        }
        return ['leap' => $leap, 'gy' => $gy, 'march' => $march];
    }

    private static function gregorianToDayNumber(int $gy, int $gm, int $gd): int
    {
        $d = intdiv(($gy + intdiv($gm - 8, 6) + 100100) * 1461, 4)
            + intdiv(153 * (($gm + 9) % 12) + 2, 5) + $gd - 34840408;
        $d -= intdiv(intdiv(($gy + 100100 + intdiv($gm - 8, 6)), 100) * 3, 4) - 752;
        return $d;
    }

    /** @return array{0:int, 1:int, 2:int} */
    private static function dayNumberToGregorian(int $dayNumber): array
    {
        $j = 4 * $dayNumber + 139361631;
        $j += intdiv(intdiv(4 * $dayNumber + 183187720, 146097) * 3, 4) * 4 - 3908;
        $i = intdiv($j % 1461, 4) * 5 + 308;
        $gd = intdiv($i % 153, 5) + 1;
        $gm = intdiv($i, 153) % 12 + 1;
        $gy = intdiv($j, 1461) - 100100 + intdiv(8 - $gm, 6);
        return [$gy, $gm, $gd];
    }

    /** @return array{0:int, 1:int, 2:int} سال، ماه، روز شمسی */
    public static function fromGregorian(int $gy, int $gm, int $gd): array
    {
        $dayNumber = self::gregorianToDayNumber($gy, $gm, $gd);
        [$year] = self::dayNumberToGregorian($dayNumber);
        $jy = $year - 621;
        $r = self::cal($jy);
        $firstDay = self::gregorianToDayNumber($r['gy'], 3, $r['march']);
        $k = $dayNumber - $firstDay;
        if ($k >= 0) {
            if ($k <= 185) {
                return [$jy, 1 + intdiv($k, 31), ($k % 31) + 1];
            }
            $k -= 186;
        } else {
            $jy--;
            $k += 179;
            if ($r['leap'] === 1) {
                $k++;
            }
        }
        return [$jy, 7 + intdiv($k, 30), ($k % 30) + 1];
    }

    /** `۱۴۰۵/۰۶/۲۷ - ۰۹:۳۵` — همان شکلی که اپ نشان می‌دهد. */
    public static function format(?int $millis, bool $withTime = true): string
    {
        if ($millis === null || $millis === 0) {
            return '—';
        }
        $date = (new DateTimeImmutable('@' . intdiv($millis, 1000)))
            ->setTimezone(new DateTimeZone(self::ZONE));
        [$jy, $jm, $jd] = self::fromGregorian(
            (int) $date->format('Y'),
            (int) $date->format('n'),
            (int) $date->format('j')
        );
        $text = sprintf('%04d/%02d/%02d', $jy, $jm, $jd);
        if ($withTime) {
            $text .= ' - ' . $date->format('H:i');
        }
        return self::digits($text);
    }

    public static function digits(string $value): string
    {
        return strtr($value, [
            '0' => '۰', '1' => '۱', '2' => '۲', '3' => '۳', '4' => '۴',
            '5' => '۵', '6' => '۶', '7' => '۷', '8' => '۸', '9' => '۹',
        ]);
    }
}
