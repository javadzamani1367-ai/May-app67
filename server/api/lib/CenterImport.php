<?php
declare(strict_types=1);

/**
 * ورود فایل اکسل آمار سال‌های پیش یا فایل جاری: سرستون، ستون‌ها و سطرها خودکار پیدا
 * می‌شوند (CenterHeader)، حتی اگر ستون‌ها جابه‌جا یا کم و زیاد باشند.
 *
 * هر سطر فایل با سطر موجود همان سال جور می‌شود (اشتراک، شناسه قبض، شماره پرونده، کد
 * سامانه پیامکی)؛ پرونده‌های تأییدشده پیش از آن سطر خودشان را گرفته‌اند، پس جور شدن
 * با سطر یعنی جور شدن با پرونده. مقدار فایل جای مقدار سامانه را نمی‌گیرد: اگر فرق
 * داشتند خانه ناسازگار می‌شود تا مدیر انتخاب کند. سطر جورنشده سطر تازه است.
 */
final class CenterImport
{
    /** کلیدهایی که یک سطر فایل را «سطر داده» می‌کنند. */
    private const IDS = ['subscription', 'bill_id', 'file_number', 'sms_code', 'subscriber', 'finder_name', 'miners'];

    /**
     * برگه‌های فایل با سطرهای خوانده‌شده.
     *
     * @return list<array{name:string, kind:string, columns:array<string,string>, unknown:list<string>, rows:list<array>}>
     *         kind: main، supplement یا none
     */
    public static function parse(string $path, int $defaultYear): array
    {
        $out = [];
        $defs = self::defs();
        foreach (XlsxReader::read($path) as $sheet) {
            $header = CenterHeader::detect($sheet, $defs);
            $supp = CenterHeader::detect($sheet, CenterSupplement::defs());
            $mainScore = $header === null ? 0 : count($header['map']);
            if ($supp !== null && CenterSupplement::looksLike($supp['map']) && count($supp['map']) > $mainScore) {
                $out[] = ['name' => $sheet['name'], 'kind' => 'supplement', 'columns' => [], 'unknown' => $supp['unknown'],
                    'rows' => self::suppRows($sheet, $supp, self::sheetYear($sheet['name']) ?? $defaultYear)];
                continue;
            }
            if ($header === null || $mainScore < 4) {
                $out[] = ['name' => $sheet['name'], 'kind' => 'none', 'columns' => [], 'unknown' => [], 'rows' => []];
                continue;
            }
            $columns = [];
            foreach (array_keys($header['map']) as $key) {
                $columns[$key] = CenterColumns::label($key);
            }
            $out[] = ['name' => $sheet['name'], 'kind' => 'main', 'columns' => $columns, 'unknown' => $header['unknown'],
                'rows' => self::rows($sheet, $header, self::sheetYear($sheet['name']) ?? $defaultYear)];
        }
        return $out;
    }

    /** @return list<array{line:int, year:int, date:int, area:?string, values:array<string, mixed>}> */
    private static function rows(array $sheet, array $header, int $year): array
    {
        $rows = [];
        foreach ($sheet['cells'] as $r => $cells) {
            if ($r <= $header['bottom']) {
                continue;
            }
            $head = CenterValues::cleanText(implode(' ', array_map('strval', array_slice($cells, 0, 3, true))));
            if (str_contains($head, 'جمع')) {
                continue;
            }
            $values = [];
            foreach ($header['map'] as $key => [$first, $last]) {
                $column = CenterColumns::get($key);
                if ($column === null || $key === 'row') {
                    continue;
                }
                for ($c = $first; $c <= $last; $c++) {
                    $value = CenterValues::normalize($column['type'], $cells[$c] ?? null);
                    if ($value !== null) {
                        $values[$key] = $value;
                        break;
                    }
                }
            }
            if (array_intersect_key($values, array_flip(self::IDS)) === []) {
                continue;
            }
            $date = $values['report_date'] ?? $values['visit_date'] ?? null;
            $rowYear = $date !== null ? FieldCodes::jalaliYear((int) $date) : $year;
            $county = trim(explode('/', (string) ($values['county'] ?? ''))[0]);
            $rows[] = ['line' => $r + 1, 'year' => $rowYear, 'date' => $date ?? Jalali::startOfDay($rowYear, 1, 1) + 43_200_000,
                'area' => Areas::fromCounty($county !== '' ? $county : null), 'values' => $values];
        }
        return $rows;
    }

    /** @return list<array{line:int, year:int, data:array<int, string>}> */
    private static function suppRows(array $sheet, array $header, int $year): array
    {
        $rows = [];
        foreach ($sheet['cells'] as $r => $cells) {
            if ($r > $header['bottom']) {
                $data = CenterSupplement::record($cells, $header['map']);
                if (isset($data[3]) || isset($data[4])) {
                    $rows[] = ['line' => $r + 1, 'year' => $year, 'data' => $data];
                }
            }
        }
        return $rows;
    }

    /** مسیرهای سرستون هر ستون آمار برای جست‌وجو: خود فرم، و نام‌های دیگر. */
    public static function defs(): array
    {
        $defs = [];
        foreach (CenterColumns::all() as $key => $c) {
            $defs[$key] = [array_map(static fn (array $level) => $level[0], $c['head'])];
            foreach (CenterHeader::ALIASES[$key] ?? [] as $alias) {
                $defs[$key][] = [$alias];
            }
        }
        // برچسب کوتاه ستون هم پذیرفته است، تا خروجی خود سامانه دوباره وارد شود.
        foreach (array_keys($defs) as $key) {
            $defs[$key][] = [CenterColumns::label($key)];
        }
        return $defs;
    }

    private static function sheetYear(string $name): ?int
    {
        $latin = Jalali::latin(trim($name));
        return preg_match('/^(1[34]\d\d)$/', $latin, $m) === 1 ? (int) $m[1] : null;
    }
}
