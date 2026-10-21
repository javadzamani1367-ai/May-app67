<?php
declare(strict_types=1);

/**
 * تیک‌های مدیر روی جدول «ناحیه × نقش» یک شخص: هر تیک تازه یک کاربری با کد پیشنهادی
 * و رمز یک‌بارمصرف، هر تیک برداشته یک کاربری غیرفعال. کاربری حذف نمی‌شود، چون
 * پرونده‌ها و ارسال‌ها به کد آن بسته‌اند؛ با تیک دوباره، همان کاربری باز می‌شود.
 */
final class PersonAccounts
{
    /**
     * @param list<string> $ticks «401:expert»، «410:legal»، یا «-:illegal_hq» برای کاربری استانی
     * @param array<string, int> $permissions مجوز اپ میدانی هر تیک (بیت ۱ بازرسی، ۲ گزارش)
     * @return array{made: list<array<string, string>>, closed: list<string>, error: ?string}
     */
    public static function apply(string $personId, array $ticks, array $permissions, array $manager): array
    {
        $person = Persons::find($personId);
        if ($person === null) {
            return ['made' => [], 'closed' => [], 'error' => 'شخص پیدا نشد.'];
        }
        $wanted = [];
        foreach ($ticks as $tick) {
            [$area, $kind] = array_pad(explode(':', (string) $tick, 2), 2, '');
            $area = $area === '-' ? null : Areas::code($area);
            $valid = $area === null ? ($kind === 'illegal_hq') : isset(Persons::KINDS[$kind]);
            if ($valid) {
                $wanted[($area ?? '-') . ':' . $kind] = [$area, $kind];
            }
        }
        $made = [];
        $closed = [];
        $have = [];
        foreach (Persons::accounts($personId) as $account) {
            $key = ($account['area'] ?? '-') . ':' . $account['kind'];
            $have[$key] = $account;
            if (!isset($wanted[$key]) && (int) $account['active'] === 1 && $account['kind'] !== 'manager') {
                Users::save(['user_code' => $account['user_code'], 'full_name' => $person['full_name'], 'active' => 0,
                    'areas' => $account['areas']], (string) $manager['id']);
                $closed[] = (string) $account['user_code'];
            }
        }
        foreach ($wanted as $key => [$area, $kind]) {
            $perm = isset($permissions[$key]) ? max(0, min(3, (int) $permissions[$key])) : 3;
            $account = $have[$key] ?? null;
            if ($account !== null) {
                $changes = ['user_code' => $account['user_code'], 'full_name' => $person['full_name'], 'active' => 1,
                    'areas' => $account['areas'], 'permissions' => $perm];
                Users::save($changes, (string) $manager['id']);
                continue;
            }
            $result = self::add($person, $area, $kind, $perm, null, null, $manager);
            if (is_string($result)) {
                return ['made' => $made, 'closed' => $closed, 'error' => $result];
            }
            $made[] = $result;
        }
        return ['made' => $made, 'closed' => $closed, 'error' => null];
    }

    /**
     * یک کاربری تازه برای شخص. کد و رمز را برمی‌گرداند (رمز فقط همین یک بار دیده می‌شود)،
     * یا خطای فارسی.
     *
     * @return array<string, string>|string
     */
    public static function add(array $person, ?string $area, string $kind, int $permissions, ?string $code,
                               ?string $password, array $manager): array|string
    {
        $spec = $area === null ? (Persons::PROVINCE_KINDS[$kind] ?? null) : (Persons::KINDS[$kind] ?? null);
        if ($spec === null || $kind === 'manager') {
            return 'نوع کاربری نامعتبر است.';
        }
        $code = Auth::latin(trim((string) $code));
        $code = $code === '' ? Persons::suggestCode($area) : $code;
        if (Db::one('SELECT id FROM users WHERE user_code = ?', [$code]) !== null) {
            return 'کد کاربری ' . $code . ' از قبل هست.';
        }
        $password = trim((string) $password) === '' ? UserBatch::password() : trim((string) $password);
        $data = ['user_code' => $code, 'full_name' => $person['full_name'], 'role' => $spec['role'],
            'unit' => $spec['unit'] ?? null, 'crew_role' => $spec['crew'] ?? null, 'areas' => $area ?? '',
            'permissions' => $permissions, 'phone' => $person['phone'] ?? '', 'password' => $password,
            'person_id' => $person['id'], 'active' => 1];
        $problem = Users::problem($data);
        if ($problem !== null) {
            return $problem;
        }
        Users::save($data, (string) $manager['id']);
        return ['user_code' => $code, 'password' => $password, 'kind' => Persons::kindName($kind),
            'area' => $area === null ? 'استانی' : Areas::label($area), 'full_name' => (string) $person['full_name']];
    }

    /** رمز تازه یک‌بارمصرف برای یک کاربری؛ نشست‌های قبلی بسته می‌شوند. */
    public static function resetPassword(string $userId, array $manager): ?string
    {
        $user = Db::one('SELECT user_code, full_name, areas FROM users WHERE id = ?', [$userId]);
        if ($user === null) {
            return null;
        }
        $password = UserBatch::password();
        Users::save(['user_code' => $user['user_code'], 'full_name' => $user['full_name'], 'password' => $password,
            'areas' => $user['areas']], (string) $manager['id']);
        return $password;
    }

    /** کاربری موجودی (مثلاً ساخته‌شده پیش از این نسخه) به این شخص وصل می‌شود. */
    public static function attach(string $userCode, string $personId): ?string
    {
        $user = Auth::userByCode($userCode) ?? Db::one('SELECT * FROM users WHERE user_code = ?', [Auth::latin(trim($userCode))]);
        $person = Persons::find($personId);
        if ($user === null || $person === null) {
            return 'کاربری یا شخص پیدا نشد.';
        }
        if ((int) $user['role'] === Auth::ROLE_MANAGER) {
            return 'کاربری مدیر جابه‌جا نمی‌شود.';
        }
        $old = (string) ($user['person_id'] ?? '');
        Db::run('UPDATE users SET person_id = ?, full_name = ?, updated_at = ? WHERE id = ?',
            [$personId, $person['full_name'], Db::now(), $user['id']]);
        // شخص قبلی که دیگر هیچ کاربری ندارد و کد ملی هم ندارد، فقط یک نام تکراری بود.
        if ($old !== '' && $old !== $personId
            && Db::one('SELECT id FROM users WHERE person_id = ?', [$old]) === null
            && (Persons::find($old)['national_id'] ?? null) === null) {
            Db::run('DELETE FROM person_devices WHERE person_id = ?', [$old]);
            Db::run('DELETE FROM persons WHERE id = ?', [$old]);
        }
        CrewAccounts::sync((string) $user['id']);
        return null;
    }
}
