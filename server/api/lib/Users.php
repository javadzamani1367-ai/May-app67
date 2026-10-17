<?php
declare(strict_types=1);

/**
 * ساخت و ویرایش کاربر — یک قاعده برای هر دو راه: API اپ مدیر و پنل وب مدیر.
 *
 * قاعده‌ها اینجا هستند و نه در هر صفحه، چون دو راهی که هرکدام قاعده خودش را
 * دارد دیر یا زود دو قاعده متفاوت می‌شوند.
 */
final class Users
{
    public const ROLES = [Auth::ROLE_EXPERT, Auth::ROLE_MANAGER, Auth::ROLE_UNIT, Auth::ROLE_FIELD,
        Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL];
    public const ROLE_NAMES = [Auth::ROLE_EXPERT => 'کارشناس', Auth::ROLE_MANAGER => 'مدیر',
        Auth::ROLE_UNIT => 'واحد مقصد', Auth::ROLE_FIELD => 'کاربر میدانی', Auth::ROLE_EXECUTOR => 'مجری رمزارز',
        Auth::ROLE_ILLEGAL => 'مجری برق غیرمجاز'];
    public const MIN_PASSWORD = 8;

    /**
     * ایراد اطلاعات یک کاربر، به فارسی، یا تهی. جدا از save تا صفحه وب بتواند
     * پیام را کنار فرم نشان دهد، و API همان را به صورت خطای JSON بفرستد.
     *
     * @param array<string, mixed> $data
     */
    public static function problem(array $data): ?string
    {
        $userCode = trim((string) ($data['user_code'] ?? ''));
        if ($userCode === '' || trim((string) ($data['full_name'] ?? '')) === '') {
            return 'نام و کد کاربری الزامی است.';
        }
        $role = (int) ($data['role'] ?? Auth::ROLE_EXPERT);
        if (!in_array($role, self::ROLES, true)) {
            return 'نقش کاربر معتبر نیست.';
        }
        $areas = Areas::parse($data['areas'] ?? '');
        if ($role === Auth::ROLE_EXECUTOR && $areas === []) {
            return 'برای مجری رمزارز دست‌کم یک ناحیه انتخاب کنید.';
        }
        if (in_array($role, [Auth::ROLE_EXPERT, Auth::ROLE_UNIT, Auth::ROLE_FIELD], true) && count($areas) > 1) {
            return 'کارشناس، واحد و کاربر میدانی فقط یک ناحیه دارند.';
        }
        $password = (string) ($data['password'] ?? '');
        if ($password !== '' && strlen($password) < self::MIN_PASSWORD) {
            return 'رمز عبور باید دست‌کم ۸ نویسه باشد.';
        }
        if ($password === '' && Db::one('SELECT id FROM users WHERE user_code = ?', [$userCode]) === null) {
            return 'برای کاربر تازه رمز عبور لازم است.';
        }
        return null;
    }

    /**
     * ثبت یا ویرایش. رمز فقط وقتی نوشته می‌شود که رمز تازه‌ای آمده باشد — وگرنه
     * یک ویرایش کوچک کاربر را از حسابش بیرون می‌انداخت. عوض شدن رمز یا غیرفعال
     * شدن، نشست‌های باز را می‌بندد: کسی که رمزش را از دست داده نباید با نشست
     * قبلی همچنان کار کند.
     *
     * @param array<string, mixed> $data
     * @return string شناسه کاربر
     */
    public static function save(array $data, string $managerId): string
    {
        $problem = self::problem($data);
        if ($problem !== null) {
            Response::fail(400, 'invalid_user', $problem);
        }
        $userCode = trim((string) $data['user_code']);
        $fullName = trim((string) $data['full_name']);
        $role = (int) ($data['role'] ?? Auth::ROLE_EXPERT);
        $existing = Db::one('SELECT * FROM users WHERE user_code = ?', [$userCode]);
        $password = (string) ($data['password'] ?? '');

        $id = $existing['id'] ?? Db::uuid();
        $now = Db::now();
        $active = isset($data['active']) ? ((int) $data['active'] === 1 ? 1 : 0) : 1;
        $device = Auth::normaliseDevice((string) ($data['device_code'] ?? ''));
        // ویرایشی که مجوز نفرستد (اپ مدیر قدیمی) مجوزهای قبلی را نگه می‌دارد.
        $permissions = isset($data['permissions']) && is_numeric($data['permissions'])
            ? (int) $data['permissions'] : (int) ($existing['permissions'] ?? 0);
        $unit = isset($data['unit']) && is_numeric($data['unit']) ? (int) $data['unit'] : null;
        $areas = $role === Auth::ROLE_MANAGER ? null : Areas::store(Areas::parse($data['areas'] ?? ''));
        $county = trim((string) ($data['county'] ?? ''));
        if ($county === '' && $areas !== null) {
            $county = (string) (Areas::NAMES[explode(',', $areas)[0]] ?? '');
        }

        Db::run(
            'INSERT INTO users (id, user_code, full_name, role, unit, county, areas, phone, device_code,
                                password_hash, active, created_at, updated_at, note, permissions)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE
                full_name = VALUES(full_name), role = VALUES(role), unit = VALUES(unit),
                county = VALUES(county), areas = VALUES(areas), phone = VALUES(phone), device_code = VALUES(device_code),
                password_hash = VALUES(password_hash), active = VALUES(active),
                updated_at = VALUES(updated_at), note = VALUES(note), permissions = VALUES(permissions)',
            [
                $id, $userCode, $fullName, $role,
                $role === Auth::ROLE_UNIT ? $unit : null,
                $county,
                $areas,
                trim((string) ($data['phone'] ?? '')),
                $device,
                $password !== '' ? password_hash($password, PASSWORD_DEFAULT) : ($existing['password_hash'] ?? null),
                $active,
                $existing['created_at'] ?? $now,
                $now,
                trim((string) ($data['note'] ?? '')),
                $role === Auth::ROLE_FIELD ? ($permissions & (Auth::PERM_INSPECT | Auth::PERM_REPORT)) : 0,
            ]
        );

        if ($existing !== null && ($password !== '' || $active === 0)) {
            Db::run('DELETE FROM tokens WHERE user_id = ?', [$id]);
        }

        // ثبت دستگاه، درخواست در صف را می‌بندد: وگرنه مدیر برای همیشه یک
        // درخواست باز می‌بیند که قبلاً به آن رسیدگی کرده.
        if ($device !== '') {
            Db::run(
                'UPDATE device_requests SET status = 1, decided_at = ?, decided_by = ?
                 WHERE device_code = ? AND status = 0',
                [$now, $managerId, $device]
            );
        }
        return $id;
    }
}
