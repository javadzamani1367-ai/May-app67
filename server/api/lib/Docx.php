<?php
declare(strict_types=1);

/**
 * کوچک‌ترین فایل Word درست برای گزارش یک مورد: عنوان، بند، و جدول دوستونی،
 * همه راست‌به‌چپ. بدون کتابخانه، با ZipArchive — همان راهی که اپ برای Word
 * می‌رود (ساخت مستقیم OOXML). قلم Tahoma است که روی ویندوز ۷ هم هست.
 *
 * بلوک‌ها: ['h', متن] عنوان، ['p', متن] بند، ['table', [[عنوان، مقدار]...]].
 */
final class Docx
{
    private const FONT = 'Tahoma';

    /** @param array<int, array{0:string, 1:mixed}> $blocks */
    public static function send(string $name, array $blocks): void
    {
        $path = tempnam(sys_get_temp_dir(), 'docx');
        self::write($path, $blocks);
        header('Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document');
        header('Content-Disposition: attachment; filename="' . $name . '.docx"');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        @unlink($path);
    }

    /** @param array<int, array{0:string, 1:mixed}> $blocks */
    public static function write(string $path, array $blocks): void
    {
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
        $font = '<w:rFonts w:ascii="' . self::FONT . '" w:hAnsi="' . self::FONT . '" w:cs="' . self::FONT . '"/>';
        $zip->addFromString('word/styles.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            . '<w:docDefaults><w:rPrDefault><w:rPr>' . $font . '<w:sz w:val="22"/><w:szCs w:val="22"/>'
            . '<w:lang w:bidi="fa-IR"/></w:rPr></w:rPrDefault>'
            . '<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="300" w:lineRule="auto"/></w:pPr></w:pPrDefault>'
            . '</w:docDefaults>'
            . '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>'
            . '<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/>'
            . '<w:pPr><w:keepNext/><w:spacing w:before="240" w:after="120"/></w:pPr>'
            . '<w:rPr><w:b/><w:bCs/><w:sz w:val="30"/><w:szCs w:val="30"/></w:rPr></w:style>'
            . '<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/>'
            . '<w:pPr><w:keepNext/><w:spacing w:before="200" w:after="80"/></w:pPr>'
            . '<w:rPr><w:b/><w:bCs/><w:sz w:val="25"/><w:szCs w:val="25"/></w:rPr></w:style>'
            . '</w:styles>');

        $body = '';
        foreach ($blocks as $i => [$type, $content]) {
            $body .= match ($type) {
                'h' => self::paragraph((string) $content, $i === 0 ? 'Heading1' : 'Heading2'),
                'table' => self::table((array) $content),
                default => self::paragraph((string) $content),
            };
        }
        // صفحه A4 با حاشیه ۲ سانتی‌متر؛ بخش آخر هم راست‌به‌چپ.
        $body .= '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
            . '<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567" w:gutter="0"/>'
            . '<w:bidi/></w:sectPr>';
        $zip->addFromString('word/document.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'
            . $body . '</w:body></w:document>');
        $zip->close();
    }

    private static function paragraph(string $text, ?string $style = null, bool $tight = false): string
    {
        return '<w:p>' . self::pPr($text, $style, $tight) . self::runs($text) . '</w:p>';
    }

    /**
     * بندی که حرف فارسی دارد راست‌به‌چپ است. بندی که ندارد (مختصات، کد، اثر
     * انگشت) چپ‌به‌راست ولی چسبیده به راست: در بند راست‌به‌چپ، «33.6, 46.4»
     * به ترتیب وارونه نمایش داده می‌شد.
     */
    private static function pPr(string $text, ?string $style, bool $tight): string
    {
        $rtl = preg_match('/\p{Arabic}/u', $text) === 1;
        return '<w:pPr>' . ($style === null ? '' : '<w:pStyle w:val="' . $style . '"/>')
            . ($rtl ? '<w:bidi/>' : '') . ($tight ? '<w:spacing w:after="0"/>' : '')
            . ($rtl ? '' : '<w:jc w:val="right"/>') . '</w:pPr>';
    }

    /** متن چندخطی: هر خط یک run، با شکست خط میانشان. */
    private static function runs(string $text, bool $bold = false): string
    {
        $parts = [];
        foreach (preg_split('/\R/u', $text) ?: [''] as $line) {
            // خطی که هیچ حرف فارسی ندارد (مختصات، کد، اثر انگشت) چپ‌به‌راست می‌ماند،
            // وگرنه «33.6, 46.4» وارونه خوانده می‌شود.
            $rtl = preg_match('/\p{Arabic}/u', $line) === 1;
            $rPr = '<w:rPr>' . ($bold ? '<w:b/><w:bCs/>' : '') . ($rtl ? '<w:rtl/>' : '') . '</w:rPr>';
            $parts[] = '<w:r>' . $rPr . '<w:t xml:space="preserve">' . self::xml($line) . '</w:t></w:r>';
        }
        return implode('<w:r><w:br/></w:r>', $parts);
    }

    /** @param array<int, array{0:string, 1:string}> $rows */
    private static function table(array $rows): string
    {
        if ($rows === []) {
            return '';
        }
        $border = '<w:top w:val="single" w:sz="4" w:color="888888"/><w:bottom w:val="single" w:sz="4" w:color="888888"/>'
            . '<w:left w:val="single" w:sz="4" w:color="888888"/><w:right w:val="single" w:sz="4" w:color="888888"/>'
            . '<w:insideH w:val="single" w:sz="4" w:color="888888"/><w:insideV w:val="single" w:sz="4" w:color="888888"/>';
        $xml = '<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w="5000" w:type="pct"/><w:tblBorders>' . $border
            . '</w:tblBorders><w:tblCellMar><w:left w:w="100" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tblCellMar>'
            . '</w:tblPr><w:tblGrid><w:gridCol w:w="2900"/><w:gridCol w:w="6700"/></w:tblGrid>';
        foreach ($rows as [$label, $value]) {
            $xml .= '<w:tr>'
                . '<w:tc><w:tcPr><w:tcW w:w="2900" w:type="dxa"/><w:shd w:val="clear" w:color="auto" w:fill="EEEEEE"/></w:tcPr>'
                . '<w:p>' . self::pPr((string) $label, null, true) . self::runs((string) $label, true) . '</w:p></w:tc>'
                . '<w:tc><w:tcPr><w:tcW w:w="6700" w:type="dxa"/></w:tcPr>'
                . '<w:p>' . self::pPr((string) $value, null, true) . self::runs((string) $value) . '</w:p></w:tc>'
                . '</w:tr>';
        }
        // پس از جدول یک بند خالی لازم است، وگرنه دو جدول پشت هم به هم می‌چسبند.
        return $xml . '</w:tbl><w:p/>';
    }

    private static function xml(string $text): string
    {
        $clean = (string) preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/u', '', $text);
        return htmlspecialchars($clean, ENT_XML1 | ENT_QUOTES, 'UTF-8');
    }
}
