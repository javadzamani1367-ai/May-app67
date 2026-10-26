<?php
declare(strict_types=1);

/**
 * کوچک‌ترین فایل Word درست برای گزارش یک مورد: عنوان، بند، و جدول دوستونی،
 * همه راست‌به‌چپ. بدون کتابخانه، با ZipArchive — همان راهی که اپ برای Word
 * می‌رود (ساخت مستقیم OOXML). قلم Tahoma است که روی ویندوز ۷ هم هست.
 *
 * بلوک‌ها: ['h', متن] عنوان، ['p', متن] بند، ['table', [[عنوان، مقدار]...]]،
 * ['grid', [سرستون‌ها، سطرها، پهنا]] جدول چندستونی (پهنای اختیاری هر ستون به درصد)، ['image', [مسیر فایل، شرح]] تصویر.
 */
final class Docx
{
    private const FONT = 'Tahoma';

    /** @param array<int, array{0:string, 1:mixed}> $blocks */
    public static function send(string $name, array $blocks, array $options = []): void
    {
        $path = tempnam(sys_get_temp_dir(), 'docx');
        self::write($path, $blocks, $options);
        header('Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document');
        header('Content-Disposition: attachment; filename="' . $name . '.docx"');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        @unlink($path);
    }

    /** پهنای جدول چندستونی به dxa؛ برگه افقی پهن‌تر است. */
    private static int $tableWidth = 9600;

    /**
     * @param array<int, array{0:string, 1:mixed}> $blocks
     * @param array{landscape?: bool, size?: int} $options برگه افقی؛ اندازه قلم به نیم‌نقطه (۱۴ یعنی ۷)،
     *        برای جدول‌های پرستون مثل آمار جامع
     */
    public static function write(string $path, array $blocks, array $options = []): void
    {
        $size = (int) ($options['size'] ?? 22);
        self::$tableWidth = empty($options['landscape']) ? 9600 : 15900;
        $zip = new ZipArchive();
        if ($zip->open($path, ZipArchive::CREATE | ZipArchive::OVERWRITE) !== true) {
            throw new RuntimeException('cannot create docx');
        }
        $zip->addFromString('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            . '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            . '<Default Extension="xml" ContentType="application/xml"/>'
            . '<Default Extension="jpg" ContentType="image/jpeg"/>'
            . '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
            . '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>'
            . '</Types>');
        $zip->addFromString('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
            . '</Relationships>');
        $font = '<w:rFonts w:ascii="' . self::FONT . '" w:hAnsi="' . self::FONT . '" w:cs="' . self::FONT . '"/>';
        $zip->addFromString('word/styles.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            . '<w:docDefaults><w:rPrDefault><w:rPr>' . $font . '<w:sz w:val="' . $size . '"/><w:szCs w:val="' . $size . '"/>'
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
        $images = [];
        foreach ($blocks as $i => [$type, $content]) {
            $body .= match ($type) {
                'h' => self::paragraph((string) $content, $i === 0 ? 'Heading1' : 'Heading2'),
                'table' => self::table((array) $content),
                'grid' => self::grid((array) $content[0], (array) $content[1], (array) ($content[2] ?? [])),
                'image' => self::image($zip, (string) $content[0], (string) $content[1], $images),
                default => self::paragraph((string) $content),
            };
        }
        $zip->addFromString('word/_rels/document.xml.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            . '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            . implode('', array_map(static fn ($n) => '<Relationship Id="rImg' . $n . '" Type="http://schemas.openxmlformats.org/'
                . 'officeDocument/2006/relationships/image" Target="media/image' . $n . '.jpg"/>', $images))
            . '</Relationships>');
        // صفحه A4 با حاشیه ۲ سانتی‌متر؛ بخش آخر هم راست‌به‌چپ.
        $landscape = !empty($options['landscape']);
        $body .= '<w:sectPr>' . ($landscape ? '<w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/>' : '<w:pgSz w:w="11906" w:h="16838"/>')
            . ($landscape ? '<w:pgMar w:top="567" w:right="454" w:bottom="567" w:left="454" w:header="340" w:footer="340" w:gutter="0"/>'
                : '<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567" w:gutter="0"/>')
            . '<w:bidi/></w:sectPr>';
        $zip->addFromString('word/document.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            . '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
            . 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" '
            . 'xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" '
            . 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" '
            . 'xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>'
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

    /**
     * جدول چندستونی با سطر عنوان پررنگ؛ ستون‌ها از راست، هم‌عرض مگر پهنایشان داده شده باشد.
     *
     * @param list<string> $header
     * @param list<list<string>> $rows
     * @param list<int|float> $percents پهنای هر ستون به درصد
     */
    private static function grid(array $header, array $rows, array $percents = []): string
    {
        if ($rows === [] || $header === []) {
            return '';
        }
        $widths = count($percents) === count($header)
            ? array_map(static fn ($p): int => (int) round(self::$tableWidth * $p / array_sum($percents)), $percents)
            : array_fill(0, count($header), intdiv(self::$tableWidth, count($header)));
        $column = 0;
        $cell = static function (string $text, bool $head) use (&$column, $widths): string {
            $width = $widths[$column++ % count($widths)];
            return '<w:tc><w:tcPr><w:tcW w:w="' . $width . '" w:type="dxa"/>'
                . ($head ? '<w:shd w:val="clear" w:color="auto" w:fill="EEEEEE"/>' : '') . '</w:tcPr><w:p>'
                . self::pPr($text, null, true) . self::runs($text, $head) . '</w:p></w:tc>';
        };
        $xml = '<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w="5000" w:type="pct"/><w:tblBorders>'
            . '<w:top w:val="single" w:sz="4" w:color="888888"/><w:bottom w:val="single" w:sz="4" w:color="888888"/>'
            . '<w:left w:val="single" w:sz="4" w:color="888888"/><w:right w:val="single" w:sz="4" w:color="888888"/>'
            . '<w:insideH w:val="single" w:sz="4" w:color="888888"/><w:insideV w:val="single" w:sz="4" w:color="888888"/>'
            . '</w:tblBorders><w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid>'
            . implode('', array_map(static fn (int $w): string => '<w:gridCol w:w="' . $w . '"/>', $widths))
            . '</w:tblGrid><w:tr><w:trPr><w:tblHeader/></w:trPr>'
            . implode('', array_map(static fn ($h) => $cell((string) $h, true), $header)) . '</w:tr>';
        foreach ($rows as $row) {
            $column = 0;
            $xml .= '<w:tr><w:trPr><w:cantSplit/></w:trPr>' . implode('', array_map(static fn ($v) => $cell((string) $v, false), $row)) . '</w:tr>';
        }
        return $xml . '</w:tbl><w:p/>';
    }

    /**
     * یک تصویر JPEG در عرض حداکثر ۱۶ سانتی‌متر، با شرحش زیرش. فایلی که JPEG
     * نیست یا خوانده نمی‌شود فقط شرحش را می‌گذارد.
     *
     * @param list<int> $images شماره تصویرهای افزوده‌شده، برای رابطه‌ها
     */
    private static function image(ZipArchive $zip, string $path, string $caption, array &$images): string
    {
        $size = is_file($path) ? @getimagesize($path) : false;
        if ($size === false || ($size[2] ?? 0) !== IMAGETYPE_JPEG) {
            return self::paragraph($caption);
        }
        $n = count($images) + 1;
        $images[] = $n;
        $zip->addFile($path, 'word/media/image' . $n . '.jpg');
        $cx = 5760000;
        $cy = (int) round($cx * $size[1] / max(1, $size[0]));
        if ($cy > 6480000) {
            $cx = (int) round($cx * 6480000 / $cy);
            $cy = 6480000;
        }
        $drawing = '<w:r><w:drawing><wp:inline><wp:extent cx="' . $cx . '" cy="' . $cy . '"/>'
            . '<wp:docPr id="' . $n . '" name="image' . $n . '"/><a:graphic><a:graphicData '
            . 'uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr>'
            . '<pic:cNvPr id="' . $n . '" name="image' . $n . '.jpg"/><pic:cNvPicPr/></pic:nvPicPr>'
            . '<pic:blipFill><a:blip r:embed="rImg' . $n . '"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
            . '<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="' . $cx . '" cy="' . $cy . '"/></a:xfrm>'
            . '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>'
            . '</wp:inline></w:drawing></w:r>';
        return '<w:p><w:pPr><w:keepNext/><w:jc w:val="center"/></w:pPr>' . $drawing . '</w:p>' . self::paragraph($caption);
    }

    private static function xml(string $text): string
    {
        $clean = (string) preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/u', '', $text);
        return htmlspecialchars($clean, ENT_XML1 | ENT_QUOTES, 'UTF-8');
    }
}
