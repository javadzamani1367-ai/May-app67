<?php
declare(strict_types=1);

/**
 * بانک دستگاه‌های ماینر: هر دستگاه همه پرونده‌ها، با تاریخ کشف، ناحیه، مشترک و
 * دارنده. سریالی که در دو پرونده آمده یعنی دستگاه جابه‌جا شده — فقط مدیر هشدارش
 * را می‌گیرد و می‌بیند دستگاه پیش‌تر کجا و پیش چه کسی بوده.
 *
 * سریال‌ها یکسان‌سازی می‌شوند (حروف بزرگ، ارقام لاتین، بی فاصله و خط تیره)، چون
 * یک سریال از بارکد و همان سریال تایپ‌شده با دست نباید دو دستگاه حساب شوند.
 */
final class DeviceRegistry
{
    /** کوتاه‌تر از این، سریال نیست (۰، ۱۲۳…) و تکرارش معنایی ندارد. */
    public const MIN_KEY = 5;

    public static function key(?string $serial): ?string
    {
        $text = strtoupper(Jalali::latin((string) $serial));
        $text = (string) preg_replace('/[^A-Z0-9]/', '', $text);
        return strlen($text) >= self::MIN_KEY ? mb_substr($text, 0, 191) : null;
    }

    /** پس از رسیدن پرونده: کلید سریال‌ها و هشدار تکراری برای مدیر. شمار هشدار تازه. */
    public static function onPushed(string $reportId): int
    {
        foreach (Db::all('SELECT id, serial_number FROM devices WHERE report_id = ?', [$reportId]) as $row) {
            Db::run('UPDATE devices SET serial_key = ? WHERE id = ?', [self::key($row['serial_number']), $row['id']]);
        }
        $made = 0;
        $keys = Db::all('SELECT DISTINCT serial_key FROM devices WHERE report_id = ? AND serial_key IS NOT NULL', [$reportId]);
        foreach ($keys as $k) {
            $key = (string) $k['serial_key'];
            foreach (Db::all('SELECT DISTINCT report_id FROM devices WHERE serial_key = ? AND report_id <> ?', [$key, $reportId]) as $other) {
                $otherId = (string) $other['report_id'];
                if (Db::one('SELECT id FROM device_alerts WHERE serial_key = ? AND ((report_id = ? AND other_report_id = ?)
                        OR (report_id = ? AND other_report_id = ?))', [$key, $reportId, $otherId, $otherId, $reportId]) !== null) {
                    continue;
                }
                Db::run('INSERT INTO device_alerts (id, serial_key, report_id, other_report_id, created_at) VALUES (?, ?, ?, ?, ?)',
                    [Db::uuid(), $key, $reportId, $otherId, Db::now()]);
                $made++;
                $before = self::place($otherId);
                $now = self::place($reportId);
                Notifications::toRole(Auth::ROLE_MANAGER, 'device_duplicate', 'سریال تکراری دستگاه: ' . $key,
                    'این دستگاه پیش‌تر در پرونده ' . $before . ' ثبت شده و اکنون در پرونده ' . $now . ' آمده است.', $reportId);
            }
        }
        return $made;
    }

    /** «M-401-… (۱۴۰۵/۰۷/۱۲، ایلام — ناحیه ۴۰۱، مشترک: …، دارنده: …)» */
    public static function place(string $reportId): string
    {
        $r = Db::one('SELECT tracking_code, temp_code, visit_date, report_date, area_code, county, subscriber_name, owner_name
                      FROM reports WHERE id = ?', [$reportId]) ?? [];
        $parts = array_filter([
            Jalali::format((int) ($r['visit_date'] ?? $r['report_date'] ?? 0), false),
            CaseLabels::countyWithArea($r['county'] ?? null, $r['area_code'] ?? null),
            ($r['subscriber_name'] ?? '') === '' ? '' : 'مشترک: ' . $r['subscriber_name'],
            ($r['owner_name'] ?? '') === '' ? '' : 'دارنده: ' . $r['owner_name'],
        ]);
        return CaseLabels::code($r) . ' (' . implode('، ', $parts) . ')';
    }

    public const COLUMNS = 'd.id, d.model, d.serial_number, d.serial_key, d.power_watt, d.entry_method, d.report_id,
        r.tracking_code, r.temp_code, r.visit_date, r.report_date, r.area_code, r.county, r.district, r.address,
        r.subscription_number, r.subscriber_name, r.owner_name, r.owner_national_id, r.owner_phone, r.expert_code,
        r.status, r.approval_state, r.latitude, r.longitude,
        (SELECT COUNT(DISTINCT d2.report_id) FROM devices d2 WHERE d2.serial_key = d.serial_key) AS seen_in';

    /**
     * فیلترهای جستجو: متن (سریال، مدل، مشترک، دارنده، اشتراک، کد)، ناحیه، بازه کشف، فقط تکراری.
     *
     * @param array<string, mixed> $get
     * @return array{where:string, params:list<mixed>, values:array<string,string>}
     */
    public static function filters(array $get): array
    {
        $where = ['1=1'];
        $params = [];
        $values = [];
        $text = static fn (string $k): string => trim((string) ($get[$k] ?? ''));
        if ($text('q') !== '') {
            $q = mb_substr($text('q'), 0, 64);
            $like = '%' . str_replace(['\\', '%', '_'], ['\\\\', '\\%', '\\_'], $q) . '%';
            $key = self::key($q);
            $where[] = '(d.serial_number LIKE ? OR d.model LIKE ? OR r.subscriber_name LIKE ? OR r.owner_name LIKE ?
                        OR r.subscription_number LIKE ? OR r.tracking_code LIKE ?' . ($key === null ? '' : ' OR d.serial_key LIKE ?') . ')';
            array_push($params, $like, $like, $like, $like, $like, $like);
            if ($key !== null) {
                $params[] = '%' . $key . '%';
            }
            $values['q'] = $q;
        }
        $area = Areas::code($text('area'));
        if ($area !== null) {
            $where[] = 'r.area_code = ?';
            $params[] = $area;
            $values['area'] = $area;
        }
        foreach (['from' => '>=', 'to' => '<'] as $k => $op) {
            $day = Jalali::parse($text($k));
            if ($day !== null) {
                $where[] = "COALESCE(r.visit_date, r.report_date) $op ?";
                $params[] = Jalali::startOfDay(...$day) + ($k === 'to' ? 86_400_000 : 0);
                $values[$k] = $text($k);
            }
        }
        if ($text('dup') === '1') {
            $where[] = 'd.serial_key IS NOT NULL AND (SELECT COUNT(DISTINCT d3.report_id) FROM devices d3 WHERE d3.serial_key = d.serial_key) > 1';
            $values['dup'] = '1';
        }
        return ['where' => implode(' AND ', $where), 'params' => $params, 'values' => $values];
    }

    public static function search(array $get, int $limit = 300, int $offset = 0): array
    {
        $f = self::filters($get);
        return Db::all('SELECT ' . self::COLUMNS . ' FROM devices d JOIN reports r ON r.id = d.report_id WHERE ' . $f['where']
            . ' ORDER BY COALESCE(r.visit_date, r.report_date) DESC, d.serial_number LIMIT ' . max(1, $limit) . ' OFFSET ' . max(0, $offset),
            $f['params']);
    }

    /** همه جاهایی که یک سریال دیده شده، قدیمی‌ترین اول — سابقه دستگاه. */
    public static function history(string $key): array
    {
        return Db::all('SELECT ' . self::COLUMNS . ' FROM devices d JOIN reports r ON r.id = d.report_id WHERE d.serial_key = ?
                        ORDER BY COALESCE(r.visit_date, r.report_date)', [$key]);
    }

    /** هشدارهای تکراری، تازه‌ترین اول. */
    public static function alerts(int $limit = 100): array
    {
        $rows = Db::all('SELECT * FROM device_alerts ORDER BY created_at DESC LIMIT ' . max(1, $limit));
        foreach ($rows as &$row) {
            $row['now'] = self::place((string) $row['report_id']);
            $row['before'] = self::place((string) $row['other_report_id']);
        }
        return $rows;
    }

    public static function unseenAlerts(): int
    {
        return (int) (Db::one('SELECT COUNT(*) AS n FROM device_alerts WHERE seen_at IS NULL')['n'] ?? 0);
    }

    public static function markSeen(): void
    {
        Db::run('UPDATE device_alerts SET seen_at = ? WHERE seen_at IS NULL', [Db::now()]);
    }

    /** یک سطر برای API و اکسل. */
    public static function present(array $row): array
    {
        return [
            'id' => $row['id'], 'report_id' => $row['report_id'], 'code' => CaseLabels::code($row),
            'model' => $row['model'], 'serial' => $row['serial_number'], 'serial_key' => $row['serial_key'],
            'power_watt' => $row['power_watt'] === null ? null : (float) $row['power_watt'],
            'found_at' => $row['visit_date'] !== null ? (int) $row['visit_date'] : ($row['report_date'] === null ? null : (int) $row['report_date']),
            'area' => Areas::label($row['area_code']), 'county' => $row['county'], 'district' => $row['district'],
            'address' => $row['address'], 'subscription' => $row['subscription_number'],
            'subscriber' => $row['subscriber_name'], 'owner' => $row['owner_name'], 'owner_national_id' => $row['owner_national_id'],
            'expert_code' => $row['expert_code'], 'seen_in' => (int) $row['seen_in'],
        ];
    }
}
