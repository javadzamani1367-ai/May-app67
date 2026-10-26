<?php
declare(strict_types=1);

/**
 * ویرایش آمار جامع: نوشتن خانه‌ها، انتخاب میان سامانه و فایل برای خانه ناسازگار،
 * افزودن و حذف سطر. هر تغییر یک سطر در center_changes با مقدار پیش و پس، روش
 * (edit، choose، import، add، delete، lock) و کاربر.
 *
 * کاربر فقط ستون‌هایی را می‌نویسد که دسترسی‌اش اجازه می‌دهد؛ واحد فقط ستون‌های تیک‌خورده
 * خودش. ستون محاسبه‌ای (پاداش‌ها) را فقط مدیر می‌تواند با مقدار دیگری جایگزین کند.
 */
final class CenterEdit
{
    /**
     * @param array<string, mixed> $values کلید ستون → مقدار تازه (خالی یعنی پاک)
     * @param array<string, string> $choices کلید ستون → system، file یا own
     * @return string|null خطا، یا تهی اگر ذخیره شد
     */
    public static function save(array $access, array $user, string $id, array $values, array $choices): ?string
    {
        $built = CenterRows::one($access, $id);
        if ($built === null) {
            return 'این سطر پیدا نشد.';
        }
        if ($built['locked']) {
            return 'سال ' . Jalali::digits((string) $built['year']) . ' قفل است و ویرایش نمی‌شود.';
        }
        $row = Db::one('SELECT values_json, choice_json FROM center_rows WHERE id = ?', [$id]);
        $own = CenterValues::decode($row['values_json']);
        $chosen = CenterValues::decode($row['choice_json']);
        $allowed = array_flip(self::writable($access));
        $log = [];
        foreach ($values as $key => $raw) {
            $column = CenterColumns::get((string) $key);
            if ($column === null || !isset($allowed[$key])) {
                continue;
            }
            $value = CenterValues::normalize($column['type'], is_string($raw) ? trim($raw) : $raw);
            if ($column['type'] === 'sheba' && $value !== null && !CenterCheck::shebaValid((string) $value)) {
                return 'شماره شبا درست نیست؛ IR و ۲۴ رقم، با رقم کنترل درست.';
            }
            $before = $built['values'][$key] ?? null;
            if (($chosen[$key] ?? null) === 'own' && CenterValues::same($column['type'], $own[$key] ?? null, $value)) {
                continue;
            }
            $own[$key] = $value;
            $chosen[$key] = 'own';
            $log[] = [$key, $before, $value, 'edit'];
        }
        foreach ($choices as $key => $choice) {
            if (!isset($allowed[$key]) || !in_array($choice, ['system', 'file'], true) || isset($values[$key])) {
                continue;
            }
            $after = $choice === 'file' ? ($built['file'][$key] ?? null) : ($built['system'][$key] ?? null);
            unset($own[$key]);
            $chosen[$key] = $choice;
            $log[] = [$key, $built['values'][$key] ?? null, $after, 'choose:' . $choice];
        }
        if ($log === []) {
            return null;
        }
        Db::run('UPDATE center_rows SET values_json = ?, choice_json = ?, updated_at = ? WHERE id = ?',
            [self::ownJson($own), CenterValues::encode($chosen), Db::now(), $id]);
        foreach ($log as [$key, $before, $after, $how]) {
            self::log($id, $key, $before, $after, $how, $user);
        }
        return null;
    }

    /** سطر دستی؛ با copy_of، سطر دوم همان پرونده. @return string شناسه، یا خطا با پیشوند «!» */
    public static function add(array $access, array $user, ?string $date, ?string $area, ?string $copyOf): string
    {
        $reportId = null;
        if ($copyOf !== null && $copyOf !== '') {
            $source = CenterRows::one($access, $copyOf);
            if ($source === null) {
                return '!سطر مبدأ پیدا نشد.';
            }
            [$reportId, $millis, $area] = [$source['report_id'], $source['row_date'], $source['area_code']];
        } else {
            $millis = CenterValues::date($date ?? '');
            if ($millis === null) {
                return '!تاریخ گزارش را به شکل ۱۴۰۴/۰۱/۰۱ بنویسید.';
            }
        }
        $area = Areas::code($area);
        if ($access['areas'] !== null && !in_array((string) $area, $access['areas'], true)) {
            return '!ناحیه را از ناحیه‌های خودتان انتخاب کنید.';
        }
        $year = FieldCodes::jalaliYear((int) $millis);
        if (CenterLocks::locked($year)) {
            return '!سال ' . Jalali::digits((string) $year) . ' قفل است.';
        }
        $id = Db::uuid();
        $now = Db::now();
        Db::run(
            'INSERT INTO center_rows (id, report_id, year, area_code, row_date, source, created_by, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
            [$id, $reportId, $year, $area, $millis, CenterSchema::SOURCE_MANUAL, $user['id'], $now, $now]
        );
        self::log($id, '*', null, null, 'add', $user);
        return $id;
    }

    public static function delete(array $access, array $user, string $id): ?string
    {
        $built = CenterRows::one($access, $id);
        if ($built === null) {
            return 'این سطر پیدا نشد.';
        }
        if ($built['locked']) {
            return 'سال ' . Jalali::digits((string) $built['year']) . ' قفل است.';
        }
        Db::run('UPDATE center_rows SET deleted_at = ?, updated_at = ? WHERE id = ?', [Db::now(), Db::now(), $id]);
        self::log($id, '*', null, null, 'delete', $user);
        return null;
    }

    /** ستون‌هایی که این دسترسی می‌نویسد؛ مدیر پاداش محاسبه‌شده را هم. @return list<string> */
    public static function writable(array $access): array
    {
        return $access['manage'] ? array_keys(CenterColumns::all()) : $access['fields'];
    }

    public static function log(string $rowId, string $field, mixed $before, mixed $after, string $how, ?array $user): void
    {
        Db::run(
            'INSERT INTO center_changes (row_id, field, old_value, new_value, how, user_id, at) VALUES (?, ?, ?, ?, ?, ?, ?)',
            [$rowId, $field, self::text($before), self::text($after), $how, $user['id'] ?? null, Db::now()]
        );
    }

    /** تاریخچه یک سطر، تازه‌ترین بالا. @return list<array<string, mixed>> */
    public static function changes(string $rowId): array
    {
        return array_map(static fn (array $c) => [
            'field' => $c['field'], 'label' => $c['field'] === '*' ? '' : CenterColumns::label($c['field']),
            'old' => $c['old_value'], 'new' => $c['new_value'], 'how' => $c['how'], 'at' => (int) $c['at'],
            'who' => $c['full_name'],
        ], Db::all(
            'SELECT c.*, u.full_name FROM center_changes c LEFT JOIN users u ON u.id = c.user_id
             WHERE c.row_id = ? ORDER BY c.at DESC, c.id DESC LIMIT 200',
            [$rowId]
        ));
    }

    /** مقدار نوشته‌شده کاربر؛ تهی هم می‌ماند چون «پاک کردم» با «ننوشتم» فرق دارد. */
    private static function ownJson(array $own): ?string
    {
        return $own === [] ? null : json_encode($own, JSON_UNESCAPED_UNICODE);
    }

    private static function text(mixed $value): ?string
    {
        if ($value === null) {
            return null;
        }
        return mb_substr(is_scalar($value) ? (string) $value : (string) json_encode($value, JSON_UNESCAPED_UNICODE), 0, 2000);
    }
}
