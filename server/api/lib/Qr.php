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
require_once __DIR__ . '/QrLayout.php';

final class Qr
{
    /** برای هر نسخه: [کدواژه تصحیح هر بلوک، [تعداد بلوک، کدواژه داده هر بلوک]...] */
    private const BLOCKS = [
        1 => [10, [1, 16]], 2 => [16, [1, 28]], 3 => [26, [1, 44]], 4 => [18, [2, 32]],
        5 => [24, [2, 43]], 6 => [16, [4, 27]], 7 => [18, [4, 31]], 8 => [22, [2, 38], [2, 39]],
        9 => [22, [3, 36], [2, 37]], 10 => [26, [4, 43], [1, 44]],
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
        [$base, $reserved] = QrLayout::functionPatterns($version, $size);
        $filled = QrLayout::place($base, $reserved, $codewords, $size);

        $best = null;
        $bestScore = PHP_INT_MAX;
        foreach ($mask === null ? range(0, 7) : [$mask] as $m) {
            $candidate = QrLayout::applyMask($filled, $reserved, $m, $size);
            QrLayout::drawFormat($candidate, $m, $size);
            if ($version >= 7) {
                QrLayout::drawVersion($candidate, $version, $size);
            }
            $score = $mask === null ? QrLayout::penalty($candidate, $size) : 0;
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

}
