<?php
declare(strict_types=1);

/**
 * اکسل رسمی هم‌شکل با Word رسمی (OfficialDocx): همان مدل — عنوان، زیرعنوان، مقدمه و شرح
 * دستورالعمل، جدول‌ها با سطر جمع، یادداشت‌ها و جای امضا — با قلم‌های فرم توانیر (B Titr و
 * B Nazanin، سبک‌های ۲۶۷ به بعد templates/stats/styles.xml)، راست‌به‌چپ و آماده چاپ A4 افقی.
 * عددها عدد می‌مانند (با جداکننده هزارگان) تا در اکسل جمع زده شوند.
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
        $n = max(2, ...array_map(static fn ($t) => count($t['header']), $tables ?: [['header' => [1, 2]]]));
        $widths = array_fill(0, $n, 6.0);
        foreach ($tables as $t) {
            foreach (array_merge([$t['header']], $t['rows'], [$t['total'] ?? []]) as $line) {
                foreach (array_values($line) as $c => $v) {
                    $text = is_float($v) || is_int($v) ? number_format((float) $v) : (string) $v;
                    $widths[$c] = max($widths[$c], min(40.0, CenterTemplate::fit($text, self::CELL)));
                }
            }
        }
        $total = array_sum($widths);
        $grid = new CenterGrid();
        $r = 1;
        $grid->put($r, max(0, $n - 2), "شماره: ..........   تاریخ: ..........   پیوست: ..........", self::NOTE);
        $grid->merge($r, max(0, $n - 2), $r, $n - 1);
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
            foreach (array_values($t['header']) as $c => $h) {
                $grid->put($r, $c, (string) $h, self::HEAD);
            }
            $grid->height($r, 34);
            $r++;
            $right = array_flip($t['right'] ?? []);
            foreach ($t['rows'] as $row) {
                foreach (array_values($row) as $c => $v) {
                    $numeric = is_int($v) || is_float($v);
                    $grid->put($r, $c, $v, $numeric ? self::MONEY : (isset($right[$c]) ? self::CELL_RIGHT : self::CELL));
                }
                $grid->height($r, 22);
                $r++;
            }
            if (!empty($t['total'])) {
                foreach (array_values($t['total']) as $c => $v) {
                    $grid->put($r, $c, $v, is_int($v) || is_float($v) ? self::TOTAL_MONEY : self::TOTAL);
                }
                $grid->height($r, 24);
                $r++;
            }
            $r++;
        }
        foreach ($model['notes'] ?? [] as $text) {
            self::line($grid, $r++, $n, (string) $text, self::NOTE, self::height((string) $text, $total));
        }
        $signs = $model['signatures'] ?? [];
        if ($signs !== []) {
            $r++;
            $per = max(1, intdiv($n, count($signs)));
            foreach (array_values($signs) as $i => $title) {
                $c1 = $i * $per;
                $c2 = $i === count($signs) - 1 ? $n - 1 : $c1 + $per - 1;
                $grid->put($r, $c1, $title, self::SIGN);
                $grid->put($r + 1, $c1, 'امضا و تاریخ', self::SUBTITLE);
                if ($c2 > $c1) {
                    $grid->merge($r, $c1, $r, $c2);
                    $grid->merge($r + 1, $c1, $r + 1, $c2);
                }
            }
            $grid->height($r, 24);
            $grid->height($r + 1, 46);
        }
        $grid->fitHeights($widths, 1, $r);
        return $grid->sheet($widths, 0, null);
    }

    /** یک سطر ادغام‌شده سراسری: عنوان، بند یا یادداشت. */
    private static function line(CenterGrid $grid, int $r, int $n, string $text, int $style, float $height): void
    {
        $grid->put($r, 0, $text, $style);
        for ($c = 1; $c < $n; $c++) {
            $grid->put($r, $c, null, $style);
        }
        $grid->merge($r, 0, $r, $n - 1);
        $grid->height($r, $height);
    }

    /** ارتفاع بندی که در پهنای کل برگه می‌پیچد. */
    private static function height(string $text, float $width): float
    {
        $lines = (int) ceil(CenterTemplate::fit($text, self::NOTE) / max(10.0, $width));
        return min(160.0, max(20.0, $lines * 17.0 + 6));
    }
}
