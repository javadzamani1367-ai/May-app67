<?php
declare(strict_types=1);

require_once __DIR__ . '/FieldHistorySearch.php';

/**
 * گزارش سوابق بازرسی: اکسل (خلاصه هر تابلو و تیر، و همه خوانش‌ها) و Word (برای هر
 * کلید نمودار سه فاز و جدول خوانش‌ها). عددها در اکسل عدد می‌مانند تا جمع و نمودار شوند.
 */
final class FieldHistoryExport
{
    /** کلیدهایی که Word با نمودار و جدول کامل می‌آورد؛ بیشترشان در اکسل‌اند. */
    public const DOCX_KEYS = 40;
    private const PHASE_COLORS = ['r' => '#DC2626', 's' => '#D97706', 't' => '#2563EB'];

    /** @return list<array{0:string, 1:list<string>, 2:list<list<string|int|float|null>>}> */
    public static function book(array $keys, Scope $scope): array
    {
        $summary = [];
        $rows = [];
        foreach ($keys as $k) {
            $last = $k['last'] ?? [];
            $summary[] = [$k['area_label'], $k['label'], $k['count'], CrewSheets::date($k['first_at']), CrewSheets::date($k['last_at']),
                $last['r'] ?? null, $last['s'] ?? null, $last['t'] ?? null, $last['temp_max'] ?? null, $k['anomalies']];
            foreach (FieldHistory::series($k['area'], $k['asset'], $k['plate_key'], $k['part'], null, 500, true) as $r) {
                $rows[] = [$k['area_label'], $k['label'], CrewSheets::date($r['at']), CrewSheets::time($r['at']), $r['r'], $r['s'], $r['t'],
                    $r['n'], $r['temp_max'], $r['temp_box_max'], $r['temp_box_avg'], $r['anomaly'] ? 'بله' : '', (string) ($r['anomaly_note'] ?? ''),
                    (string) ($r['tracking_code'] ?? ''), (string) ($r['who'] ?? '')];
            }
        }
        return [
            ['خلاصه', ['ناحیه', 'تابلو یا تیر', 'شمار خوانش', 'نخستین', 'آخرین', 'آخرین R', 'آخرین S', 'آخرین T', 'آخرین بیشینه دما',
                'افزایش نامتعارف'], $summary],
            ['خوانش‌ها', ['ناحیه', 'تابلو یا تیر', 'تاریخ', 'ساعت', 'R (آمپر)', 'S (آمپر)', 'T (آمپر)', 'نول', 'بیشینه دما',
                'بیشینه کادر', 'میانگین کادر', 'نامتعارف', 'تغییر', 'کد مورد', 'ثبت‌کننده'], $rows],
        ];
    }

    /** @return array<int, array{0:string, 1:mixed}> */
    public static function blocks(array $keys, string $filters): array
    {
        $blocks = [['h', 'گزارش سوابق بازرسی شبکه'], ['p', $filters],
            ['p', 'هر جدول از تازه‌ترین خوانش به قبل است. «نامتعارف» یعنی افزایش یک فاز نسبت به بار قبل یا میانگین تا پنج بار قبل، '
                . 'بیش از حد مجاز تنظیمات میدانی.']];
        foreach (array_slice($keys, 0, self::DOCX_KEYS) as $k) {
            $series = FieldHistory::series($k['area'], $k['asset'], $k['plate_key'], $k['part'], null, 60, true);
            $blocks[] = ['h', $k['label'] . ' — ' . $k['area_label']];
            $chart = $k['part'] === FieldReadings::PART_THERMAL ? null : self::chart($series);
            if ($chart !== null) {
                $blocks[] = ['image', [$chart, 'سرخ R، نارنجی S، آبی T — از قدیم (راست) به تازه (چپ)']];
            }
            $thermal = $k['part'] === FieldReadings::PART_THERMAL;
            $header = $thermal ? ['تاریخ و ساعت', 'بیشینه دما', 'بیشینه کادر', 'میانگین کادر', 'کد مورد']
                : ['تاریخ و ساعت', 'R', 'S', 'T', 'نول', 'نامتعارف', 'کد مورد'];
            $lines = array_map(static fn (array $r) => $thermal
                ? [Jalali::format($r['at']), self::n($r['temp_max']), self::n($r['temp_box_max']), self::n($r['temp_box_avg']),
                    (string) ($r['tracking_code'] ?? '')]
                : [Jalali::format($r['at']), self::n($r['r']), self::n($r['s']), self::n($r['t']), self::n($r['n']),
                    $r['anomaly'] ? Jalali::digits((string) $r['anomaly_note']) : '', (string) ($r['tracking_code'] ?? '')], $series);
            $blocks[] = ['grid', [$header, $lines]];
        }
        if (count($keys) > self::DOCX_KEYS) {
            $blocks[] = ['p', 'بقیه ' . Jalali::digits((string) (count($keys) - self::DOCX_KEYS)) . ' تابلو و تیر در خروجی اکسل همین جستجو هست.'];
        }
        return $blocks;
    }

    /** نمودار سه خطی فازها، JPEG موقت؛ تهی اگر GD نیست یا کمتر از دو خوانش. */
    public static function chart(array $series): ?string
    {
        $points = array_reverse($series);
        if (count($points) < 2 || !function_exists('imagecreatetruecolor')) {
            return null;
        }
        $w = 900;
        $h = 300;
        $pad = 36;
        $img = imagecreatetruecolor($w, $h);
        $rgb = static function (string $hex) use ($img): int {
            return (int) imagecolorallocate($img, hexdec(substr($hex, 1, 2)), hexdec(substr($hex, 3, 2)), hexdec(substr($hex, 5, 2)));
        };
        imagefill($img, 0, 0, $rgb('#FFFFFF'));
        $values = [];
        foreach ($points as $p) {
            foreach (FieldHistory::PHASES as $ph) {
                if ($p[$ph] !== null) {
                    $values[] = (float) $p[$ph];
                }
            }
        }
        if ($values === []) {
            imagedestroy($img);
            return null;
        }
        $max = max(1.0, max($values)) * 1.1;
        $grid = $rgb('#E2E8F0');
        for ($i = 0; $i <= 4; $i++) {
            $y = (int) ($h - $pad - ($h - 2 * $pad) * $i / 4);
            imageline($img, $pad, $y, $w - $pad, $y, $grid);
            imagestring($img, 2, 4, $y - 6, (string) round($max * $i / 4), $rgb('#64748B'));
        }
        $count = count($points);
        // راست‌به‌چپ مثل جدول‌ها: قدیمی‌ترین در راست.
        $x = static fn (int $i): int => (int) ($w - $pad - ($w - 2 * $pad) * $i / max(1, $count - 1));
        $y = static fn (float $v): int => (int) ($h - $pad - ($h - 2 * $pad) * $v / $max);
        foreach (self::PHASE_COLORS as $ph => $hex) {
            $color = $rgb($hex);
            $prev = null;
            foreach ($points as $i => $p) {
                if ($p[$ph] === null) {
                    $prev = null;
                    continue;
                }
                $pt = [$x($i), $y((float) $p[$ph])];
                imagefilledellipse($img, $pt[0], $pt[1], 7, 7, $color);
                if ($prev !== null) {
                    imagesetthickness($img, 2);
                    imageline($img, $prev[0], $prev[1], $pt[0], $pt[1], $color);
                }
                $prev = $pt;
            }
        }
        $path = tempnam(sys_get_temp_dir(), 'hchart');
        $ok = imagejpeg($img, $path, 88);
        imagedestroy($img);
        return $ok ? $path : null;
    }

    /** خلاصه فیلترها برای سر گزارش. */
    public static function filtersText(array $f): string
    {
        $parts = [];
        $parts[] = 'ناحیه: ' . (($f['area'] ?? null) === null ? 'همه' : Areas::label((string) $f['area']));
        $parts[] = 'نوع: ' . match ($f['asset'] ?? null) { FieldReadings::ASSET_PANEL => 'تابلو', FieldReadings::ASSET_POLE => 'تیر', default => 'تابلو و تیر' };
        if (($f['plate'] ?? '') !== '') {
            $parts[] = 'پلاک: ' . Jalali::digits((string) $f['plate']);
        }
        if (($f['from'] ?? null) !== null || ($f['to'] ?? null) !== null) {
            $parts[] = 'بازه: ' . (($f['from'] ?? null) === null ? '—' : Jalali::format((int) $f['from'], false))
                . ' تا ' . (($f['to'] ?? null) === null ? '—' : Jalali::format((int) $f['to'], false));
        }
        if (!empty($f['anomaly'])) {
            $parts[] = 'فقط نامتعارف‌ها';
        }
        return implode(' · ', $parts) . ' · تهیه: ' . Jalali::format(Db::now());
    }

    private static function n(?float $v): string
    {
        return $v === null ? '—' : Jalali::digits(FieldHistory::num($v));
    }
}
