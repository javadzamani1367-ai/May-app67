<?php
declare(strict_types=1);

/**
 * سطرهای آمار جامع: ساختن سطر برای هر پرونده تأییدشده رمزارز، خواندن سطرهای یک بازه،
 * و ساختن مقدار نهایی هر خانه (CenterValues) با پاداش‌های حساب‌شده (CenterRewards).
 *
 * پرونده تازه تأییدشده اگر با سطری از فایل سال‌های پیش جور شود (اشتراک، شناسه قبض،
 * شماره پرونده یا کد سامانه پیامکی)، به همان سطر وصل می‌شود و سطر دوم نمی‌سازد.
 * سطری که مدیر حذف کرده دوباره ساخته نمی‌شود.
 */
final class CenterRows
{
    /** پرونده‌های تأییدشده‌ای که هنوز سطر ندارند. */
    public static function sync(): int
    {
        $new = Db::all(
            'SELECT r.id, r.area_code, r.report_date, r.created_at, r.subscription_number, r.bill_number, r.file_number,
                    r.tracking_code, r.report_type
             FROM reports r WHERE ' . CenterSystem::ELIGIBLE . '
               AND NOT EXISTS (SELECT 1 FROM center_rows c WHERE c.report_id = r.id)'
        );
        if ($new === []) {
            return 0;
        }
        $orphans = [];
        foreach (Db::all('SELECT id, import_json FROM center_rows WHERE report_id IS NULL AND deleted_at IS NULL') as $row) {
            foreach (CenterMatch::keys(CenterValues::decode($row['import_json'])) as $key) {
                $orphans[$key] ??= $row['id'];
            }
        }
        $now = Db::now();
        $taken = [];
        foreach ($new as $r) {
            $date = (int) ($r['report_date'] ?: $r['created_at']);
            $keys = CenterMatch::keys([
                'subscription' => $r['subscription_number'], 'bill_id' => $r['bill_number'], 'file_number' => $r['file_number'],
                'sms_code' => (int) $r['report_type'] === 1 ? $r['tracking_code'] : null,
            ]);
            $match = null;
            foreach ($keys as $key) {
                if (isset($orphans[$key]) && !isset($taken[$orphans[$key]])) {
                    $match = $orphans[$key];
                    break;
                }
            }
            if ($match !== null) {
                $taken[$match] = true;
                Db::run('UPDATE center_rows SET report_id = ?, area_code = COALESCE(area_code, ?), updated_at = ? WHERE id = ?',
                    [$r['id'], $r['area_code'], $now, $match]);
                continue;
            }
            Db::run(
                'INSERT INTO center_rows (id, report_id, year, area_code, row_date, source, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
                [Db::uuid(), $r['id'], FieldCodes::jalaliYear($date), $r['area_code'], $date, CenterSchema::SOURCE_SYSTEM, $now, $now]
            );
        }
        return count($new);
    }

    /**
     * سطرهای یک بازه برای یک دسترسی، به ترتیب تاریخ. @return list<array<string, mixed>>
     */
    public static function query(array $access, int $from, int $to, ?string $area = null, string $search = ''): array
    {
        self::sync();
        $where = ['c.deleted_at IS NULL', 'c.row_date BETWEEN ? AND ?'];
        $params = [min($from, $to), max($from, $to)];
        if ($access['areas'] !== null) {
            $where[] = 'c.area_code IN (' . implode(',', array_fill(0, count($access['areas']), '?')) . ')';
            array_push($params, ...$access['areas']);
        }
        if ($area !== null && $area !== '') {
            $where[] = 'c.area_code = ?';
            $params[] = $area;
        }
        $rows = Db::all('SELECT c.* FROM center_rows c WHERE ' . implode(' AND ', $where) . ' ORDER BY c.row_date, c.created_at', $params);
        $built = self::build($rows);
        if ($search !== '') {
            $needle = CenterValues::loose($search);
            $built = array_values(array_filter($built, static function (array $row) use ($needle) {
                foreach (['tracking_code', 'subscription', 'bill_id', 'subscriber', 'file_number', 'sms_code', 'finder_name', 'county'] as $k) {
                    if (($row['values'][$k] ?? null) !== null && str_contains(CenterValues::loose((string) $row['values'][$k]), $needle)) {
                        return true;
                    }
                }
                return false;
            }));
        }
        foreach ($built as $i => &$row) {
            $row['values']['row'] = $i + 1;
        }
        unset($row);
        return $built;
    }

    /** یک سطر با همه جزئیات، یا تهی اگر نیست یا بیرون از دسترسی است. */
    public static function one(array $access, string $id): ?array
    {
        $row = Db::one('SELECT * FROM center_rows WHERE id = ? AND deleted_at IS NULL', [$id]);
        if ($row === null || ($access['areas'] !== null && !in_array((string) $row['area_code'], $access['areas'], true))) {
            return null;
        }
        return self::build([$row], true)[0];
    }

    /**
     * مقدار نهایی هر خانه. با detail، مقدار سامانه و فایل هر خانه، حاضران، پاداش‌ها و
     * تاریخچه هم؛ با withSystem فقط مقدار سامانه (برای جور کردن فایل).
     *
     * @param list<array<string, mixed>> $rows
     * @return list<array<string, mixed>>
     */
    public static function build(array $rows, bool $detail = false, bool $withSystem = false): array
    {
        $reportIds = array_values(array_filter(array_column($rows, 'report_id')));
        $system = CenterSystem::values($reportIds);
        $attendees = CenterSystem::attendees($reportIds);
        $hints = CenterSystem::statusHints($reportIds);
        $locked = CenterLocks::years();
        $columns = CenterColumns::all();
        $out = [];
        foreach ($rows as $row) {
            $sys = $row['report_id'] !== null ? ($system[$row['report_id']] ?? []) : [];
            $values = CenterValues::decode($row['values_json']);
            $import = CenterValues::decode($row['import_json']);
            $choices = CenterValues::decode($row['choice_json']);
            $date = $row['row_date'] === null ? null : (int) $row['row_date'];
            $cells = [];
            foreach ($columns as $key => $c) {
                if ($c['src'] !== 'calc') {
                    $cells[$key] = CenterValues::effective($key, $c['type'], $sys[$key] ?? null, $values, $import, $choices);
                }
            }
            $plain = array_map(static fn ($cell) => $cell['value'], $cells);
            $finder = CenterRewards::finder($plain, $date);
            $police = CenterRewards::enforcement($plain, $date, $attendees[$row['report_id']] ?? []);
            $staff = CenterRewards::staff($plain, $date);
            $calc = ['finder_reward' => $finder['amount'] ?? null, 'police_reward' => $police['amount'] ?? null,
                'staff_reward' => $staff['amount'] ?? null];
            foreach ($calc as $key => $value) {
                $cells[$key] = CenterValues::effective($key, 'money', $value ?: null, $values, $import, $choices);
            }
            $built = [
                'id' => $row['id'], 'report_id' => $row['report_id'], 'year' => (int) $row['year'],
                'area_code' => $row['area_code'], 'source' => (int) $row['source'], 'row_date' => $date,
                'locked' => isset($locked[(int) $row['year']]),
                'approved' => $row['report_id'] === null || $sys !== [],
                'values' => array_map(static fn ($cell) => $cell['value'], $cells),
                'from' => array_map(static fn ($cell) => $cell['from'], $cells),
                'conflicts' => array_keys(array_filter($cells, static fn ($cell) => $cell['conflict'])),
                'hint' => $hints[$row['report_id']] ?? null,
            ];
            $built['warnings'] = CenterCheck::warnings($built['values']);
            if ($detail || $withSystem) {
                $built['system'] = array_intersect_key($sys + $calc, $columns);
            }
            if ($detail) {
                $built['file'] = $import;
                $built['own'] = $values;
                $built['choices'] = $choices;
                $built['police'] = $police;
                $built['staff'] = $staff;
                $built['finder'] = $finder;
                $built['changes'] = CenterEdit::changes($row['id']);
            }
            $out[] = $built;
        }
        CenterCheck::duplicates($out);
        return $out;
    }

    /** جمع ستون‌های جمع‌پذیر؛ ستون تیک و متنی (سایر موارد) شمار خانه‌های پر. @return array<string, float|int> */
    public static function totals(array $rows): array
    {
        $totals = [];
        foreach (CenterColumns::all() as $key => $c) {
            if (!$c['total']) {
                continue;
            }
            $sum = 0;
            foreach ($rows as $row) {
                $value = $row['values'][$key] ?? null;
                $sum += in_array($c['type'], ['tick', 'text'], true) ? ($value !== null && $value !== '' ? 1 : 0) : (float) ($value ?? 0);
            }
            $totals[$key] = $c['type'] === 'kw' ? round($sum, 2) : (int) round($sum);
        }
        return $totals;
    }
}
