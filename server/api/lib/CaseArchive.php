<?php
declare(strict_types=1);

/**
 * آرشیو وب پرونده‌های بازدید: همان کار نرم‌افزار ویندوز (جستجو، گزارش‌گیری،
 * خروجی اکسل) روی پرونده‌هایی که گوشی‌ها به سرور فرستاده‌اند. فیلترها و
 * آمار یک‌جا اینجاست تا فهرست، صفحه آمار و اکسل هرگز دو عدد متفاوت نگویند.
 */
final class CaseArchive
{
    /** هر پرونده با شمار دستگاه‌ها، مجموع توان دستگاه‌ها و نام کارشناس. */
    private const SELECT = 'SELECT r.*, u.full_name AS expert_name,
        (SELECT COUNT(*) FROM devices d WHERE d.report_id = r.id) AS device_count,
        (SELECT COALESCE(SUM(d.power_watt), 0) FROM devices d WHERE d.report_id = r.id) AS device_watt
        FROM reports r LEFT JOIN users u ON u.user_code = r.expert_code';

    /**
     * فیلترها از پارامترهای صفحه؛ مقدار نامعتبر نادیده گرفته می‌شود.
     * بازه تاریخ روی تاریخ گزارش است، مثل خروجی ویندوز.
     *
     * @return array{where:string, params:list<mixed>, values:array<string, string>}
     */
    public static function filters(array $get, ?Scope $scope = null): array
    {
        [$scoped, $params] = $scope?->reportWhere('r') ?? ['1 = 1', []];
        $where = [$scoped];
        $values = [];
        $area = Areas::code($get['area'] ?? null);
        if ($area !== null) {
            $where[] = 'r.area_code = ?';
            $params[] = $area;
            $values['area'] = $area;
        }
        $text = static fn (string $k): string => trim((string) ($get[$k] ?? ''));
        $coded = ['type' => ['r.report_type', CaseLabels::REPORT_TYPES], 'status' => ['r.status', CaseLabels::STATUSES],
                  'approval' => ['r.approval_state', CaseLabels::APPROVALS]];
        foreach ($coded as $key => [$column, $names]) {
            if ($text($key) !== '' && isset($names[(int) $text($key)])) {
                $where[] = "$column = ?";
                $params[] = (int) $text($key);
                $values[$key] = $text($key);
            }
        }
        foreach (['county' => 'r.county', 'expert' => 'r.expert_code'] as $key => $column) {
            if ($text($key) !== '') {
                $where[] = "$column = ?";
                $params[] = mb_substr($text($key), 0, 100);
                $values[$key] = $text($key);
            }
        }
        if ($text('q') !== '') {
            $q = mb_substr(self::latin($text('q')), 0, 64);
            $like = '%' . str_replace(['\\', '%', '_'], ['\\\\', '\\%', '\\_'], $q) . '%';
            $where[] = '(r.tracking_code LIKE ? OR r.temp_code LIKE ? OR r.address LIKE ? OR r.subscription_number LIKE ?
                        OR r.owner_name LIKE ? OR r.file_number LIKE ? OR r.bill_number LIKE ? OR r.subscriber_name LIKE ?)';
            array_push($params, $like, $like, $like, $like, $like, $like, $like, $like);
            $values['q'] = $text('q');
        }
        foreach (['from' => ['>=', 0], 'to' => ['<', 86_400_000]] as $key => [$op, $shift]) {
            $date = Jalali::parse($text($key));
            if ($date !== null) {
                $where[] = "r.report_date $op ?";
                $params[] = Jalali::startOfDay(...$date) + $shift;
                $values[$key] = $text($key);
            }
        }
        if ($text('tampered') === '1') {
            $where[] = 'r.meter_tampered = 1';
            $values['tampered'] = '1';
        }
        return ['where' => implode(' AND ', $where), 'params' => $params, 'values' => $values];
    }

    /** ارقام فارسی و عربی جستجو لاتین می‌شوند؛ کدها و شماره‌ها لاتین ذخیره شده‌اند. */
    public static function latin(string $text): string
    {
        return strtr($text, ['۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4', '۵' => '5', '۶' => '6',
            '۷' => '7', '۸' => '8', '۹' => '9', '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4',
            '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9']);
    }

    /** پرونده‌ها؛ تازه‌ترین گزارش اول. */
    public static function rows(array $filter, ?int $limit = null, int $offset = 0): array
    {
        $sql = self::SELECT . " WHERE {$filter['where']} ORDER BY r.report_date DESC, r.created_at DESC";
        if ($limit !== null) {
            $sql .= ' LIMIT ' . max(1, $limit) . ' OFFSET ' . max(0, $offset);
        }
        return Db::all($sql, $filter['params']);
    }

    public static function count(array $filter): int
    {
        return (int) (Db::one("SELECT COUNT(*) AS n FROM reports r WHERE {$filter['where']}", $filter['params'])['n'] ?? 0);
    }

    /**
     * آمار همان پرونده‌هایی که فیلتر می‌گوید: شمار و توان، تفکیک وضعیت، نوع،
     * شهرستان، کارشناس و ماه، و یافته‌های فنی.
     *
     * @return array<string, mixed>
     */
    public static function stats(array $filter): array
    {
        $w = $filter['where'];
        $p = $filter['params'];
        $one = static fn (string $sql) => Db::one($sql, $p) ?? [];
        $totals = $one("SELECT COUNT(*) AS cases, COALESCE(SUM(r.total_watt), 0) AS watt,
            SUM(r.meter_tampered = 1) AS tampered, SUM(r.tap_point = 0) AS before_meter,
            SUM(r.tariff_type = 5) AS no_supply FROM reports r WHERE $w");
        $devices = $one("SELECT COUNT(*) AS n FROM devices d JOIN reports r ON r.id = d.report_id WHERE $w");
        $group = static fn (string $column) => Db::all(
            "SELECT $column AS k, COUNT(*) AS n, COALESCE(SUM(r.total_watt), 0) AS watt FROM reports r
             WHERE $w GROUP BY $column ORDER BY n DESC", $p);
        $months = [];
        foreach (Db::all("SELECT r.report_date FROM reports r WHERE $w", $p) as $row) {
            [$jy, $jm] = Jalali::fromGregorian(...array_map('intval', explode('-',
                gmdate('Y-m-d', intdiv((int) $row['report_date'], 1000) + 12600))));
            $key = sprintf('%04d/%02d', $jy, $jm);
            $months[$key] = ($months[$key] ?? 0) + 1;
        }
        ksort($months);
        return [
            'cases' => (int) ($totals['cases'] ?? 0),
            'watt' => (float) ($totals['watt'] ?? 0),
            'devices' => (int) ($devices['n'] ?? 0),
            'tampered' => (int) ($totals['tampered'] ?? 0),
            'before_meter' => (int) ($totals['before_meter'] ?? 0),
            'no_supply' => (int) ($totals['no_supply'] ?? 0),
            'status' => $group('r.status'),
            'type' => $group('r.report_type'),
            'county' => $group('r.county'),
            'expert' => Db::all("SELECT r.expert_code AS k, MAX(u.full_name) AS name, COUNT(*) AS n,
                COALESCE(SUM(r.total_watt), 0) AS watt FROM reports r LEFT JOIN users u ON u.user_code = r.expert_code
                WHERE $w GROUP BY r.expert_code ORDER BY n DESC", $p),
            'months' => array_slice($months, -12, null, true),
        ];
    }

    /** سرستون‌های اکسل، همان CLAUDE.md بخش ۱۱. */
    public const EXCEL_HEADER = ['کد رهگیری', 'نوع گزارش', 'تاریخ گزارش', 'تاریخ بازدید', 'شهرستان', 'بخش', 'آدرس',
        'شماره اشتراک', 'شماره پرونده', 'شماره قبض', 'نام مالک', 'آمپراژ کنتور', 'آمپراژ مصرفی', 'تعداد دستگاه',
        'مجموع توان (وات)', 'وضعیت', 'تأیید مدیر', 'کارشناس', 'مختصات', 'نام مشترک'];

    /** یک سطر اکسل؛ عددها عدد می‌مانند تا در اکسل جمع زده شوند. */
    public static function excelRow(array $r): array
    {
        $number = static fn ($v) => is_numeric($v) ? (float) $v : null;
        return [
            CaseLabels::code($r), CaseLabels::of(CaseLabels::REPORT_TYPES, $r['report_type']),
            Jalali::format((int) $r['report_date'], false),
            $r['visit_date'] === null ? null : Jalali::format((int) $r['visit_date'], false),
            CaseLabels::countyWithArea($r['county'], $r['area_code']), $r['district'], $r['address'],
            $r['subscription_number'], $r['file_number'], $r['bill_number'], $r['owner_name'],
            $number($r['meter_amperage']), $number($r['measured_amperage']), (int) $r['device_count'],
            $number($r['total_watt']), CaseLabels::of(CaseLabels::STATUSES, $r['status']),
            CaseLabels::of(CaseLabels::APPROVALS, $r['approval_state']),
            CaseReport::expert(trim((string) $r['expert_name']), (string) $r['expert_code']),
            is_numeric($r['latitude']) ? sprintf('%.6f, %.6f', $r['latitude'], $r['longitude']) : null,
            $r['subscriber_name'] ?? null,
        ];
    }
}
