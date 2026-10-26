<?php
declare(strict_types=1);

/**
 * «جدول اطلاعات تکمیلی»: مشخصات مشترک و کنتور هر مرکز از سامانه مشترکین، برگه دوم
 * فایل آمار. از فایل وارد می‌شود (center_subscribers) و با شماره اشتراک یا شناسه قبض
 * به سطر آمار وصل است؛ ستون‌هایی که فایل نداشت و پرونده دارد از پرونده پر می‌شوند.
 */
final class CenterSupplement
{
    public const LABELS = ['ردیف', 'ناحیه', 'پرونده', 'اشتراک', 'شناسه قبض', 'نوع مشترک', 'نام مشترک', 'آدرس مشترک',
        'تلفن همراه', 'کد ملی', 'کد پستی', 'فاز', 'آمپر', 'دیماند قرارداد', 'نوع مصرف', 'نوع ولتاژ', 'مامور', 'روزکار',
        'ترتیب', 'تاریخ نصب', 'نوع منطقه', 'نوع انشعاب', 'نوع کنتور', 'فابریک کنتور', 'شرکت سازنده کنتور', 'مدل کنتور',
        'مدل کنتور', 'ضریب کنتور', 'تعداد ارقام کنتور', 'تعداد تعرفه اکتیو', 'تاریخ آخرین تست', 'تاریخ آخرین قرائت',
        'قرائت اکتیو1', 'قرائت اکتیو2', 'قرائت اکتیو3', 'قرائت راکتیو', 'ولتاژ یک', 'ولتاژ دو', 'ولتاژ سه',
        'تعداد دفعات کد مانع قرائت کنتور سوخته یا معیوب'];

    /** تعریف‌های سرستون برای CenterHeader: s1، s2… (ردیف وارد نمی‌شود). */
    public static function defs(): array
    {
        $defs = [];
        foreach (self::LABELS as $i => $label) {
            $defs['s' . $i] = [[$label]];
        }
        $defs['s39'][] = ['تعداد دفعات کد مانع قرانت کنتور سوخته یا معیوب'];
        $defs['s39'][] = ['تعداد دفعات کد مانع قرائت'];
        return $defs;
    }

    /** برگه تکمیلی است؟ دست‌کم شش سرستون آن پیدا شده و اشتراک یا شناسه قبض میانشان. */
    public static function looksLike(array $map): bool
    {
        return count($map) >= 6 && (isset($map['s3']) || isset($map['s4']));
    }

    /** یک سطر فایل به داده ذخیره‌ای (شماره ستون → مقدار متنی). */
    public static function record(array $cells, array $map): array
    {
        $data = [];
        foreach ($map as $key => [$first, $last]) {
            $index = (int) substr($key, 1);
            if ($index === 0) {
                continue;
            }
            for ($c = $first; $c <= $last; $c++) {
                $value = $cells[$c] ?? null;
                if ($value !== null && $value !== '') {
                    $data[$index] = CenterValues::normalize('text', $value);
                    break;
                }
            }
        }
        return $data;
    }

    /** ذخیره یا جایگزینی سطر تکمیلی یک سال با همان اشتراک یا شناسه قبض. */
    public static function store(int $year, array $data): bool
    {
        $subscription = CenterMatch::clean($data[3] ?? null);
        $bill = CenterMatch::clean($data[4] ?? null);
        if ($subscription === null && $bill === null) {
            return false;
        }
        $existing = Db::one(
            'SELECT id FROM center_subscribers WHERE year = ? AND ((subscription IS NOT NULL AND subscription = ?) OR (bill_id IS NOT NULL AND bill_id = ?))',
            [$year, $subscription ?? '-', $bill ?? '-']
        );
        $json = json_encode($data, JSON_UNESCAPED_UNICODE);
        $now = Db::now();
        if ($existing !== null) {
            Db::run('UPDATE center_subscribers SET data_json = ?, subscription = ?, bill_id = ?, file_number = ?, updated_at = ? WHERE id = ?',
                [$json, $subscription, $bill, CenterMatch::clean($data[2] ?? null), $now, $existing['id']]);
        } else {
            Db::run('INSERT INTO center_subscribers (id, year, subscription, bill_id, file_number, data_json, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
                [Db::uuid(), $year, $subscription, $bill, CenterMatch::clean($data[2] ?? null), $json, $now, $now]);
        }
        return true;
    }

    /**
     * سطرهای برگه تکمیلی برای سطرهای آمار یک خروجی، به همان ترتیب؛ ستون ردیف شماره سطر
     * همان مرکز در برگه اصلی است. @return list<list<string|int|float|null>>
     */
    public static function rows(array $rows): array
    {
        $years = array_values(array_unique(array_column($rows, 'year')));
        $stored = [];
        if ($years !== []) {
            $in = implode(',', array_fill(0, count($years), '?'));
            foreach (Db::all("SELECT year, subscription, bill_id, data_json FROM center_subscribers WHERE year IN ($in)", $years) as $s) {
                $data = CenterValues::decode($s['data_json']);
                foreach (['sub' => $s['subscription'], 'bill' => $s['bill_id']] as $kind => $key) {
                    if ($key !== null) {
                        $stored[$s['year'] . ":$kind:$key"] = $data;
                    }
                }
            }
        }
        $reports = self::reports(array_values(array_filter(array_column($rows, 'report_id'))));
        $out = [];
        foreach ($rows as $row) {
            $v = $row['values'];
            $data = $stored[$row['year'] . ':sub:' . CenterMatch::clean($v['subscription'] ?? null)]
                ?? $stored[$row['year'] . ':bill:' . CenterMatch::clean($v['bill_id'] ?? null)] ?? [];
            $r = $reports[$row['report_id']] ?? null;
            if ($data === [] && $r === null) {
                continue;
            }
            $fallback = [
                1 => $row['area_code'], 2 => $v['file_number'] ?? null, 3 => $v['subscription'] ?? null, 4 => $v['bill_id'] ?? null,
                6 => $v['subscriber'] ?? null, 7 => $r['address'] ?? null, 8 => $r['owner_phone'] ?? null,
                9 => $r['owner_national_id'] ?? null, 10 => $r['postal_code'] ?? null,
                11 => isset($r['phase_type']) ? ((int) $r['phase_type'] === 1 ? 3 : 1) : null,
                22 => $v['meter_type'] ?? null,
            ];
            $line = [(int) $v['row']];
            for ($i = 1; $i < count(self::LABELS); $i++) {
                $line[] = $data[$i] ?? $fallback[$i] ?? null;
            }
            $out[] = $line;
        }
        return $out;
    }

    /** @return array<string, array<string, mixed>> */
    private static function reports(array $ids): array
    {
        if ($ids === []) {
            return [];
        }
        $in = implode(',', array_fill(0, count($ids), '?'));
        $out = [];
        foreach (Db::all("SELECT id, address, owner_phone, owner_national_id, postal_code, phase_type FROM reports WHERE id IN ($in)", $ids) as $r) {
            $out[$r['id']] = $r;
        }
        return $out;
    }
}
