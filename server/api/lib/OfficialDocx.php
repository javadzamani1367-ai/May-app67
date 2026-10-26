<?php
declare(strict_types=1);

/**
 * Word رسمی برای مکاتبه اداری: قلم B Nazanin برای متن و B Titr برای عنوان و سرستون (همان
 * قلم‌های فرم توانیر)، جای «شماره / تاریخ / پیوست» بالای برگه، عنوان وسط، مقدمه و شرح
 * دستورالعمل، جدول‌ها با سرستون سایه‌دار و سطر جمع، یادداشت‌ها و جای امضا.
 *
 * مدل: title، subtitle، paragraphs، tables [{caption, header, rows, total, widths}]، notes،
 * signatures. خانه‌های جدول همان متنی است که نشان داده می‌شود (رقم فارسی).
 */
final class OfficialDocx
{
    private const BODY = 'B Nazanin';
    private const HEAD = 'B Titr';

    public static function send(string $name, array $model, bool $landscape = true): void
    {
        $path = tempnam(sys_get_temp_dir(), 'odx');
        self::write($path, $model, $landscape);
        header('Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document');
        header('Content-Disposition: attachment; filename="' . $name . '.docx"');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        @unlink($path);
    }

    public static function write(string $path, array $model, bool $landscape = true): void
    {
        $width = $landscape ? 15400 : 10000;
        $body = self::meta($width)
            . self::para($model['title'] ?? '', 32, true, 'center', self::HEAD)
            . (empty($model['subtitle']) ? '' : self::para($model['subtitle'], 24, false, 'center'));
        foreach ($model['paragraphs'] ?? [] as $text) {
            $body .= self::para($text, 24, false, 'both');
        }
        foreach ($model['tables'] ?? [] as $table) {
            if (!empty($table['caption'])) {
                $body .= self::para($table['caption'], 24, true, 'right', self::HEAD);
            }
            $body .= self::table($table, $width, (int) ($model['cell_size'] ?? 22));
        }
        foreach ($model['notes'] ?? [] as $text) {
            $body .= self::para($text, 20, false, 'both');
        }
        if (!empty($model['signatures'])) {
            $body .= self::signatures($model['signatures'], $width);
        }
        $size = $landscape ? '<w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/>' : '<w:pgSz w:w="11906" w:h="16838"/>';
        $body .= '<w:sectPr>' . $size . '<w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="340" w:footer="340" w:gutter="0"/>'
            . '<w:bidi/></w:sectPr>';
        $zip = new ZipArchive();
        if ($zip->open($path, ZipArchive::CREATE | ZipArchive::OVERWRITE) !== true) {
            throw new RuntimeException('cannot create docx');
        }
        $zip->addFromString('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            . '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            . '<Default Extension="xml" ContentType="application/xml"/>'
            . '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
            . '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>'
            . '</Types>');
        $zip->addFromString('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
            . '</Relationships>');
        $zip->addFromString('word/_rels/document.xml.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            . '</Relationships>');
        $zip->addFromString('word/styles.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            . '<w:docDefaults><w:rPrDefault><w:rPr>' . self::font(self::BODY) . '<w:sz w:val="24"/><w:szCs w:val="24"/>'
            . '<w:lang w:bidi="fa-IR"/></w:rPr></w:rPrDefault>'
            . '<w:pPrDefault><w:pPr><w:spacing w:after="80" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>'
            . '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style></w:styles>');
        $zip->addFromString('word/document.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>' . $body . '</w:body></w:document>');
        $zip->close();
    }

    /** «شماره / تاریخ / پیوست» در گوشه بالای برگه، برای ثبت دبیرخانه. */
    private static function meta(int $width): string
    {
        $cell = static fn (string $text, int $w, string $jc) => '<w:tc><w:tcPr><w:tcW w:w="' . $w . '" w:type="dxa"/></w:tcPr>'
            . self::para($text, 22, true, $jc, self::BODY, true) . '</w:tc>';
        $side = 3200;
        return '<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w="' . $width . '" w:type="dxa"/><w:tblLayout w:type="fixed"/></w:tblPr>'
            . '<w:tblGrid><w:gridCol w:w="' . ($width - $side) . '"/><w:gridCol w:w="' . $side . '"/></w:tblGrid><w:tr>'
            . $cell('', $width - $side, 'right')
            . $cell("شماره: ....................\nتاریخ: ....................\nپیوست: ....................", $side, 'left')
            . '</w:tr></w:tbl>';
    }

    private static function table(array $table, int $width, int $size): string
    {
        $header = $table['header'];
        $count = count($header);
        $weights = $table['widths'] ?? array_fill(0, $count, 1);
        $sum = array_sum($weights) ?: 1;
        $cols = array_map(static fn ($w) => (int) round($width * $w / $sum), $weights);
        $border = '<w:top w:val="single" w:sz="6" w:color="1F2937"/><w:bottom w:val="single" w:sz="6" w:color="1F2937"/>'
            . '<w:left w:val="single" w:sz="6" w:color="1F2937"/><w:right w:val="single" w:sz="6" w:color="1F2937"/>'
            . '<w:insideH w:val="single" w:sz="4" w:color="64748B"/><w:insideV w:val="single" w:sz="4" w:color="64748B"/>';
        $xml = '<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w="' . $width . '" w:type="dxa"/><w:tblBorders>' . $border . '</w:tblBorders>'
            . '<w:tblLayout w:type="fixed"/><w:tblCellMar><w:left w:w="60" w:type="dxa"/><w:right w:w="60" w:type="dxa"/></w:tblCellMar></w:tblPr><w:tblGrid>'
            . implode('', array_map(static fn ($w) => '<w:gridCol w:w="' . $w . '"/>', $cols)) . '</w:tblGrid>';
        $xml .= self::row($header, $cols, 'DCE6F2', true, self::HEAD, $size - 2, true);
        foreach ($table['rows'] as $i => $row) {
            $xml .= self::row($row, $cols, $i % 2 === 1 ? 'F8FAFC' : null, false, self::BODY, $size, false);
        }
        if (!empty($table['total'])) {
            $xml .= self::row($table['total'], $cols, 'F1F5F9', true, self::HEAD, $size - 2, false);
        }
        return $xml . '</w:tbl>' . self::para('', 12, false, 'right');
    }

    /** @param list<string> $cells */
    private static function row(array $cells, array $cols, ?string $fill, bool $bold, string $font, int $size, bool $repeat): string
    {
        $xml = '<w:tr>' . ($repeat ? '<w:trPr><w:tblHeader/><w:cantSplit/></w:trPr>' : '<w:trPr><w:cantSplit/></w:trPr>');
        foreach (array_values($cells) as $i => $text) {
            $xml .= '<w:tc><w:tcPr><w:tcW w:w="' . ($cols[$i] ?? 1000) . '" w:type="dxa"/><w:vAlign w:val="center"/>'
                . ($fill === null ? '' : '<w:shd w:val="clear" w:color="auto" w:fill="' . $fill . '"/>') . '</w:tcPr>'
                . self::para(self::show($text), $size, $bold, 'center', $font, true) . '</w:tc>';
        }
        return $xml . '</w:tr>';
    }

    /** جای امضا: هر عنوان یک ستون، بی کادر. @param list<string> $titles */
    private static function signatures(array $titles, int $width): string
    {
        $w = intdiv($width, count($titles));
        $xml = self::para('', 20, false, 'right') . '<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w="' . $width . '" w:type="dxa"/>'
            . '<w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>' . str_repeat('<w:gridCol w:w="' . $w . '"/>', count($titles)) . '</w:tblGrid><w:tr>';
        foreach ($titles as $title) {
            $xml .= '<w:tc><w:tcPr><w:tcW w:w="' . $w . '" w:type="dxa"/></w:tcPr>' . self::para($title, 22, true, 'center', self::HEAD, true)
                . self::para("\n\nامضا و تاریخ", 20, false, 'center', self::BODY, true) . '</w:tc>';
        }
        return $xml . '</w:tr></w:tbl>';
    }

    private static function para(string $text, int $size, bool $bold, string $align, string $font = self::BODY, bool $tight = false): string
    {
        $rPr = '<w:rPr>' . self::font($font) . ($bold ? '<w:b/><w:bCs/>' : '') . '<w:sz w:val="' . $size . '"/><w:szCs w:val="' . $size . '"/><w:rtl/></w:rPr>';
        $runs = [];
        foreach (preg_split('/\R/u', $text) ?: [''] as $line) {
            $runs[] = '<w:r>' . $rPr . '<w:t xml:space="preserve">' . self::xml($line) . '</w:t></w:r>';
        }
        return '<w:p><w:pPr><w:bidi/>' . ($tight ? '<w:spacing w:before="20" w:after="20"/>' : '') . '<w:jc w:val="' . $align . '"/></w:pPr>'
            . implode('<w:r><w:br/></w:r>', $runs) . '</w:p>';
    }

    /** خانه جدول: عدد با جداکننده هزارگان، همه با رقم فارسی. */
    public static function show(mixed $value): string
    {
        if (is_int($value) || is_float($value)) {
            $decimals = is_float($value) && abs($value - round($value)) > 0.001 ? 2 : 0;
            return Jalali::digits(number_format((float) $value, $decimals));
        }
        return Jalali::digits((string) ($value ?? ''));
    }

    private static function font(string $name): string
    {
        return '<w:rFonts w:ascii="' . $name . '" w:hAnsi="' . $name . '" w:cs="' . $name . '"/>';
    }

    private static function xml(string $text): string
    {
        $clean = (string) preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/u', '', $text);
        return htmlspecialchars($clean, ENT_XML1 | ENT_QUOTES, 'UTF-8');
    }
}
