<?php
declare(strict_types=1);

/**
 * اکسل رسمی هم‌شکل با Word رسمی (OfficialDocx): همان مدل — عنوان، زیرعنوان، مقدمه و شرح
 * دستورالعمل، جدول‌ها با سطر جمع، یادداشت‌ها و جای امضا — با قلم‌های فرم توانیر (B Titr و
 * B Nazanin، سبک‌های ۲۶۷ به بعد templates/stats/styles.xml)، راست‌به‌چپ و آماده چاپ A4.
 * عددها عدد می‌مانند (با جداکننده هزارگان) تا در اکسل جمع زده شوند.
 *
 * جدولی که ستون کمتری از پهن‌ترین جدول دارد، خانه‌هایش را روی همه پهنا پخش (ادغام) می‌کند؛
 * جدول pairs (برچسب، مقدار، برچسب، مقدار…) برچسب‌ها را سایه‌دار می‌نویسد؛ portrait برای فرم
 * تک‌صفحه‌ای هر مرکز.
 */
final class OfficialXlsx
{
    private const TITLE = 267;
    private const SUBTITLE = 268;
    private const HEAD = 269;
    private const CELL = 270;
    private const MONEY = 271;
    private const TOTAL = 272;
    private const TOTAL_MONEY = 273;
    private const NOTE = 274;
    private const CELL_RIGHT = 275;
    private const SIGN = 276;

    public static function send(string $name, array $model): void
    {
        $path = tempnam(sys_get_temp_dir(), 'oxl');
        self::write($path, $model);
        TemplateBook::send($name, $path);
    }

    public static function write(string $path, array $model): void
    {
        TemplateBook::write($path, [['name' => $model['sheet'] ?? $model['title'], 'xml' => self::sheet($model)]]);
    }

    public static function sheet(array $model): string
    {
        $tables = $model['tables'] ?? [];
        $count = static fn (array $t): int => count($t['header'] ?? $t['rows'][0] ?? []);
        $n = max(2, ...array_map($count, $tables ?: [['header' => [1, 2]]]));
        $widths = array_fill(0, $n, 6.0);
        foreach ($tables as $t) {
            if ($count($t) !== $n) {
                continue;
            }
            foreach (array_merge([$t['header'] ?? []], $t['rows'], [$t['total'] ?? []]) as $line) {
                foreach (array_values($line) as $c => $v) {
                    $text = is_float($v) || is_int($v) ? number_format((float) $v) : (string) $v;
                    $widths[$c] = max($widths[$c], min(34.0, CenterTemplate::fit($text, self::CELL)));
                }
            }
        }
        $total = array_sum($widths);
        $grid = new CenterGrid();
        $r = 1;
        $grid->put($r, max(0, $n - 2), OfficialDocx::metaText($model['meta'] ?? []), self::NOTE);
        $grid->merge($r, max(0, $n - 2), $r, $n - 1);
        if (!empty($model['letterhead']) && $n > 2) {
            self::cells($grid, $r, [[0, $n - 3]], [(string) $model['letterhead']], static fn () => self::SIGN);
        }
        $grid->height($r, 50);
        $r++;
        self::line($grid, $r++, $n, (string) $model['title'], self::TITLE, 32);
        if (!empty($model['subtitle'])) {
            self::line($grid, $r++, $n, (string) $model['subtitle'], self::SUBTITLE, 20);
        }
        foreach ($model['paragraphs'] ?? [] as $text) {
            self::line($grid, $r++, $n, (string) $text, self::NOTE, self::height((string) $text, $total));
        }
        $r++;
        foreach ($tables as $t) {
            if (!empty($t['caption'])) {
                self::line($grid, $r++, $n, (string) $t['caption'], self::SIGN, 22);
            }
            $spans = self::spans($count($t), $n);
            if (!empty($t['header'])) {
                self::cells($grid, $r, $spans, array_values($t['header']), static fn () => self::HEAD);
                $grid->height($r++, 34);
            }
            $right = array_flip($t['right'] ?? []);
            $pairs = !empty($t['pairs']);
            foreach ($t['rows'] as $row) {
                self::cells($grid, $r, $spans, array_values($row), static function ($c, $v) use ($right, $pairs) {
                    if ($pairs && $c % 2 === 0) {
                        return self::HEAD;
                    }
                    return is_int($v) || is_float($v) ? self::MONEY : (isset($right[$c]) || $pairs ? self::CELL_RIGHT : self::CELL);
                });
                $grid->height($r++, 22);
            }
            if (!empty($t['total'])) {
                self::cells($grid, $r, $spans, array_values($t['total']),
                    static fn ($c, $v) => is_int($v) || is_float($v) ? self::TOTAL_MONEY : self::TOTAL);
                $grid->height($r++, 24);
            }
            $r++;
        }
        foreach ($model['notes'] ?? [] as $text) {
            self::line($grid, $r++, $n, (string) $text, self::NOTE, self::height((string) $text, $total));
        }
        $signs = $model['signatures'] ?? [];
        if ($signs !== []) {
            $r++;
            $spans = self::spans(count($signs), $n);
            self::cells($grid, $r, $spans, array_values($signs), static fn () => self::SIGN);
            self::cells($grid, $r + 1, $spans, array_fill(0, count($signs), 'امضا و تاریخ'), static fn () => self::SUBTITLE);
            $grid->height($r, 24);
            $grid->height($r + 1, 46);
            $r++;
        }
        $grid->fitHeights($widths, 1, $r);
        return $grid->sheet($widths, 0, null, empty($model['portrait']));
    }

    /** ستون‌های فیزیکی هر خانه وقتی k خانه روی n ستون پخش می‌شوند. @return list<array{0:int, 1:int}> */
    private static function spans(int $k, int $n): array
    {
        $k = max(1, min($k, $n));
        $out = [];
        for ($i = 0; $i < $k; $i++) {
            $out[] = [intdiv($i * $n, $k), intdiv(($i + 1) * $n, $k) - 1];
        }
        return $out;
    }

    /** یک سطر جدول با ادغام هر خانه روی ستون‌های خودش. */
    private static function cells(CenterGrid $grid, int $r, array $spans, array $values, callable $style): void
    {
        foreach ($spans as $i => [$c1, $c2]) {
            $value = $values[$i] ?? null;
            $s = $style($i, $value);
            $grid->put($r, $c1, $value, $s);
            for ($c = $c1 + 1; $c <= $c2; $c++) {
                $grid->put($r, $c, null, $s);
            }
            if ($c2 > $c1) {
                $grid->merge($r, $c1, $r, $c2);
            }
        }
    }

    /** یک سطر ادغام‌شده سراسری: عنوان، بند یا یادداشت. */
    private static function line(CenterGrid $grid, int $r, int $n, string $text, int $style, float $height): void
    {
        self::cells($grid, $r, [[0, $n - 1]], [$text], static fn () => $style);
        $grid->height($r, $height);
    }

    /** ارتفاع بندی که در پهنای کل برگه می‌پیچد. */
    private static function height(string $text, float $width): float
    {
        $lines = (int) ceil(CenterTemplate::fit($text, self::NOTE) / max(10.0, $width));
        return min(160.0, max(20.0, $lines * 17.0 + 6));
    }
}
