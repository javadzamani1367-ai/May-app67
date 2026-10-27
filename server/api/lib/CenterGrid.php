<?php
declare(strict_types=1);

/**
 * جدول خانه‌های یک برگه اکسل پیش از نوشتن: مقدار و سبک هر خانه، ادغام‌ها و ارتفاع سطرها؛
 * بعد XML برگه راست‌به‌چپ با پهنای ستون‌ها، سرستون ثابت، فیلتر و تنظیم چاپ A4 افقی.
 */
final class CenterGrid
{
    /** @var array<int, array<int, array{0:mixed, 1:int}>> */
    private array $cells = [];
    /** @var list<array{0:int,1:int,2:int,3:int}> */
    private array $merges = [];
    /** @var array<int, float> */
    private array $heights = [];

    /** مقدار و سبک؛ مقدار تهی سبک را می‌گذارد و مقدار قبلی را نگه می‌دارد. */
    public function put(int $r, int $c, mixed $value, ?int $style = null): void
    {
        $old = $this->cells[$r][$c] ?? [null, 0];
        $this->cells[$r][$c] = [$value ?? $old[0], $style ?? $old[1]];
    }

    public function merge(int $r1, int $c1, int $r2, int $c2): void
    {
        $this->merges[] = [$r1, $c1, $r2, $c2];
    }

    public function height(int $r, float $h): void
    {
        $this->heights[$r] = max($this->heights[$r] ?? 0, $h);
    }

    /** ارتفاع سطرهای داده به اندازه متن پیچیده یا چرخیده هر خانه. @param array<int, float> $widths */
    public function fitHeights(array $widths, int $from, int $to): void
    {
        $span = [];
        foreach ($this->merges as [$r1, $c1, $r2, $c2]) {
            if ($r1 === $r2 && $r1 >= $from && $r1 <= $to) {
                $span[$r1][$c1] = array_sum(array_slice($widths, $c1, $c2 - $c1 + 1));
            }
        }
        for ($r = $from; $r <= $to; $r++) {
            foreach ($this->cells[$r] ?? [] as $c => [$value, $style]) {
                if (!is_string($value) || $value === '') {
                    continue;
                }
                $size = 9;
                if (in_array($style, CenterTemplate::ROTATED_DATA, true)) {
                    $this->height($r, min(150, mb_strlen($value) * 8 * 0.45 + 10));
                    continue;
                }
                $width = max(1.0, $span[$r][$c] ?? $widths[$c] ?? 8.0);
                $lines = (int) ceil(CenterTemplate::fit($value, $style) / $width);
                $this->height($r, min(150, max(1, $lines) * $size * 1.55 + 8));
            }
        }
    }

    /** @param array<int, float> $widths */
    public function sheet(array $widths, int $freeze, ?string $filter, bool $landscape = true): string
    {
        ksort($this->cells);
        $cols = '';
        foreach ($widths as $c => $w) {
            $cols .= '<col min="' . ($c + 1) . '" max="' . ($c + 1) . '" width="' . round(max(0.8, $w), 3) . '" customWidth="1"/>';
        }
        $data = '';
        $last = 'A1';
        foreach ($this->cells as $r => $row) {
            ksort($row);
            $h = isset($this->heights[$r]) ? ' ht="' . round($this->heights[$r], 2) . '" customHeight="1"' : '';
            $data .= '<row r="' . $r . '"' . $h . '>';
            foreach ($row as $c => [$value, $style]) {
                $ref = Xlsx::column($c) . $r;
                $last = $ref;
                $s = $style > 0 ? ' s="' . $style . '"' : '';
                if ($value === null || $value === '') {
                    $data .= '<c r="' . $ref . '"' . $s . '/>';
                } elseif (is_int($value) || is_float($value)) {
                    $data .= '<c r="' . $ref . '"' . $s . '><v>' . (is_float($value) && floor($value) === $value ? number_format($value, 0, '.', '') : $value) . '</v></c>';
                } else {
                    $data .= '<c r="' . $ref . '"' . $s . ' t="inlineStr"><is><t xml:space="preserve">' . CenterXlsx::esc((string) $value) . '</t></is></c>';
                }
            }
            $data .= '</row>';
        }
        $merges = '';
        foreach ($this->merges as [$r1, $c1, $r2, $c2]) {
            $merges .= '<mergeCell ref="' . Xlsx::column($c1) . $r1 . ':' . Xlsx::column($c2) . $r2 . '"/>';
        }
        $pane = $freeze > 0 ? '<pane ySplit="' . $freeze . '" topLeftCell="A' . ($freeze + 1) . '" activePane="bottomLeft" state="frozen"/>' : '';
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            . 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            . '<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>'
            . '<dimension ref="A1:' . $last . '"/>'
            . '<sheetViews><sheetView rightToLeft="1" workbookViewId="0">' . $pane . '</sheetView></sheetViews>'
            . '<sheetFormatPr defaultRowHeight="14.25"/>'
            . ($cols !== '' ? '<cols>' . $cols . '</cols>' : '')
            . '<sheetData>' . $data . '</sheetData>'
            . ($filter !== null ? '<autoFilter ref="' . $filter . '"/>' : '')
            . ($merges !== '' ? '<mergeCells count="' . count($this->merges) . '">' . $merges . '</mergeCells>' : '')
            . '<pageMargins left="0.2" right="0.2" top="0.35" bottom="0.35" header="0.12" footer="0.12"/>'
            . '<pageSetup paperSize="9" fitToHeight="' . ($landscape ? 0 : 1) . '" orientation="' . ($landscape ? 'landscape' : 'portrait') . '"/>'
            . '</worksheet>';
    }
}
