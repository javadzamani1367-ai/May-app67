<?php
declare(strict_types=1);

/**
 * خواندن فایل اکسل (xlsx) بی کتابخانه: هر برگه با نامش، خانه‌ها (متن، عدد، مقدار
 * ذخیره‌شده فرمول) و محدوده‌های ادغام. فقط ZipArchive و SimpleXML، که هاست اشتراکی دارد.
 *
 * فایل از بیرون می‌آید: اندازه بازشده هر بخش محدود است (فایل فشرده‌ای که باز شود و
 * حافظه را پر کند رد می‌شود)، و SimpleXML بی LIBXML_NOENT موجودیت بیرونی را نمی‌خواند.
 */
final class XlsxReader
{
    private const MAX_PART = 40 * 1024 * 1024;
    private const MAX_ROWS = 20000;
    private const MAX_COLS = 200;

    /**
     * @return list<array{name:string, cells:array<int, array<int, string|float>>, merges:list<array{0:int,1:int,2:int,3:int}>}>
     *         سطر و ستون از صفر؛ ادغام [سطر اول، ستون اول، سطر آخر، ستون آخر]
     */
    public static function read(string $path): array
    {
        $zip = new ZipArchive();
        if ($zip->open($path) !== true) {
            throw new RuntimeException('not a zip');
        }
        try {
            $shared = self::shared($zip);
            $workbook = self::xml($zip, 'xl/workbook.xml');
            $rels = self::xml($zip, 'xl/_rels/workbook.xml.rels');
            if ($workbook === null || $rels === null) {
                throw new RuntimeException('no workbook');
            }
            $targets = [];
            foreach ($rels->Relationship as $rel) {
                $target = ltrim((string) $rel['Target'], '/');
                $targets[(string) $rel['Id']] = str_starts_with($target, 'xl/') ? $target : 'xl/' . $target;
            }
            $sheets = [];
            foreach ($workbook->sheets->sheet as $sheet) {
                $rid = (string) $sheet->attributes('http://schemas.openxmlformats.org/officeDocument/2006/relationships')['id'];
                $xml = isset($targets[$rid]) ? self::xml($zip, $targets[$rid]) : null;
                if ($xml !== null) {
                    $sheets[] = ['name' => (string) $sheet['name']] + self::sheet($xml, $shared);
                }
            }
            return $sheets;
        } finally {
            $zip->close();
        }
    }

    /** @return array{cells:array<int, array<int, string|float>>, merges:list<array{0:int,1:int,2:int,3:int}>} */
    private static function sheet(SimpleXMLElement $xml, array $shared): array
    {
        $cells = [];
        $count = 0;
        foreach ($xml->sheetData->row ?? [] as $row) {
            if (++$count > self::MAX_ROWS) {
                break;
            }
            $r = (int) $row['r'] - 1;
            $next = 0;
            foreach ($row->c as $c) {
                $ref = (string) $c['r'];
                [$cr, $cc] = $ref !== '' ? self::ref($ref) : [$r, $next];
                $next = $cc + 1;
                if ($cc >= self::MAX_COLS) {
                    continue;
                }
                $value = self::value($c, $shared);
                if ($value !== null && $value !== '') {
                    $cells[$cr][$cc] = $value;
                }
            }
        }
        $merges = [];
        foreach ($xml->mergeCells->mergeCell ?? [] as $merge) {
            $parts = explode(':', (string) $merge['ref']);
            if (count($parts) === 2) {
                [$r1, $c1] = self::ref($parts[0]);
                [$r2, $c2] = self::ref($parts[1]);
                $merges[] = [$r1, $c1, $r2, $c2];
            }
        }
        ksort($cells);
        return ['cells' => $cells, 'merges' => $merges];
    }

    private static function value(SimpleXMLElement $c, array $shared): string|float|null
    {
        $type = (string) $c['t'];
        if ($type === 'inlineStr') {
            return self::text($c->is);
        }
        if (!isset($c->v)) {
            return null;
        }
        $raw = (string) $c->v;
        return match ($type) {
            's' => $shared[(int) $raw] ?? null,
            'str', 'e' => $type === 'e' ? null : $raw,
            'b' => (float) $raw,
            default => is_numeric($raw) ? (float) $raw : $raw,
        };
    }

    /** @return list<string> */
    private static function shared(ZipArchive $zip): array
    {
        $xml = self::xml($zip, 'xl/sharedStrings.xml');
        $out = [];
        foreach ($xml?->si ?? [] as $si) {
            $out[] = self::text($si);
        }
        return $out;
    }

    /** متن یک خانه، با تکه‌های قالب‌دار (r) و بی آوانویسی (rPh). */
    private static function text(SimpleXMLElement $node): string
    {
        if (isset($node->t)) {
            return (string) $node->t;
        }
        $text = '';
        foreach ($node->r as $run) {
            $text .= (string) $run->t;
        }
        return $text;
    }

    /** «AB12» → [۱۱, ۲۷]، از صفر. @return array{0:int, 1:int} */
    public static function ref(string $ref): array
    {
        preg_match('/^([A-Z]+)(\d+)$/', strtoupper($ref), $m);
        $col = 0;
        foreach (str_split($m[1] ?? 'A') as $ch) {
            $col = $col * 26 + (ord($ch) - 64);
        }
        return [(int) ($m[2] ?? 1) - 1, $col - 1];
    }

    private static function xml(ZipArchive $zip, string $name): ?SimpleXMLElement
    {
        $stat = $zip->statName($name);
        if ($stat === false) {
            return null;
        }
        if ($stat['size'] > self::MAX_PART) {
            throw new RuntimeException('part too large');
        }
        $content = $zip->getFromName($name);
        if ($content === false) {
            return null;
        }
        $previous = libxml_use_internal_errors(true);
        $xml = simplexml_load_string($content, SimpleXMLElement::class, LIBXML_NONET | LIBXML_COMPACT);
        libxml_use_internal_errors($previous);
        return $xml === false ? null : $xml;
    }
}
