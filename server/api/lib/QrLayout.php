<?php
declare(strict_types=1);

/**
 * چیدمان ماژول‌های کد QR: الگوهای ثابت (یاب، هم‌ترازی، زمان‌بندی)، چیدن
 * بیت‌ها، ماسک‌ها، اطلاعات قالب و نسخه، و جریمه‌ای که ماسک بهتر را برمی‌گزیند.
 * داده و تصحیح خطا در Qr.php ساخته می‌شود.
 */
final class QrLayout
{
    public const ALIGN = [
        1 => [], 2 => [6, 18], 3 => [6, 22], 4 => [6, 26], 5 => [6, 30], 6 => [6, 34],
        7 => [6, 22, 38], 8 => [6, 24, 42], 9 => [6, 26, 46], 10 => [6, 28, 50],
    ];

    /** @return array{0: array<int, array<int, bool>>, 1: array<int, array<int, bool>>} */
    public static function functionPatterns(int $version, int $size): array
    {
        $m = array_fill(0, $size, array_fill(0, $size, false));
        $r = array_fill(0, $size, array_fill(0, $size, false));
        $set = static function (int $x, int $y, bool $dark) use (&$m, &$r): void {
            $m[$y][$x] = $dark;
            $r[$y][$x] = true;
        };
        foreach ([[0, 0], [$size - 7, 0], [0, $size - 7]] as [$fx, $fy]) {
            for ($dy = -1; $dy <= 7; $dy++) {
                for ($dx = -1; $dx <= 7; $dx++) {
                    $x = $fx + $dx;
                    $y = $fy + $dy;
                    if ($x < 0 || $y < 0 || $x >= $size || $y >= $size) {
                        continue;
                    }
                    $inside = $dx >= 0 && $dx <= 6 && $dy >= 0 && $dy <= 6;
                    $ring = $dx === 0 || $dx === 6 || $dy === 0 || $dy === 6;
                    $core = $dx >= 2 && $dx <= 4 && $dy >= 2 && $dy <= 4;
                    $set($x, $y, $inside && ($ring || $core));
                }
            }
        }
        for ($i = 8; $i < $size - 8; $i++) {
            $set($i, 6, $i % 2 === 0);
            $set(6, $i, $i % 2 === 0);
        }
        $centres = self::ALIGN[$version];
        $last = count($centres) - 1;
        foreach ($centres as $i => $cy) {
            foreach ($centres as $j => $cx) {
                // سه‌تایی که روی یاب‌های گوشه می‌افتند کشیده نمی‌شوند؛ آن‌هایی که روی خط زمان‌بندی‌اند، چرا.
                if (($i === 0 && $j === 0) || ($i === 0 && $j === $last) || ($i === $last && $j === 0)) {
                    continue;
                }
                for ($dy = -2; $dy <= 2; $dy++) {
                    for ($dx = -2; $dx <= 2; $dx++) {
                        $set($cx + $dx, $cy + $dy, max(abs($dx), abs($dy)) !== 1);
                    }
                }
            }
        }
        // جای اطلاعات قالب و نسخه، و ماژول همیشه سیاه.
        for ($i = 0; $i < 9; $i++) {
            $r[8][$i] = $r[$i][8] = true;
        }
        for ($i = 0; $i < 8; $i++) {
            $r[8][$size - 1 - $i] = $r[$size - 1 - $i][8] = true;
        }
        $set(8, $size - 8, true);
        if ($version >= 7) {
            for ($i = 0; $i < 6; $i++) {
                for ($j = 0; $j < 3; $j++) {
                    $r[$i][$size - 11 + $j] = $r[$size - 11 + $j][$i] = true;
                }
            }
        }
        return [$m, $r];
    }
    /** چیدن بیت‌ها به شکل زیگزاگ دوستونی، از پایین راست. */
    public static function place(array $m, array $reserved, array $codewords, int $size): array
    {
        $bits = '';
        foreach ($codewords as $cw) {
            $bits .= str_pad(decbin($cw), 8, '0', STR_PAD_LEFT);
        }
        $index = 0;
        $total = strlen($bits);
        $upward = true;
        for ($right = $size - 1; $right >= 1; $right -= 2) {
            if ($right === 6) {
                $right = 5;
            }
            for ($k = 0; $k < $size; $k++) {
                $y = $upward ? $size - 1 - $k : $k;
                for ($c = 0; $c < 2; $c++) {
                    $x = $right - $c;
                    if (!$reserved[$y][$x]) {
                        $m[$y][$x] = $index < $total && $bits[$index] === '1';
                        $index++;
                    }
                }
            }
            $upward = !$upward;
        }
        return $m;
    }
    public static function applyMask(array $m, array $reserved, int $mask, int $size): array
    {
        for ($y = 0; $y < $size; $y++) {
            for ($x = 0; $x < $size; $x++) {
                if ($reserved[$y][$x]) {
                    continue;
                }
                $flip = match ($mask) {
                    0 => ($x + $y) % 2 === 0,
                    1 => $y % 2 === 0,
                    2 => $x % 3 === 0,
                    3 => ($x + $y) % 3 === 0,
                    4 => (intdiv($y, 2) + intdiv($x, 3)) % 2 === 0,
                    5 => ($x * $y) % 2 + ($x * $y) % 3 === 0,
                    6 => (($x * $y) % 2 + ($x * $y) % 3) % 2 === 0,
                    default => (($x + $y) % 2 + ($x * $y) % 3) % 2 === 0,
                };
                if ($flip) {
                    $m[$y][$x] = !$m[$y][$x];
                }
            }
        }
        return $m;
    }
    private static function bch(int $value, int $poly, int $polyBits): int
    {
        $v = $value << ($polyBits - 1);
        for ($i = 31; $i >= $polyBits - 1; $i--) {
            if (($v >> $i) & 1) {
                $v ^= $poly << ($i - $polyBits + 1);
            }
        }
        return ($value << ($polyBits - 1)) | $v;
    }
    public static function drawFormat(array &$m, int $mask, int $size): void
    {
        // سطح M = 00. همان چینش استاندارد، [y][x].
        $bits = self::bch($mask, 0x537, 11) ^ 0x5412;
        $bit = static fn (int $i): bool => (($bits >> $i) & 1) === 1;
        for ($i = 0; $i <= 5; $i++) {
            $m[$i][8] = $bit($i);
        }
        $m[7][8] = $bit(6);
        $m[8][8] = $bit(7);
        $m[8][7] = $bit(8);
        for ($i = 9; $i < 15; $i++) {
            $m[8][14 - $i] = $bit($i);
        }
        for ($i = 0; $i < 8; $i++) {
            $m[8][$size - 1 - $i] = $bit($i);
        }
        for ($i = 8; $i < 15; $i++) {
            $m[$size - 15 + $i][8] = $bit($i);
        }
        $m[$size - 8][8] = true;
    }
    public static function drawVersion(array &$m, int $version, int $size): void
    {
        $bits = self::bch($version, 0x1F25, 13);
        for ($i = 0; $i < 18; $i++) {
            $dark = (($bits >> $i) & 1) === 1;
            $a = intdiv($i, 3);
            $b = $i % 3 + $size - 11;
            $m[$a][$b] = $dark;
            $m[$b][$a] = $dark;
        }
    }
    /** جریمه استاندارد چهار قاعده؛ ماسک با کمترین جریمه خواناتر است. */
    public static function penalty(array $m, int $size): int
    {
        $score = 0;
        $dark = 0;
        for ($pass = 0; $pass < 2; $pass++) {
            for ($i = 0; $i < $size; $i++) {
                $run = 1;
                $line = '';
                for ($j = 0; $j < $size; $j++) {
                    $v = $pass === 0 ? $m[$i][$j] : $m[$j][$i];
                    $line .= $v ? '1' : '0';
                    if ($j > 0) {
                        $prev = $pass === 0 ? $m[$i][$j - 1] : $m[$j - 1][$i];
                        if ($v === $prev) {
                            $run++;
                        } else {
                            $score += $run >= 5 ? $run - 2 : 0;
                            $run = 1;
                        }
                    }
                }
                $score += $run >= 5 ? $run - 2 : 0;
                $score += 40 * (substr_count($line, '10111010000') + substr_count($line, '00001011101'));
            }
        }
        for ($y = 0; $y < $size; $y++) {
            for ($x = 0; $x < $size; $x++) {
                $dark += $m[$y][$x] ? 1 : 0;
                if ($x < $size - 1 && $y < $size - 1 && $m[$y][$x] === $m[$y][$x + 1]
                    && $m[$y][$x] === $m[$y + 1][$x] && $m[$y][$x] === $m[$y + 1][$x + 1]) {
                    $score += 3;
                }
            }
        }
        $total = $size * $size;
        return $score + max(0, (int) ceil(abs($dark * 20 - $total * 10) / $total) - 1) * 10;
    }
}
