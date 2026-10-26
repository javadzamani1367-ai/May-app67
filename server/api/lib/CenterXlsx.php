<?php
declare(strict_types=1);

/**
 * اکسل آمار جامع، به شکل فرم توانیر: سرستون چهارسطری ادغام‌شده، فونت B Titr و B Nazanin،
 * رنگ و کادر و چرخش متن از سبک‌های خود فرم (CenterTemplate)، تیک ارگان با «*»، تیک
 * کمیسیون با Wingdings، سطر جمع، راهنمای «سایر موارد»، و برگه دوم «جدول اطلاعات تکمیلی».
 *
 * ستون‌ها همان‌هایی‌اند که مدیر تیک زده (پیش‌فرض همه ستون‌های فرم)؛ ستون حذف‌شده گروه
 * سرستونش را نمی‌شکند، چون ادغام‌ها از روی مسیر سرستون دوباره ساخته می‌شوند.
 */
final class CenterXlsx
{
    private const FIRST_DATA = 5;

    public static function send(string $name, array $rows, array $keys, string $title): void
    {
        $path = tempnam(sys_get_temp_dir(), 'cxl');
        self::write($path, $rows, $keys, $title);
        header('Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
        header('Content-Disposition: attachment; filename="' . $name . '.xlsx"');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        @unlink($path);
    }

    public static function write(string $path, array $rows, array $keys, string $title): void
    {
        $title = mb_substr(str_replace(['/', '\\', '?', '*', '[', ']', ':'], ' ', $title), 0, 31);
        [$main, $mainRef, $lastRow] = self::mainSheet($rows, $keys);
        $supp = CenterSupplementSheet::xml(CenterSupplement::rows($rows));
        $zip = new ZipArchive();
        if ($zip->open($path, ZipArchive::CREATE | ZipArchive::OVERWRITE) !== true) {
            throw new RuntimeException('cannot create xlsx');
        }
        $dir = dirname(__DIR__) . '/templates/stats';
        $zip->addFromString('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            . '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            . '<Default Extension="xml" ContentType="application/xml"/>'
            . '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            . '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            . '<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            . '<Override PartName="/xl/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>'
            . '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
            . '</Types>');
        $zip->addFromString('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            . '</Relationships>');
        $quoted = "'" . str_replace("'", "''", $title) . "'";
        $zip->addFromString('xl/workbook.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            . 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            . '<sheets><sheet name="' . self::esc($title) . '" sheetId="1" r:id="rId1"/>'
            . '<sheet name="جدول اطلاعات تکمیلی" sheetId="2" r:id="rId2"/></sheets>'
            . '<definedNames>'
            . ($lastRow >= self::FIRST_DATA ? '<definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">' . self::esc($quoted . '!' . $mainRef) . '</definedName>' : '')
            . '<definedName name="_xlnm.Print_Titles" localSheetId="0">' . self::esc($quoted . '!$1:$4') . '</definedName>'
            . '</definedNames></workbook>');
        $zip->addFromString('xl/_rels/workbook.xml.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
            . '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>'
            . '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>'
            . '<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            . '</Relationships>');
        $zip->addFile($dir . '/styles.xml', 'xl/styles.xml');
        $zip->addFile($dir . '/theme1.xml', 'xl/theme/theme1.xml');
        $zip->addFromString('xl/worksheets/sheet1.xml', $main);
        $zip->addFromString('xl/worksheets/sheet2.xml', $supp);
        $zip->close();
    }

    /** @return array{0:string, 1:string, 2:int} xml برگه، محدوده فیلتر، آخرین سطر داده */
    private static function mainSheet(array $rows, array $keys): array
    {
        $layout = [];
        $p = 0;
        foreach ($keys as $key) {
            $column = CenterColumns::get($key);
            $letters = $column['cols'] ?? [null];
            $layout[] = ['key' => $key, 'c' => $column, 'letters' => $letters, 'p' => $p];
            $p += count($letters);
        }
        $grid = new CenterGrid();
        self::header($grid, $layout);
        $r = self::FIRST_DATA;
        $widths = array_fill(0, $p, 0.0);
        $fits = [];
        foreach ($rows as $row) {
            $grid->height($r, 30);
            foreach ($layout as $i => $col) {
                $fits[$i] = max($fits[$i] ?? 0, self::cell($grid, $r, $col, $row['values'][$col['key']] ?? null));
            }
            $r++;
        }
        $lastData = $r - 1;
        $totals = CenterRows::totals($rows);
        $firstTotal = null;
        foreach ($layout as $i => $col) {
            if (isset($totals[$col['key']])) {
                $firstTotal ??= $col['p'];
                $fits[$i] = max($fits[$i] ?? 0, self::cell($grid, $r, $col, $totals[$col['key']], true));
            } else {
                self::cell($grid, $r, $col, null, true);
            }
        }
        $grid->height($r, 30);
        $labelEnd = max(0, ($firstTotal ?? 1) - 1);
        if ($firstTotal !== 0) {
            $grid->put($r, 0, 'جمع کل', CenterTemplate::data($layout[0]['letters'][0] ?? null));
        }
        if ($labelEnd > 0 && $firstTotal !== 0) {
            $grid->merge($r, 0, $r, $labelEnd);
        }
        if (in_array('org_other', $keys, true)) {
            $legend = $r + 3;
            $grid->put($legend, 0, '*', CenterTemplate::LEGEND_KEY);
            $grid->put($legend, 1, 'سایر موارد حتما اعلام گردد', CenterTemplate::LEGEND_TEXT);
            foreach (CenterColumns::OTHER_LEGEND as $n => $text) {
                $grid->put($legend + $n, 0, $n, CenterTemplate::LEGEND_KEY);
                $grid->put($legend + $n, 1, $text, CenterTemplate::LEGEND_TEXT);
            }
            for ($l = $legend; $l <= $legend + 4; $l++) {
                $grid->height($l, 22.5);
                for ($c = 2; $c <= min(5, $p - 1); $c++) {
                    $grid->put($l, $c, null, CenterTemplate::LEGEND_TEXT);
                }
                if ($p > 2) {
                    $grid->merge($l, 1, $l, min(5, $p - 1));
                }
            }
        }
        foreach ($layout as $i => $col) {
            $styles = array_map([CenterTemplate::class, 'data'], $col['letters']);
            $template = array_sum(array_map([CenterTemplate::class, 'width'], $col['letters']));
            $rotated = array_intersect($styles, CenterTemplate::ROTATED_DATA) !== [];
            $total = $rotated || ($fits[$i] ?? 0) <= 0 ? $template : min(40.0, max(2.4, $fits[$i]));
            foreach (CenterTemplate::spread($col['letters'], $total) as $k => $w) {
                $widths[$col['p'] + $k] = $w;
            }
        }
        $grid->fitHeights($widths, self::FIRST_DATA, $r);
        $ref = '$A$4:$' . Xlsx::column(max(0, $p - 1)) . '$' . max(4, $lastData);
        return [$grid->sheet($widths, 4, $lastData >= self::FIRST_DATA ? 'A4:' . Xlsx::column(max(0, $p - 1)) . $lastData : null), $ref, $lastData];
    }

    /** چهار سطر سرستون با ادغام‌های گروه‌ها. */
    private static function header(CenterGrid $grid, array $layout): void
    {
        foreach (CenterTemplate::HEAD_HEIGHTS as $i => $h) {
            $grid->height($i + 1, $h);
        }
        foreach ($layout as $col) {
            foreach ($col['letters'] as $k => $letter) {
                for ($row = 1; $row <= 4; $row++) {
                    $grid->put($row, $col['p'] + $k, null, CenterTemplate::head($letter, $row));
                }
            }
        }
        $depth = max(array_map(static fn ($c) => count($c['c']['head']), $layout));
        for ($level = 0; $level < $depth; $level++) {
            $i = 0;
            while ($i < count($layout)) {
                $head = $layout[$i]['c']['head'];
                if (!isset($head[$level])) {
                    $i++;
                    continue;
                }
                $leaf = $level === count($head) - 1;
                $key = json_encode(array_slice($head, 0, $level + 1)) . ($leaf ? $layout[$i]['key'] : '');
                $j = $i;
                while (!$leaf && isset($layout[$j + 1]) && isset($layout[$j + 1]['c']['head'][$level])
                    && $level < count($layout[$j + 1]['c']['head']) - 1
                    && json_encode(array_slice($layout[$j + 1]['c']['head'], 0, $level + 1)) === $key) {
                    $j++;
                }
                [$label, $r1, $r2] = $head[$level];
                $c1 = $layout[$i]['p'];
                $c2 = $layout[$j]['p'] + count($layout[$j]['letters']) - 1;
                $grid->put($r1, $c1, $label);
                if ($r1 !== $r2 || $c1 !== $c2) {
                    $grid->merge($r1, $c1, $r2, $c2);
                }
                $i = $j + 1;
            }
        }
    }

    /** یک خانه داده یا جمع؛ پهنای لازم متنش را برمی‌گرداند. */
    private static function cell(CenterGrid $grid, int $r, array $col, mixed $value, bool $total = false): float
    {
        $type = $col['c']['type'];
        $styles = array_map([CenterTemplate::class, 'data'], $col['letters']);
        foreach ($styles as $k => $style) {
            $grid->put($r, $col['p'] + $k, null, $style);
        }
        if (count($styles) > 1) {
            $grid->merge($r, $col['p'], $r, $col['p'] + count($styles) - 1);
        }
        if ($value === null || $value === '') {
            return 0.0;
        }
        $style = $styles[0];
        if ($type === 'tick' && !$total) {
            $style = $col['key'] === 'commission' && $col['letters'][0] === 'Y' ? CenterTemplate::CHECK : CenterTemplate::TICK;
            $value = $style === CenterTemplate::CHECK ? 'ü' : '*';
        } elseif ($type === 'date') {
            $value = Jalali::latin(Jalali::format((int) $value, false));
        } elseif (is_string($value) && preg_match('/^[1-9]\d{0,14}$/', $value) === 1) {
            $value = (float) $value;
        }
        $grid->put($r, $col['p'], $value, $style);
        $text = is_float($value) || is_int($value)
            ? (in_array($type, ['money', 'int'], true) ? number_format((float) $value) : (string) $value) : (string) $value;
        return CenterTemplate::fit($text, $style);
    }

    public static function esc(string $text): string
    {
        $clean = (string) preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/u', '', $text);
        return htmlspecialchars($clean, ENT_XML1 | ENT_QUOTES, 'UTF-8');
    }
}
