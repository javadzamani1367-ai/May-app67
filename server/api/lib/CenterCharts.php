<?php
declare(strict_types=1);

/**
 * نمودارهای آمار جامع برای داشبورد مدیر (پنل و اپ): مراکز، ماینرها و کیلووات هر ماه
 * شمسی، هر ناحیه، وضعیت اقدام، ارگان شناسایی‌کننده، و مبلغ‌های صورتحساب و وصول.
 * پنل همین داده را با نمودار SVG خودش می‌کشد؛ اپ با نمودار Compose.
 */
final class CenterCharts
{
    /** آغاز و پایان سال شمسی، برای بازه پیش‌فرض. @return array{0:int, 1:int} */
    public static function year(int $year): array
    {
        return [Jalali::startOfDay($year, 1, 1), Jalali::startOfDay($year + 1, 1, 1) - 1];
    }

    /** نام برگه اکسل: سال، اگر بازه در یک سال است؛ وگرنه «کلی» مثل فرم. */
    public static function title(int $from, int $to): string
    {
        $year = FieldCodes::jalaliYear($from);
        return $year === FieldCodes::jalaliYear($to) ? (string) $year : 'کلی';
    }

    /** @return array<string, mixed> */
    public static function summary(array $rows): array
    {
        $months = [];
        $areas = [];
        $status = [];
        $orgs = ['org_sepah' => 0, 'org_vaja' => 0, 'org_faraja' => 0, 'org_other' => 0];
        $money = ['energy_amount' => 0.0, 'damage_amount' => 0.0, 'collected_amount' => 0.0, 'finder_reward' => 0.0,
            'collection_reward' => 0.0, 'police_reward' => 0.0, 'staff_reward' => 0.0];
        $totals = ['centers' => count($rows), 'miners' => 0, 'kw' => 0.0, 'conflicts' => 0, 'warnings' => 0, 'commission' => 0];
        foreach ($rows as $row) {
            $v = $row['values'];
            $month = $row['row_date'] === null ? '—' : mb_substr(Jalali::latin(Jalali::format((int) $row['row_date'], false)), 0, 7);
            $months[$month] ??= ['label' => $month, 'centers' => 0, 'miners' => 0, 'kw' => 0.0];
            $months[$month]['centers']++;
            $months[$month]['miners'] += (int) ($v['miners'] ?? 0);
            $kw = (float) ($v['capacity_kw'] ?? 0);
            $months[$month]['kw'] += $kw;
            $area = $row['area_code'] ?? '—';
            $areas[$area] ??= ['code' => $area, 'label' => $row['area_code'] ? Areas::label($row['area_code']) : 'بی ناحیه',
                'centers' => 0, 'miners' => 0];
            $areas[$area]['centers']++;
            $areas[$area]['miners'] += (int) ($v['miners'] ?? 0);
            $state = CenterValues::cleanText((string) ($v['status'] ?? '')) ?: 'نامشخص';
            $status[$state] = ($status[$state] ?? 0) + 1;
            foreach (array_keys($orgs) as $k) {
                $orgs[$k] += ($v[$k] ?? null) !== null && ($v[$k] ?? '') !== '' ? 1 : 0;
            }
            foreach (array_keys($money) as $k) {
                $money[$k] += (float) ($v[$k] ?? 0);
            }
            $totals['miners'] += (int) ($v['miners'] ?? 0);
            $totals['kw'] += $kw;
            $totals['conflicts'] += count($row['conflicts']);
            $totals['warnings'] += count($row['warnings']) > 0 ? 1 : 0;
            $totals['commission'] += ($v['commission'] ?? null) ? 1 : 0;
        }
        ksort($months);
        arsort($status);
        usort($areas, static fn ($a, $b) => $b['centers'] <=> $a['centers']);
        $totals['kw'] = round($totals['kw'], 1);
        return [
            'totals' => $totals,
            'months' => array_values(array_map(static fn ($m) => $m + ['kw' => round($m['kw'], 1)], $months)),
            'areas' => array_values($areas),
            'status' => array_map(static fn ($k, $n) => ['label' => (string) $k, 'count' => $n], array_keys($status), $status),
            'orgs' => array_map(static fn ($k, $n) => ['key' => $k, 'label' => CenterColumns::label($k), 'count' => $n],
                array_keys($orgs), $orgs),
            'money' => array_map(static fn ($k, $n) => ['key' => $k, 'label' => CenterColumns::label($k), 'amount' => round($n)],
                array_keys($money), $money),
        ];
    }

    /**
     * نمودار ستونی افقی ساده برای پنل، بی کتابخانه. @param list<array{0:string, 1:float}> $bars
     */
    public static function svg(array $bars, string $color = '#0f766e', bool $money = false): string
    {
        if ($bars === []) {
            return '<p class="muted">داده‌ای نیست.</p>';
        }
        $max = max(1.0, ...array_map(static fn ($b) => (float) $b[1], $bars));
        $h = 26;
        $height = count($bars) * $h + 8;
        $svg = '<svg viewBox="0 0 600 ' . $height . '" width="100%" role="img" style="max-width:640px;direction:ltr">';
        foreach (array_values($bars) as $i => [$label, $value]) {
            $y = 4 + $i * $h;
            $w = max(2, (int) round(360 * (float) $value / $max));
            $text = $money ? Jalali::digits(number_format((float) $value)) : Jalali::digits((string) round((float) $value, 1));
            $svg .= '<rect x="' . (590 - $w) . '" y="' . $y . '" width="' . $w . '" height="18" rx="3" fill="' . $color . '"/>'
                . '<text x="' . (584 - $w) . '" y="' . ($y + 14) . '" font-size="12" text-anchor="end" fill="#334155">' . htmlspecialchars($text) . '</text>'
                . '<text x="4" y="' . ($y + 14) . '" font-size="12" fill="#0f172a">' . htmlspecialchars(Jalali::digits($label)) . '</text>';
        }
        return $svg . '</svg>';
    }
}
