<?php
declare(strict_types=1);

/**
 * تنظیمات آمار جامع، در همان جدول کلید-مقدار field_settings:
 *  - center_executor: مجری رمزارز به آمار ناحیه‌های خودش دسترسی دارد یا نه
 *  - center_units: هر واحد کدام ستون‌ها را می‌بیند و پر می‌کند (پیش‌فرض هیچ)
 *  - center_excel / center_word: ستون‌های تیک‌خورده خروجی‌ها
 *
 * و دسترسی هر کاربر از همین‌ها ساخته می‌شود (access): پیش‌فرض فقط مدیر.
 */
final class CenterSettings
{
    /** ستون‌های پیش‌فرض Word؛ Word جای همه ستون‌ها را ندارد. */
    public const WORD_DEFAULT = ['row', 'county', 'report_date', 'finder_name', 'miners', 'finder_reward', 'subscription',
        'subscriber', 'capacity_kw', 'commission', 'energy_amount', 'damage_amount', 'collected_amount', 'status'];
    /** ستون‌هایی که هر کاربر دارای دسترسی برای شناختن سطر همیشه می‌بیند. */
    public const IDENTITY = ['row', 'county', 'report_date', 'tracking_code', 'subscription', 'subscriber', 'file_number'];

    public static function executorAllowed(): bool
    {
        return (int) self::raw('center_executor', '0') === 1;
    }

    /** @return array<int, list<string>> واحد → ستون‌های مجاز */
    public static function unitFields(): array
    {
        $data = CenterValues::decode(self::raw('center_units', ''));
        $editable = CenterColumns::editableKeys();
        $out = [];
        foreach (array_keys(UnitStages::NAMES) as $unit) {
            $out[$unit] = array_values(array_intersect($editable, (array) ($data[(string) $unit] ?? [])));
        }
        return $out;
    }

    /** @return list<string> */
    public static function columns(string $format): array
    {
        $keys = CenterValues::decode(self::raw($format === 'docx' ? 'center_word' : 'center_excel', ''));
        return CenterColumns::pick($keys, $format === 'docx' ? self::WORD_DEFAULT : CenterColumns::templateKeys());
    }

    public static function save(bool $executor, array $units, array $excel, array $word): void
    {
        $editable = CenterColumns::editableKeys();
        $cleanUnits = [];
        foreach (array_keys(UnitStages::NAMES) as $unit) {
            $cleanUnits[(string) $unit] = array_values(array_intersect($editable, array_map('strval', (array) ($units[$unit] ?? []))));
        }
        self::put('center_executor', $executor ? '1' : '0');
        self::put('center_units', (string) json_encode($cleanUnits));
        self::put('center_excel', (string) json_encode(CenterColumns::pick($excel)));
        self::put('center_word', (string) json_encode(CenterColumns::pick($word, self::WORD_DEFAULT)));
    }

    /**
     * دسترسی یک کاربر: null یعنی هیچ؛ وگرنه areas (null همه استان)، fields که می‌نویسد،
     * see ستون‌هایی که می‌بیند، و manage برای دستورالعمل، تنظیمات، قفل سال و ورود فایل.
     *
     * @return array{areas:?list<string>, fields:list<string>, see:list<string>, manage:bool, role:int}|null
     */
    public static function access(array $user): ?array
    {
        $role = (int) $user['role'];
        $areas = Areas::parse($user['areas'] ?? '');
        if ($role === Auth::ROLE_MANAGER) {
            $all = array_keys(CenterColumns::all());
            return ['areas' => null, 'fields' => CenterColumns::editableKeys(), 'see' => $all, 'manage' => true, 'role' => $role];
        }
        if ($role === Auth::ROLE_EXECUTOR && self::executorAllowed() && $areas !== []) {
            return ['areas' => $areas, 'fields' => CenterColumns::editableKeys(), 'see' => array_keys(CenterColumns::all()),
                'manage' => false, 'role' => $role];
        }
        if ($role === Auth::ROLE_UNIT && $user['unit'] !== null) {
            $fields = self::unitFields()[(int) $user['unit']] ?? [];
            if ($fields === []) {
                return null;
            }
            return ['areas' => $areas === [] ? null : $areas, 'fields' => $fields,
                'see' => CenterColumns::pick(array_merge(self::IDENTITY, $fields)), 'manage' => false, 'role' => $role];
        }
        return null;
    }

    /** دسترسی یا پایان درخواست با ۴۰۳. */
    public static function require(array $user, bool $manage = false): array
    {
        $access = self::access($user);
        if ($access === null || ($manage && !$access['manage'])) {
            Response::fail(403, 'forbidden', 'به آمار جامع مراکز دسترسی ندارید.');
        }
        return $access;
    }

    private static function raw(string $key, string $fallback): string
    {
        $row = Db::one('SELECT v FROM field_settings WHERE k = ?', [$key]);
        return $row === null ? $fallback : (string) $row['v'];
    }

    private static function put(string $key, string $value): void
    {
        Db::run('INSERT INTO field_settings (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)', [$key, $value]);
    }
}
