<?php
declare(strict_types=1);

require_once __DIR__ . '/PersonDevices.php';
require_once __DIR__ . '/Persons.php';
require_once __DIR__ . '/CrewAccounts.php';

/**
 * ساخت و ویرایش یک کاربری — یک قاعده برای همه راه‌ها: API اپ مدیر، پنل وب و
 * صفحه شخص (Persons). هر کاربری یک نقش در یک ناحیه است و به یک شخص وصل است.
 *
 * ویرایشی که فیلدی را نفرستد، آن فیلد را دست نمی‌زند: اپ مدیر قدیمی نقش، ناحیه و
 * مجوز را نمی‌فرستاد و با هر ذخیره، کاربر میدانی یا مجری را «کارشناس» بی‌ناحیه می‌کرد.
 */
final class Users
{
    public const ROLES = [Auth::ROLE_EXPERT, Auth::ROLE_MANAGER, Auth::ROLE_UNIT, Auth::ROLE_FIELD,
        Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL, Auth::ROLE_ILLEGAL_AREA];
    public const ROLE_NAMES = [Auth::ROLE_EXPERT => 'کارشناس', Auth::ROLE_MANAGER => 'مدیر',
        Auth::ROLE_UNIT => 'واحد مقصد', Auth::ROLE_FIELD => 'کاربر میدانی', Auth::ROLE_EXECUTOR => 'مجری رمزارز',
        Auth::ROLE_ILLEGAL => 'مجری برق غیرمجاز ستاد', Auth::ROLE_ILLEGAL_AREA => 'مجری برق غیرمجاز شهرستان'];
    /** نقش‌هایی که ناحیه دارند و بی آن معنایی ندارند. */
    private const NEEDS_AREA = [Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL_AREA];
    /** نقش‌هایی که فقط یک ناحیه دارند؛ واحد بی‌ناحیه استانی است. */
    private const ONE_AREA = [Auth::ROLE_EXPERT, Auth::ROLE_UNIT, Auth::ROLE_FIELD, Auth::ROLE_ILLEGAL_AREA];
    public const MIN_PASSWORD = 8;

    /**
     * داده ورودی، کامل‌شده با آنچه کاربری موجود دارد و ورودی نفرستاده.
     *
     * @param array<string, mixed> $data
     * @return array<string, mixed>
     */
    public static function merged(array $data): array
    {
        $data['user_code'] = Auth::latin(trim((string) ($data['user_code'] ?? '')));
        $existing = $data['user_code'] === '' ? null : Db::one('SELECT * FROM users WHERE user_code = ?', [$data['user_code']]);
        if ($existing === null) {
            return $data;
        }
        // اپ مدیر قدیمی «areas» را نمی‌فرستد و نقش را همیشه صفر می‌فرستد.
        $legacy = !array_key_exists('areas', $data);
        foreach (['role', 'unit', 'areas', 'permissions', 'crew_role', 'person_id', 'county', 'phone', 'note',
                  'device_code'] as $key) {
            if (!array_key_exists($key, $data) || ($legacy && in_array($key, ['role', 'unit'], true))) {
                $data[$key] = $existing[$key];
            }
        }
        return $data;
    }

    /**
     * ایراد اطلاعات یک کاربری، به فارسی، یا تهی. جدا از save تا صفحه وب بتواند
     * پیام را کنار فرم نشان دهد، و API همان را به صورت خطای JSON بفرستد.
     *
     * @param array<string, mixed> $data
     */
    public static function problem(array $data): ?string
    {
        $data = self::merged($data);
        $userCode = (string) $data['user_code'];
        if ($userCode === '' || trim((string) ($data['full_name'] ?? '')) === '') {
            return 'نام و کد کاربری الزامی است.';
        }
        $isNew = Db::one('SELECT id FROM users WHERE user_code = ?', [$userCode]) === null;
        if ($isNew && !preg_match('/^[A-Za-z0-9._-]{2,64}$/', $userCode)) {
            return 'کد کاربری فقط حرف و رقم لاتین، نقطه، خط تیره و زیرخط باشد.';
        }
        $role = (int) ($data['role'] ?? Auth::ROLE_EXPERT);
        if (!in_array($role, self::ROLES, true)) {
            return 'نقش کاربر معتبر نیست.';
        }
        $areas = Areas::parse($data['areas'] ?? '');
        if (in_array($role, self::NEEDS_AREA, true) && $areas === []) {
            return 'برای مجری دست‌کم یک ناحیه انتخاب کنید.';
        }
        if (in_array($role, self::ONE_AREA, true) && count($areas) > 1) {
            return 'هر کاربری کارشناس، واحد، میدانی و مجری غیرمجاز شهرستان فقط یک ناحیه دارد؛ برای ناحیه دیگر کاربری دیگری بسازید.';
        }
        $password = (string) ($data['password'] ?? '');
        if ($password !== '' && mb_strlen($password) < self::MIN_PASSWORD) {
            return 'رمز عبور باید دست‌کم ۸ نویسه باشد.';
        }
        if ($password === '' && $isNew) {
            return 'برای کاربر تازه رمز عبور لازم است.';
        }
        if ($role === Auth::ROLE_MANAGER) {
            $other = Db::one('SELECT user_code FROM users WHERE role = ? AND active = 1 AND user_code <> ?',
                [Auth::ROLE_MANAGER, $userCode]);
            if ($other !== null) {
                return 'سامانه فقط یک مدیر دارد (' . $other['user_code'] . ').';
            }
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
        $data = self::merged($data);
        $userCode = (string) $data['user_code'];
        $fullName = trim((string) $data['full_name']);
        $role = (int) ($data['role'] ?? Auth::ROLE_EXPERT);
        $existing = Db::one('SELECT * FROM users WHERE user_code = ?', [$userCode]);
        $password = (string) ($data['password'] ?? '');

        $id = $existing['id'] ?? Db::uuid();
        $now = Db::now();
        $active = isset($data['active']) ? ((int) $data['active'] === 1 ? 1 : 0) : (int) ($existing['active'] ?? 1);
        $device = Auth::normaliseDevice((string) ($data['device_code'] ?? ''));
        $permissions = is_numeric($data['permissions'] ?? null) ? (int) $data['permissions'] : 0;
        $unit = is_numeric($data['unit'] ?? null) ? (int) $data['unit'] : null;
        $crewRole = is_numeric($data['crew_role'] ?? null) && $role === Auth::ROLE_FIELD ? (int) $data['crew_role'] : null;
        $areas = in_array($role, [Auth::ROLE_MANAGER, Auth::ROLE_ILLEGAL], true) ? null : Areas::store(Areas::parse($data['areas'] ?? ''));
        $county = trim((string) ($data['county'] ?? ''));
        if ($areas !== null) {
            $county = (string) (Areas::NAMES[explode(',', $areas)[0]] ?? $county);
        }
        $personId = ($data['person_id'] ?? null) ?: null;

        Db::run(
            'INSERT INTO users (id, user_code, full_name, role, unit, county, areas, phone, device_code,
                                password_hash, active, created_at, updated_at, note, permissions, person_id, crew_role)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE
                full_name = VALUES(full_name), role = VALUES(role), unit = VALUES(unit),
                county = VALUES(county), areas = VALUES(areas), phone = VALUES(phone), device_code = VALUES(device_code),
                password_hash = VALUES(password_hash), active = VALUES(active),
                updated_at = VALUES(updated_at), note = VALUES(note), permissions = VALUES(permissions),
                person_id = VALUES(person_id), crew_role = VALUES(crew_role)',
            [
                $id, $userCode, $fullName, $role,
                $role === Auth::ROLE_UNIT ? $unit : null,
                $county === '' ? null : $county,
                $areas,
                trim((string) ($data['phone'] ?? '')),
                $device,
                $password !== '' ? Auth::hashPassword($password) : ($existing['password_hash'] ?? null),
                $active,
                $existing['created_at'] ?? $now,
                $now,
                trim((string) ($data['note'] ?? '')),
                $role === Auth::ROLE_FIELD ? ($permissions & (Auth::PERM_INSPECT | Auth::PERM_REPORT)) : 0,
                $personId,
                $crewRole,
            ]
        );

        if ($existing !== null && ($password !== '' || $active === 0)) {
            Db::run('DELETE FROM tokens WHERE user_id = ?', [$id]);
        }
        // کاربری‌ای که از راه‌های قدیمی (اپ مدیر پیش از ۱.۸، ساخت یک‌جای ناحیه‌ها) بی شخص آمد،
        // شخص خودش را می‌گیرد تا قفل دستگاه و صفحه شخص برایش کار کند.
        if ($personId === null && $role !== Auth::ROLE_MANAGER) {
            $personId = Persons::ensureFor($id);
        }
        // کارشناسی که کد دستگاهش در همین فرم آمده (اپ مدیر قدیمی، فرم کاربر وب): کد اپ
        // کارشناس همان شخص است.
        if ($device !== '' && $role === Auth::ROLE_EXPERT && $personId !== null) {
            PersonDevices::set((string) $personId, 'expert', $device, $managerId);
        }
        CrewAccounts::sync($id);
        return $id;
    }
}
