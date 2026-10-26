<?php
declare(strict_types=1);

/**
 * پیدا کردن سرستون و ستون‌های یک برگه اکسل آمار، هر جا که باشند و به هر ترتیبی.
 *
 * سرستون فرم توانیر چند سطری و ادغام‌شده است؛ برای هر ستون فایل «مسیر» سرستونش ساخته
 * می‌شود (متن هر سطر سرستون بالای آن، با ادغام‌ها) و با مسیر هر ستون آمار مقایسه
 * می‌شود: نام پایین‌ترین سطح امتیاز اصلی است و گروه بالایش ابهام را برطرف می‌کند
 * («تعداد شناسایی شده» زیر «با داشتن انشعاب» یا «بدون داشتن انشعاب»).
 */
final class CenterHeader
{
    /** نام‌های دیگری که در فایل‌های دیگر برای همان ستون دیده می‌شود. */
    public const ALIASES = [
        'row' => ['ردیف', 'ردیف'], 'county' => ['شهرستان', 'بخش', 'شهرستان بخش'], 'report_date' => ['تاریخ گزارش', 'تاریخ'],
        'finder_name' => ['نام عامل شناسایی', 'عامل شناسایی'], 'sheba' => ['شبا', 'شماره شبا'],
        'miners' => ['تعداد ماینر', 'تعداد دستگاه', 'تعداد ماینر کشف شده'], 'sms_code' => ['کد پیگیری', 'کد سراغ'],
        'capacity_kw' => ['ظرفیت برآوردی', 'مجموع ظرفیت'], 'bill_id' => ['شناسه قبض'], 'subscriber' => ['نام مشترک'],
        'subscription' => ['اشتراک'], 'file_number' => ['پرونده'], 'commission' => ['کمیسیون'],
        'status' => ['وضعیت', 'وضعیت پرونده'], 'tracking_code' => ['کد رهگیری'], 'license' => ['مجوز', 'پروانه بهره برداری'],
        'meter_type' => ['کنتور'], 'coordinates' => ['مختصات', 'موقعیت'], 'address' => ['آدرس', 'نشانی'],
    ];

    /**
     * سرستون برگه: سطر اول و آخر سرستون و ستون فایل هر کلید آمار.
     *
     * @param array{cells:array, merges:list<array>} $sheet
     * @param array<string, list<list<string>>> $defs کلید → مسیرهای ممکن (هر مسیر از بالا به پایین)
     * @return array{top:int, bottom:int, map:array<string, array{0:int, 1:int}>, unknown:list<string>}|null
     *         map: کلید → [ستون اول، ستون آخر] در فایل
     */
    public static function detect(array $sheet, array $defs): ?array
    {
        $vocab = [];
        foreach ($defs as $paths) {
            foreach ($paths as $path) {
                foreach ($path as $label) {
                    $vocab[CenterValues::loose($label)] = true;
                }
            }
        }
        $scores = [];
        foreach ($sheet['cells'] as $r => $cells) {
            if ($r > 20) {
                break;
            }
            $scores[$r] = 0;
            foreach ($cells as $value) {
                if (is_string($value) && isset($vocab[CenterValues::loose($value)])) {
                    $scores[$r]++;
                }
            }
        }
        $top = null;
        foreach ($scores as $r => $score) {
            if ($score >= 3) {
                $top = $r;
                break;
            }
        }
        if ($top === null) {
            return null;
        }
        $bottom = $top;
        for ($r = $top + 1; $r <= $top + 5; $r++) {
            $first = $sheet['cells'][$r][0] ?? null;
            if (($scores[$r] ?? 0) < 1 && !self::insideMerge($sheet['merges'], $r, $top) || is_float($first)) {
                break;
            }
            $bottom = $r;
        }
        $paths = self::paths($sheet, $top, $bottom);
        $candidates = [];
        foreach ($defs as $key => $options) {
            foreach ($paths as $col => $path) {
                $best = 0;
                foreach ($options as $option) {
                    $best = max($best, self::score($option, $path['texts']));
                }
                if ($best >= 5) {
                    $candidates[] = [$best, $key, $col];
                }
            }
        }
        usort($candidates, static fn ($a, $b) => $b[0] <=> $a[0] ?: $a[2] <=> $b[2]);
        $map = [];
        $used = [];
        foreach ($candidates as [, $key, $col]) {
            if (!isset($map[$key]) && !isset($used[$col])) {
                $map[$key] = [$col, $paths[$col]['last']];
                $used[$col] = true;
            }
        }
        $unknown = [];
        foreach ($paths as $col => $path) {
            if (!isset($used[$col]) && $path['texts'] !== []) {
                $unknown[] = $path['label'];
            }
        }
        return ['top' => $top, 'bottom' => $bottom, 'map' => $map, 'unknown' => array_values(array_unique($unknown))];
    }

    /** امتیاز یک مسیر تعریف با متن‌های سرستون یک ستون فایل. */
    private static function score(array $option, array $texts): int
    {
        if ($texts === []) {
            return 0;
        }
        $leaf = CenterValues::loose((string) end($option));
        $last = end($texts);
        $score = 0;
        if ($last === $leaf) {
            $score = 10;
        } elseif (in_array($leaf, $texts, true)) {
            $score = 6;
        } elseif (mb_strlen($leaf) >= 4 && mb_strlen($last) >= 4 && (str_contains($last, $leaf) || str_contains($leaf, $last))) {
            $score = 5;
        }
        if ($score === 0) {
            return 0;
        }
        foreach (array_slice($option, 0, -1) as $group) {
            if (in_array(CenterValues::loose($group), $texts, true)) {
                $score += 2;
            }
        }
        return $score;
    }

    /**
     * مسیر سرستون هر ستون فایل که سرستون دارد؛ ستون‌هایی که زیر یک خانه ادغام‌شده‌اند
     * یکی حساب می‌شوند. @return array<int, array{texts:list<string>, label:string, last:int}>
     */
    private static function paths(array $sheet, int $top, int $bottom): array
    {
        $grid = [];
        foreach ($sheet['merges'] as [$r1, $c1, $r2, $c2]) {
            if ($r2 < $top || $r1 > $bottom) {
                continue;
            }
            $value = $sheet['cells'][$r1][$c1] ?? null;
            for ($r = max($r1, $top); $r <= min($r2, $bottom); $r++) {
                for ($c = $c1; $c <= $c2; $c++) {
                    $grid[$r][$c] = ['v' => $value, 'span' => [$c1, $c2]];
                }
            }
        }
        $maxCol = 0;
        for ($r = $top; $r <= $bottom; $r++) {
            foreach ($sheet['cells'][$r] ?? [] as $c => $value) {
                $grid[$r][$c] ??= ['v' => $value, 'span' => [$c, $c]];
                $maxCol = max($maxCol, $c);
            }
            $maxCol = max($maxCol, ...array_keys($grid[$r] ?? [0 => 0]));
        }
        $paths = [];
        for ($c = 0; $c <= $maxCol; $c++) {
            $texts = [];
            $labels = [];
            for ($r = $top; $r <= $bottom; $r++) {
                $cell = $grid[$r][$c] ?? null;
                if ($cell === null || $cell['v'] === null || $cell['v'] === '') {
                    continue;
                }
                $loose = CenterValues::loose((string) $cell['v']);
                if ($loose !== '' && end($texts) !== $loose) {
                    $texts[] = $loose;
                    $labels[] = CenterValues::cleanText((string) $cell['v']);
                }
            }
            $last = $grid[$bottom][$c]['span'][1] ?? $c;
            $first = $grid[$bottom][$c]['span'][0] ?? $c;
            if ($first !== $c && isset($paths[$first]) && ($grid[$bottom][$c]['v'] ?? null) !== null) {
                continue;
            }
            $paths[$c] = ['texts' => $texts, 'label' => implode(' / ', $labels), 'last' => max($c, $last)];
        }
        return $paths;
    }

    private static function insideMerge(array $merges, int $row, int $top): bool
    {
        foreach ($merges as [$r1, , $r2]) {
            if ($r1 >= $top && $r1 < $row && $r2 >= $row) {
                return true;
            }
        }
        return false;
    }
}
