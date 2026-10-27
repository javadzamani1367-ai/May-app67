<?php
declare(strict_types=1);

/**
 * آنچه سامانه برای هر سطر آمار می‌داند، هر بار تازه از پرونده: مکان، تاریخ‌ها، تعداد
 * و توان دستگاه‌ها، شناسه‌های مشترک، کمیسیون (صورتجلسه پیوست)، نوع انشعاب و کنتور.
 *
 * فقط پرونده تأییدشده رمزارز وارد آمار می‌شود؛ برق غیرمجاز نه.
 * «مجموع ظرفیت برآوردی دستگاه‌ها (KW)» توانی است که کارشناس در محل از آمپر و ولتاژ ساخت
 * (total_watt)؛ اگر نبود، مجموع توان دستگاه‌های ثبت‌شده. «کیلو وات شناسایی شده» مصرف یک
 * بازه است (چند ماه) و از صورتحساب می‌آید، نه از بازدید؛ پس دستی است.
 */
final class CenterSystem
{
    /** پرونده‌هایی که آمار می‌گیرند. */
    public const ELIGIBLE = 'r.approval_state = 2 AND r.case_kind = 0';

    /**
     * @param list<string> $ids
     * @return array<string, array<string, mixed>> شناسه پرونده → مقدار هر ستون
     */
    public static function values(array $ids): array
    {
        $ids = array_values(array_unique(array_filter($ids)));
        if ($ids === []) {
            return [];
        }
        $in = implode(',', array_fill(0, count($ids), '?'));
        $reports = Db::all(
            "SELECT r.*, u.full_name AS expert_name FROM reports r
             LEFT JOIN users u ON u.user_code = r.expert_code WHERE r.id IN ($in)",
            $ids
        );
        $devices = [];
        foreach (Db::all(
            "SELECT report_id, COUNT(*) AS n, SUM(COALESCE(power_watt, 0)) AS w FROM devices WHERE report_id IN ($in) GROUP BY report_id",
            $ids
        ) as $row) {
            $devices[$row['report_id']] = $row;
        }
        $commission = array_flip(array_column(Db::all(
            "SELECT DISTINCT report_id FROM attachments WHERE category = 2 AND report_id IN ($in)",
            $ids
        ), 'report_id'));

        $out = [];
        foreach ($reports as $r) {
            $out[$r['id']] = self::of($r, $devices[$r['id']] ?? null, isset($commission[$r['id']]));
        }
        return $out;
    }

    /** @return array<string, mixed> */
    private static function of(array $r, ?array $devices, bool $commission): array
    {
        $subscription = trim((string) ($r['subscription_number'] ?? ''));
        $tariff = $r['tariff_type'] === null ? null : (int) $r['tariff_type'];
        $hasBranch = $subscription !== '' && $tariff !== 5;
        $kw = $r['total_watt'] === null ? null : round((float) $r['total_watt'] / 1000, 2);
        $count = $devices === null ? null : (int) $devices['n'];
        $deviceKw = $devices === null || (float) $devices['w'] <= 0 ? null : round((float) $devices['w'] / 1000, 2);
        $type = (int) $r['report_type'];
        $county = trim((string) ($r['county'] ?? ''));
        $district = trim((string) ($r['district'] ?? ''));

        return [
            'county' => $district !== '' && $district !== $county ? "$county / $district" : ($county ?: null),
            'report_date' => self::millis($r['report_date']),
            'visit_date' => self::millis($r['visit_date']),
            'org_other' => match (true) {
                in_array($type, [1, 2, 3], true) => CenterColumns::OTHER_LEGEND[1],
                in_array($type, [4, 6], true) => CenterColumns::OTHER_LEGEND[2],
                default => null,
            },
            'miners' => $count,
            'sms_code' => $type === 1 ? self::text($r['tracking_code']) : null,
            'capacity_kw' => $kw ?? $deviceKw,
            'bill_id' => self::text($r['bill_number']),
            'subscriber' => self::text($r['subscriber_name']) ?? self::text($r['owner_name']),
            'subscription' => $subscription ?: null,
            'file_number' => self::text($r['file_number']),
            'commission' => $commission ? 1 : null,
            'branch_type' => $tariff !== null && $tariff >= 0 && $tariff <= 4 ? CaseLabels::TARIFFS[$tariff] : null,
            'with_found' => $hasBranch ? 1 : null,
            'without_found' => $hasBranch ? null : 1,
            'adjust_branch' => CenterColumns::ADJUST[$hasBranch ? 0 : 1],
            'meter_type' => match ($r['meter_type'] === null ? null : (int) $r['meter_type']) {
                0 => 'مکانیکی', 1 => 'دیجیتال', default => null,
            },
            'tracking_code' => CaseLabels::code($r),
            'area' => $r['area_code'] ? Areas::label($r['area_code']) : null,
            'address' => self::text($r['address']),
            'coordinates' => $r['latitude'] !== null && $r['longitude'] !== null
                ? sprintf('%.6f, %.6f', (float) $r['latitude'], (float) $r['longitude']) : null,
            'expert' => self::text($r['expert_name']) ?? self::text($r['expert_code']),
            'owner_name' => self::text($r['owner_name']),
            'owner_national_id' => self::text($r['owner_national_id']),
            'measured_amperage' => $r['measured_amperage'] === null ? null : round((float) $r['measured_amperage'], 1),
            // برای ساخت سطر و فیلتر؛ ستون نیستند.
            '_area_code' => $r['area_code'],
            '_row_date' => self::millis($r['report_date']) ?? self::millis($r['created_at']),
        ];
    }

    /**
     * حاضران هر پرونده، برای پاداش جمع‌آوری و عوامل انتظامی.
     * @return array<string, list<array{id:string, org:int, name:?string, org_name:?string, position:?string}>>
     */
    public static function attendees(array $ids): array
    {
        $ids = array_values(array_unique(array_filter($ids)));
        if ($ids === []) {
            return [];
        }
        $in = implode(',', array_fill(0, count($ids), '?'));
        $out = [];
        foreach (Db::all("SELECT id, report_id, organization, full_name, position, org_name FROM attendees WHERE report_id IN ($in)", $ids) as $a) {
            $out[$a['report_id']][] = ['id' => (string) $a['id'], 'org' => (int) $a['organization'], 'name' => self::text($a['full_name']),
                'org_name' => self::text($a['org_name']), 'position' => self::text($a['position'])];
        }
        return $out;
    }

    /**
     * آخرین مرحله‌ای که هر واحد برای پرونده ثبت کرده: راهنمای ستون وضعیت.
     *
     * @return array<string, array{text:string, suggest:?string}>
     */
    public static function statusHints(array $ids): array
    {
        $ids = array_values(array_unique(array_filter($ids)));
        if ($ids === []) {
            return [];
        }
        $in = implode(',', array_fill(0, count($ids), '?'));
        $out = [];
        foreach (Db::all(
            "SELECT report_id, unit, stage, stage_at FROM dispatches WHERE stage IS NOT NULL AND report_id IN ($in) ORDER BY stage_at",
            $ids
        ) as $d) {
            $name = UnitStages::name((int) $d['unit'], (int) $d['stage']);
            if ($name === null) {
                continue;
            }
            $prev = $out[$d['report_id']] ?? ['parts' => [], 'suggest' => null];
            $prev['parts'][(int) $d['unit']] = (UnitStages::NAMES[(int) $d['unit']] ?? '') . ': ' . $name;
            $prev['suggest'] = self::suggest($name) ?? $prev['suggest'];
            $out[$d['report_id']] = $prev;
        }
        return array_map(static fn (array $h) => ['text' => implode(' · ', $h['parts']), 'suggest' => $h['suggest']], $out);
    }

    /** نام مرحله واحد به یکی از وضعیت‌های ثابت فرم، اگر روشن باشد. */
    public static function suggest(string $stage): ?string
    {
        foreach ([
            'قطع' => 'قطع', 'جمع‌آوری' => 'جمع‌آوری', 'قضایی' => 'دادگاه', 'شکوائیه' => 'دادگاه', 'رأی' => 'دادگاه',
            'حکم' => 'دادگاه', 'ابلاغ' => 'ابلاغ ۲ هفته', 'وصول' => 'پرداخت', 'قسط' => 'قسط‌بندی', 'وصل' => 'وصل',
            'لاگ' => 'لاگ', 'مدارک' => 'تکمیل مدارک',
        ] as $word => $status) {
            if (mb_strpos($stage, $word) !== false) {
                return $status;
            }
        }
        return null;
    }

    private static function text(mixed $value): ?string
    {
        $text = trim((string) ($value ?? ''));
        return $text === '' ? null : $text;
    }

    private static function millis(mixed $value): ?int
    {
        return $value === null || (int) $value <= 0 ? null : (int) $value;
    }
}
