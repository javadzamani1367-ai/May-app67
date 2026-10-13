<?php
declare(strict_types=1);

/**
 * کوچک‌ترین فایل اکسل درستی که لازم است: یک برگه راست‌به‌چپ، سطر اول
 * پررنگ و ثابت، متن‌ها درون‌خطی. بدون کتابخانه، چون روی هاست اشتراکی
 * composer نیست؛ فقط ZipArchive که تقریباً همه هاست‌ها دارند. اگر نداشت،
 * همان جدول CSV با BOM می‌شود که اکسل فارسی‌اش را درست باز می‌کند.
 */
final class Xlsx
{
    /**
     * @param array<int, string> $header
     * @param array<int, array<int, string|int|float|null>> $rows
     */
    public static function send(string $name, string $sheetTitle, array $header, array $rows): void
    {
        self::sendBook($name, [[$sheetTitle, $header, $rows]]);
    }

    /**
     * چند برگه در یک فایل؛ هر برگه [عنوان، سطر عنوان، سطرها]. بی ZipArchive فقط
     * برگه اول به صورت CSV می‌رود.
     *
     * @param list<array{0:string, 1:array<int, string>, 2:array<int, array<int, string|int|float|null>>}> $sheets
     */
    public static function sendBook(string $name, array $sheets): void
    {
        if (!class_exists('ZipArchive')) {
            self::sendCsv($name, $sheets[0][1], $sheets[0][2]);
            return;
        }
        $path = tempnam(sys_get_temp_dir(), 'xlsx');
        self::writeBook($path, $sheets);
        header('Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
        header('Content-Disposition: attachment; filename="' . $name . '.xlsx"');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        @unlink($path);
    }

    /**
     * @param array<int, string> $header
     * @param array<int, array<int, string|int|float|null>> $rows
     */
    public static function write(string $path, string $sheetTitle, array $header, array $rows): void
    {
        self::writeBook($path, [[$sheetTitle, $header, $rows]]);
    }

    /** @param list<array{0:string, 1:array<int, string>, 2:array<int, array<int, string|int|float|null>>}> $sheets */
    public static function writeBook(string $path, array $sheets): void
    {
        $zip = new ZipArchive();
        if ($zip->open($path, ZipArchive::CREATE | ZipArchive::OVERWRITE) !== true) {
            throw new RuntimeException('cannot create xlsx');
        }
        $overrides = '';
        $entries = '';
        $rels = '';
        foreach (array_values($sheets) as $i => [$title]) {
            $n = $i + 1;
            $overrides .= '<Override PartName="/xl/worksheets/sheet' . $n . '.xml" '
                . 'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>';
            $entries .= '<sheet name="' . self::xml(mb_substr($title, 0, 31)) . '" sheetId="' . $n . '" r:id="rId' . $n . '"/>';
            $rels .= '<Relationship Id="rId' . $n . '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet' . $n . '.xml"/>';
        }
        $zip->addFromString('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            . '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            . '<Default Extension="xml" ContentType="application/xml"/>'
            . '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            . $overrides
            . '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
            . '</Types>');
        $zip->addFromString('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            . '</Relationships>');
        $zip->addFromString('xl/workbook.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            . 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            . '<sheets>' . $entries . '</sheets></workbook>');
        $zip->addFromString('xl/_rels/workbook.xml.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' . $rels
            . '<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            . '</Relationships>');
        // سبک ۱: پررنگ با زمینه خاکستری، برای سطر عنوان.
        $zip->addFromString('xl/styles.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            . '<fonts count="2"><font><sz val="11"/><name val="Tahoma"/></font><font><b/><sz val="11"/><name val="Tahoma"/></font></fonts>'
            . '<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>'
            . '<fill><patternFill patternType="solid"><fgColor rgb="FFE2E8F0"/></patternFill></fill></fills>'
            . '<borders count="1"><border/></borders>'
            . '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
            . '<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
            . '<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>'
            . '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>'
            . '</styleSheet>');

        foreach (array_values($sheets) as $i => [, $header, $rows]) {
            $sheet = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                . '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
                . '<sheetViews><sheetView rightToLeft="1" workbookViewId="0">'
                . '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>'
                . '<sheetData>' . self::row(1, $header, 1);
            foreach (array_values($rows) as $r => $row) {
                $sheet .= self::row($r + 2, $row, 0);
            }
            $zip->addFromString('xl/worksheets/sheet' . ($i + 1) . '.xml', $sheet . '</sheetData></worksheet>');
        }
        $zip->close();
    }

    /** @param array<int, string|int|float|null> $cells */
    private static function row(int $number, array $cells, int $style): string
    {
        $xml = '<row r="' . $number . '">';
        foreach (array_values($cells) as $i => $value) {
            $ref = self::column($i) . $number;
            $s = $style > 0 ? ' s="' . $style . '"' : '';
            if ($value === null || $value === '') {
                continue;
            }
            if (is_int($value) || is_float($value)) {
                $xml .= '<c r="' . $ref . '"' . $s . '><v>' . $value . '</v></c>';
            } else {
                $xml .= '<c r="' . $ref . '"' . $s . ' t="inlineStr"><is><t xml:space="preserve">'
                    . self::xml($value) . '</t></is></c>';
            }
        }
        return $xml . '</row>';
    }

    /** A، B، … Z، AA، AB… */
    public static function column(int $index): string
    {
        $name = '';
        for ($n = $index + 1; $n > 0; $n = intdiv($n - 1, 26)) {
            $name = chr(65 + ($n - 1) % 26) . $name;
        }
        return $name;
    }

    private static function xml(string $text): string
    {
        // نویسه‌های کنترلی در XML مجاز نیستند و فایل را برای اکسل خراب می‌کنند.
        $clean = (string) preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/u', '', $text);
        return htmlspecialchars($clean, ENT_XML1 | ENT_QUOTES, 'UTF-8');
    }

    /**
     * @param array<int, string> $header
     * @param array<int, array<int, string|int|float|null>> $rows
     */
    private static function sendCsv(string $name, array $header, array $rows): void
    {
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="' . $name . '.csv"');
        $out = fopen('php://output', 'wb');
        fwrite($out, "\xEF\xBB\xBF");
        fputcsv($out, $header, ',', '"', '');
        foreach ($rows as $row) {
            // فرمولی که از داده آمده اجرا نشود.
            fputcsv($out, array_map(static fn ($v) => is_string($v) && preg_match('/^[=+\-@]/', $v) ? "'" . $v : $v, $row), ',', '"', '');
        }
        fclose($out);
    }
}
