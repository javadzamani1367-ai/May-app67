<?php
declare(strict_types=1);

/**
 * کد QR بدون کتابخانه: حالت بایت، تصحیح خطای سطح M، نسخه ۱ تا ۱۰ (تا ۲۱۳ بایت).
 * برای برگه ارجاع کافی است — یک شناسه یا یک نشانی geo: چند ده بایت است.
 * خروجی SVG است تا روی کاغذ تیز چاپ شود.
 *
 * درستی‌اش با کتابخانه qrcode پایتون (ماتریس به ماتریس، با ماسک یکسان) و با
 * رمزگشای zxing سنجیده شده؛ تست run.php همان ماتریس‌ها را نگه می‌دارد.
 */
final class Qr
{
    /** برای هر نسخه: [کدواژه تصحیح هر بلوک، [تعداد بلوک، کدواژه داده هر بلوک]...] */
    private const BLOCKS = [
        1 => [10, [1, 16]], 2 => [16, [1, 28]], 3 => [26, [1, 44]], 4 => [18, [2, 32]],
        5 => [24, [2, 43]], 6 => [16, [4, 27]], 7 => [18, [4, 31]], 8 => [22, [2, 38], [2, 39]],
        9 => [22, [3, 36], [2, 37]], 10 => [26, [4, 43], [1, 44]],
    ];

    private const ALIGN = [
        1 => [], 2 => [6, 18], 3 => [6, 22], 4 => [6, 26], 5 => [6, 30], 6 => [6, 34],
        7 => [6, 22, 38], 8 => [6, 24, 42], 9 => [6, 26, 46], 10 => [6, 28, 50],
    ];

    /** @var array<int, int> */
    private static array $exp = [];
    /** @var array<int, int> */
    private static array $log = [];

    /**
     * ماتریس ماژول‌ها: true یعنی سیاه. [$mask] فقط برای تست؛ در حالت عادی
     * ماسکی که کمترین جریمه را دارد انتخاب می‌شود.
     *
     * @return array<int, array<int, bool>>
     */
    public static function matrix(string $data, ?int $mask = null): array
    {
        $version = self::version(strlen($data));
        $codewords = self::codewords($data, $version);
        $size = 17 + 4 * $version;
        [$base, $reserved] = self::functionPatterns($version, $size);
        $filled = self::place($base, $reserved, $codewords, $size);

        $best = null;
        $bestScore = PHP_INT_MAX;
        foreach ($mask === null ? range(0, 7) : [$mask] as $m) {
            $candidate = self::applyMask($filled, $reserved, $m, $size);
            self::drawFormat($candidate, $m, $size);
            if ($version >= 7) {
                self::drawVersion($candidate, $version, $size);
            }
            $score = $mask === null ? self::penalty($candidate, $size) : 0;
            if ($score < $bestScore) {
                $best = $candidate;
                $bestScore = $score;
            }
        }
        return $best;
    }

    /** SVG با حاشیه سفید چهارماژولی که اسکنرها لازم دارند. */
    public static function svg(string $data, int $pixels = 160): string
    {
        $m = self::matrix($data);
        $size = count($m);
        $full = $size + 8;
        $path = '';
        foreach ($m as $y => $row) {
            foreach ($row as $x => $dark) {
                if ($dark) {
                    $path .= 'M' . ($x + 4) . ' ' . ($y + 4) . 'h1v1h-1z';
                }
            }
        }
        return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' . $full . ' ' . $full . '" width="' . $pixels
            . '" height="' . $pixels . '" shape-rendering="crispEdges"><rect width="' . $full . '" height="' . $full
            . '" fill="#fff"/><path d="' . $path . '" fill="#000"/></svg>';
    }

    private static function version(int $bytes): int
    {
        foreach (self::BLOCKS as $version => $spec) {
            $dataBytes = 0;
            foreach (array_slice($spec, 1) as [$count, $per]) {
                $dataBytes += $count * $per;
            }
            $countBits = $version < 10 ? 8 : 16;
            if (4 + $countBits + 8 * $bytes <= 8 * $dataBytes) {
                return $version;
            }
        }
        throw new InvalidArgumentException('QR payload too long');
    }

    /** @return array<int, int> داده و تصحیح خطا، درهم‌شده به ترتیب استاندارد */
    private static function codewords(string $data, int $version): array
    {
        $spec = self::BLOCKS[$version];
        $ecPer = $spec[0];
        $groups = array_slice($spec, 1);
        $capacity = 0;
        foreach ($groups as [$count, $per]) {
            $capacity += $count * $per;
        }
        $bits = '0100' . str_pad(decbin(strlen($data)), $version < 10 ? 8 : 16, '0', STR_PAD_LEFT);
        foreach (str_split($data) as $ch) {
            $bits .= str_pad(decbin(ord($ch)), 8, '0', STR_PAD_LEFT);
        }
        $bits .= str_repeat('0', min(4, $capacity * 8 - strlen($bits)));
        $bits .= str_repeat('0', (8 - strlen($bits) % 8) % 8);
        $bytes = array_map('bindec', str_split($bits, 8));
        for ($i = 0; count($bytes) < $capacity; $i++) {
            $bytes[] = $i % 2 === 0 ? 0xEC : 0x11;
        }

        $dataBlocks = [];
        $ecBlocks = [];
        $offset = 0;
        foreach ($groups as [$count, $per]) {
            for ($b = 0; $b < $count; $b++) {
                $block = array_slice($bytes, $offset, $per);
                $offset += $per;
                $dataBlocks[] = $block;
                $ecBlocks[] = self::reedSolomon($block, $ecPer);
            }
        }
        $out = [];
        $longest = max(array_map('count', $dataBlocks));
        for ($i = 0; $i < $longest; $i++) {
            foreach ($dataBlocks as $block) {
                if (isset($block[$i])) {
                    $out[] = $block[$i];
                }
            }
        }
        for ($i = 0; $i < $ecPer; $i++) {
            foreach ($ecBlocks as $block) {
                $out[] = $block[$i];
            }
        }
        return $out;
    }

    /** @return array<int, int> */
    private static function reedSolomon(array $data, int $degree): array
    {
        if (self::$exp === []) {
            $x = 1;
            for ($i = 0; $i < 255; $i++) {
                self::$exp[$i] = $x;
                self::$log[$x] = $i;
                $x <<= 1;
                if ($x & 0x100) {
                    $x ^= 0x11D;
                }
            }
        }
        $gen = [1];
        for ($i = 0; $i < $degree; $i++) {
            $next = array_fill(0, count($gen) + 1, 0);
            foreach ($gen as $j => $coef) {
                $next[$j] ^= $coef;
                $next[$j + 1] ^= self::mul($coef, self::$exp[$i]);
            }
            $gen = $next;
        }
        $rem = array_merge($data, array_fill(0, $degree, 0));
        for ($i = 0, $n = count($data); $i < $n; $i++) {
            $factor = $rem[$i];
            if ($factor !== 0) {
                foreach ($gen as $j => $coef) {
                    $rem[$i + $j] ^= self::mul($coef, $factor);
                }
            }
        }
        return array_slice($rem, count($data));
    }

    private static function mul(int $a, int $b): int
    {
        return $a === 0 || $b === 0 ? 0 : self::$exp[(self::$log[$a] + self::$log[$b]) % 255];
    }

    /** @return array{0: array<int, array<int, bool>>, 1: array<int, array<int, bool>>} */
    private static function functionPatterns(int $version, int $size): array
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
    private static function place(array $m, array $reserved, array $codewords, int $size): array
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

    private static function applyMask(array $m, array $reserved, int $mask, int $size): array
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

    private static function drawFormat(array &$m, int $mask, int $size): void
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

    private static function drawVersion(array &$m, int $version, int $size): void
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
    private static function penalty(array $m, int $size): int
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
