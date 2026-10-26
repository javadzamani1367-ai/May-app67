<?php
declare(strict_types=1);

/**
 * بسته اکسل با سبک‌ها و پوسته فرم توانیر (templates/stats): هر برگه XML آماده‌اش را می‌دهد
 * (CenterGrid)، اینجا کتاب ساخته و فیلتر و سطرهای تکرارشونده چاپ هر برگه تعریف می‌شود.
 * آمار جامع و گزارش‌های پاداش هر دو از همین می‌روند تا قلم و رنگ یکی باشد.
 */
final class TemplateBook
{
    /** @param list<array{name:string, xml:string, filter?:?string, titles?:?string}> $sheets */
    public static function write(string $path, array $sheets): void
    {
        $zip = new ZipArchive();
        if ($zip->open($path, ZipArchive::CREATE | ZipArchive::OVERWRITE) !== true) {
            throw new RuntimeException('cannot create xlsx');
        }
        $dir = dirname(__DIR__) . '/templates/stats';
        $overrides = '';
        $entries = '';
        $names = '';
        $rels = '';
        $used = [];
        foreach (array_values($sheets) as $i => $sheet) {
            $n = $i + 1;
            $title = self::title($sheet['name'], $used);
            $quoted = "'" . str_replace("'", "''", $title) . "'";
            $overrides .= '<Override PartName="/xl/worksheets/sheet' . $n . '.xml" '
                . 'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>';
            $entries .= '<sheet name="' . CenterXlsx::esc($title) . '" sheetId="' . $n . '" r:id="rId' . $n . '"/>';
            $rels .= '<Relationship Id="rId' . $n . '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet' . $n . '.xml"/>';
            if (!empty($sheet['filter'])) {
                $names .= '<definedName name="_xlnm._FilterDatabase" localSheetId="' . $i . '" hidden="1">' . CenterXlsx::esc($quoted . '!' . $sheet['filter']) . '</definedName>';
            }
            if (!empty($sheet['titles'])) {
                $names .= '<definedName name="_xlnm.Print_Titles" localSheetId="' . $i . '">' . CenterXlsx::esc($quoted . '!' . $sheet['titles']) . '</definedName>';
            }
            $zip->addFromString('xl/worksheets/sheet' . $n . '.xml', $sheet['xml']);
        }
        $zip->addFromString('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            . '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            . '<Default Extension="xml" ContentType="application/xml"/>'
            . '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            . $overrides
            . '<Override PartName="/xl/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>'
            . '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
            . '</Types>');
        $zip->addFromString('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            . '</Relationships>');
        $zip->addFromString('xl/workbook.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            . 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            . '<sheets>' . $entries . '</sheets>' . ($names !== '' ? '<definedNames>' . $names . '</definedNames>' : '') . '</workbook>');
        $zip->addFromString('xl/_rels/workbook.xml.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' . $rels
            . '<Relationship Id="rIdTheme" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>'
            . '<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            . '</Relationships>');
        $zip->addFile($dir . '/styles.xml', 'xl/styles.xml');
        $zip->addFile($dir . '/theme1.xml', 'xl/theme/theme1.xml');
        $zip->close();
    }

    public static function send(string $name, string $path): void
    {
        header('Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
        header('Content-Disposition: attachment; filename="' . $name . '.xlsx"');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        @unlink($path);
    }

    /** نام برگه: بی نویسه‌های ممنوع اکسل، حداکثر ۳۱ نویسه، یکتا. */
    private static function title(string $name, array &$used): string
    {
        $title = mb_substr(trim(str_replace(['/', '\\', '?', '*', '[', ']', ':'], ' ', $name)), 0, 28) ?: 'برگه';
        $base = $title;
        for ($i = 2; isset($used[$title]); $i++) {
            $title = $base . ' ' . $i;
        }
        $used[$title] = true;
        return $title;
    }
}
