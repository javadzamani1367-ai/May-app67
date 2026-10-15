<?php
declare(strict_types=1);

require_once __DIR__ . '/PositionAudit.php';

/**
 * آمپرگیری تیربه‌تیر یک فیدر (نوع FT) برای خواندن آدم: ردیف‌های خلاصه، جدول
 * تیرها با افت هر بازه، و نمودار SVG جریان سه فاز در طول مسیر.
 *
 * داوری «بار قابل توجه» و «افزایش غیرعادی» را گوشی با آستانه همان روز کرده و
 * در payload.analysis فرستاده (LineAnalysis.kt)؛ اینجا همان نشان داده می‌شود
 * و فقط تفاضل‌ها — که حساب ساده‌اند — دوباره حساب می‌شوند.
 */
final class FieldLine
{
    public const NETWORKS = ['overhead' => 'سیم هوایی', 'abc' => 'کابل خودنگهدار', 'underground' => 'کابل زمینی'];

    private const PHASE_COLORS = ['#dc2626', '#d97706', '#2563eb'];

    /**
     * نقطه‌های دارای آمپر: تابلو (شماره ۰) اگر خوانده شده، و هر تیر اندازه‌گیری‌شده.
     *
     * @return list<array{pole:int, phases:list<float>, total:float}>
     */
    public static function points(array $p): array
    {
        $points = [];
        $start = self::phases($p['start'] ?? null);
        if ($start !== null) {
            $points[] = ['pole' => 0, 'phases' => $start, 'total' => array_sum($start)];
        }
        foreach (array_values((array) ($p['poles'] ?? [])) as $i => $pole) {
            $pole = (array) $pole;
            $phases = ($pole['state'] ?? 'measured') === 'measured' ? self::phases($pole['phases'] ?? null) : null;
            if ($phases !== null) {
                $points[] = ['pole' => $i + 1, 'phases' => $phases, 'total' => array_sum($phases)];
            }
        }
        return $points;
    }

    /** @return list<float>|null */
    private static function phases(mixed $value): ?array
    {
        $v = (array) $value;
        foreach (['r', 's', 't'] as $k) {
            if (!is_numeric($v[$k] ?? null)) {
                return null;
            }
        }
        return [(float) $v['r'], (float) $v['s'], (float) $v['t']];
    }

    public static function pointName(int $pole): string
    {
        return $pole === 0 ? 'تابلو' : 'تیر ' . Jalali::digits((string) $pole);
    }

    private static function amps(float $value): string
    {
        return Jalali::digits((string) (round($value * 10) / 10));
    }

    /** خلاصه برای چک‌لیست مورد، برگه ارجاع و Word. @return array<int, array{0:string, 1:?string}> */
    public static function rows(array $p): array
    {
        $feeder = (string) ($p['feeder'] ?? '');
        $poles = (array) ($p['poles'] ?? []);
        $points = self::points($p);
        $analysis = (array) ($p['analysis'] ?? []);
        $rows = [
            ['فیدر', $feeder === 'other' ? (string) ($p['feeder_name'] ?? '') : ($feeder === '' ? null : 'فیدر ' . $feeder)],
            ['نوع شبکه', self::NETWORKS[$p['network'] ?? ''] ?? null],
            ['ابتدای فیدر در تابلو (آمپر)', isset($p['start']) ? FieldPresenter::phasesText($p['start']) : null],
            ['تیرها', Jalali::digits((string) count($poles)) . ' تیر، '
                . Jalali::digits((string) count(array_filter($points, static fn ($pt) => $pt['pole'] > 0))) . ' اندازه‌گیری‌شده'],
        ];
        $heaviest = (array) ($analysis['heaviest'] ?? []);
        if ($heaviest !== []) {
            $rows[] = ['بیشترین افت', 'بین ' . self::pointName((int) $heaviest['from']) . ' و ' . self::pointName((int) $heaviest['to'])
                . '، ' . self::amps((float) $heaviest['drop']) . ' آمپر'];
        }
        $heavy = array_map(static fn ($s) => self::pointName((int) $s['from']) . ' ← ' . self::pointName((int) $s['to'])
            . ' (' . self::amps((float) $s['drop']) . ')', (array) ($analysis['heavy'] ?? []));
        $rows[] = ['بار قابل توجه', implode('، ', $heavy)];
        $rises = array_map(static fn ($s) => self::pointName((int) $s['from']) . ' ← ' . self::pointName((int) $s['to']),
            (array) ($analysis['rises'] ?? []));
        $rows[] = ['افزایش غیرعادی جریان', implode('، ', $rises)];
        if (isset($analysis['tail_a'])) {
            $rows[] = ['جریان بعد از آخرین تیر (آمپر)', self::amps((float) $analysis['tail_a'])];
        }
        $rows[] = ['توضیح', $p['note'] ?? null];
        return $rows;
    }

    /**
     * جدول تیرها: هر تیر با پلاک، سه فاز، تغییر نسبت به نقطه اندازه‌گیری‌شده قبلی،
     * انشعاب، مختصات، و زمان ثبت موقعیت با فاصله کاربر از تیر.
     *
     * @return list<list<string>>
     */
    public static function poleTable(array $p): array
    {
        $table = [];
        $previous = self::phases($p['start'] ?? null);
        foreach (array_values((array) ($p['poles'] ?? [])) as $i => $pole) {
            $pole = (array) $pole;
            $phases = ($pole['state'] ?? 'measured') === 'measured' ? self::phases($pole['phases'] ?? null) : null;
            $change = '—';
            if ($phases !== null && $previous !== null) {
                $drop = array_sum($previous) - array_sum($phases);
                $change = ($drop >= 0 ? 'افت ' : 'افزایش ') . self::amps(abs($drop));
            }
            $lat = is_numeric($pole['lat'] ?? null) ? sprintf('%.6f, %.6f', (float) $pole['lat'], (float) $pole['lon']) : '—';
            $table[] = [
                Jalali::digits((string) ($i + 1)),
                (string) ($pole['plate'] ?? ''),
                $phases === null ? 'اندازه‌گیری نشد: ' . (string) ($pole['reason'] ?? '') : FieldPresenter::phasesText($pole['phases']),
                $change,
                Jalali::digits((string) (int) ($pole['branches'] ?? 0)),
                $lat,
                (string) PositionAudit::text($pole['at'] ?? null, $pole['lat'] ?? null, $pole['lon'] ?? null,
                    $pole['olat'] ?? null, $pole['olon'] ?? null, $pole['oacc'] ?? null),
            ];
            if ($phases !== null) {
                $previous = $phases;
            }
        }
        return $table;
    }

    /** نمودار جریان سه فاز در طول مسیر؛ بازه پرافت سرخ و بازه افزایش کهربایی. */
    public static function svg(array $p): string
    {
        $points = self::points($p);
        $n = count($points);
        if ($n < 2) {
            return '';
        }
        [$w, $h, $left, $right, $top, $bottom] = [640, 240, 44, 12, 12, 28];
        $max = max(1.0, max(array_map(static fn ($pt) => max($pt['phases']), $points)));
        $step = self::niceStep($max);
        $ceiling = ceil($max / $step) * $step;
        $x = static fn (int $i): float => $left + ($w - $left - $right) * $i / ($n - 1);
        $y = static fn (float $v): float => $top + ($h - $top - $bottom) * (1 - $v / $ceiling);
        $index = array_flip(array_map(static fn ($pt) => $pt['pole'], $points));
        $svg = '<svg viewBox="0 0 ' . $w . ' ' . $h . '" width="100%" role="img" style="direction:ltr;font:11px sans-serif">';
        $analysis = (array) ($p['analysis'] ?? []);
        foreach ([['heavy', '#fee2e2'], ['rises', '#fef3c7']] as [$key, $color]) {
            foreach ((array) ($analysis[$key] ?? []) as $s) {
                $a = $index[(int) ($s['from'] ?? -1)] ?? null;
                $b = $index[(int) ($s['to'] ?? -1)] ?? null;
                if ($a !== null && $b !== null) {
                    $svg .= sprintf('<rect x="%.1f" y="%d" width="%.1f" height="%d" fill="%s"/>', $x($a), $top,
                        $x($b) - $x($a), $h - $top - $bottom, $color);
                }
            }
        }
        for ($v = 0.0; $v <= $ceiling + 0.001; $v += $step) {
            $svg .= sprintf('<line x1="%d" x2="%d" y1="%.1f" y2="%.1f" stroke="#e2e8f0"/>', $left, $w - $right, $y($v), $y($v));
            $svg .= sprintf('<text x="%d" y="%.1f" text-anchor="end" fill="#64748b">%s</text>', $left - 6, $y($v) + 4,
                Jalali::digits((string) (int) $v));
        }
        foreach ($points as $i => $pt) {
            $svg .= sprintf('<text x="%.1f" y="%d" text-anchor="middle" fill="#64748b">%s</text>', $x($i), $h - 8,
                $pt['pole'] === 0 ? 'تابلو' : Jalali::digits((string) $pt['pole']));
        }
        foreach (self::PHASE_COLORS as $phase => $color) {
            $coords = [];
            foreach ($points as $i => $pt) {
                $coords[] = sprintf('%.1f,%.1f', $x($i), $y($pt['phases'][$phase]));
            }
            $svg .= '<polyline fill="none" stroke="' . $color . '" stroke-width="3" stroke-linejoin="round" points="'
                . implode(' ', $coords) . '"/>';
            foreach ($coords as $c) {
                [$cx, $cy] = explode(',', $c);
                $svg .= '<circle cx="' . $cx . '" cy="' . $cy . '" r="4" fill="' . $color . '" stroke="#fff" stroke-width="1.5"/>';
            }
        }
        return $svg . '</svg>';
    }

    /** گامی از ۱، ۲ یا ۵ ضرب در توانی از ده که چهار پنج خط روی محور بگذارد. */
    private static function niceStep(float $max): float
    {
        $rough = $max / 4;
        $magnitude = 1.0;
        while ($magnitude * 10 <= $rough) {
            $magnitude *= 10;
        }
        foreach ([1, 2, 5, 10] as $m) {
            if ($m * $magnitude >= $rough) {
                return $m * $magnitude;
            }
        }
        return 10 * $magnitude;
    }
}
