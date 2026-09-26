<?php
declare(strict_types=1);

/** مدیریت کاربران — فقط مدیر. */
final class UserController
{
    public function index(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $rows = Db::all(
            'SELECT id, user_code, full_name, role, unit, county, phone, device_code, active,
                    created_at, updated_at, note
             FROM users ORDER BY role, full_name'
        );
        Response::json(['users' => $rows]);
    }

    /**
     * ثبت یا ویرایش یک کاربر. رمز فقط وقتی نوشته می‌شود که مدیر رمز تازه‌ای
     * داده باشد — وگرنه ذخیره کردن یک ویرایش کوچک، کاربر را از حسابش بیرون
     * می‌انداخت.
     */
    public function save(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        $userCode = $request->str('user_code');
        $fullName = $request->str('full_name');
        if ($userCode === '' || $fullName === '') {
            Response::fail(400, 'missing_fields', 'نام و کد کاربری الزامی است.');
        }

        $existing = Db::one('SELECT * FROM users WHERE user_code = ?', [$userCode]);
        $id = $existing['id'] ?? Db::uuid();
        $now = Db::now();
        $password = $request->str('password');
        $hash = $password !== ''
            ? password_hash($password, PASSWORD_DEFAULT)
            : ($existing['password_hash'] ?? null);

        Db::run(
            'INSERT INTO users (id, user_code, full_name, role, unit, county, phone, device_code,
                                password_hash, active, created_at, updated_at, note)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE
                full_name = VALUES(full_name), role = VALUES(role), unit = VALUES(unit),
                county = VALUES(county), phone = VALUES(phone), device_code = VALUES(device_code),
                password_hash = VALUES(password_hash), active = VALUES(active),
                updated_at = VALUES(updated_at), note = VALUES(note)',
            [
                $id,
                $userCode,
                $fullName,
                $request->int('role', Auth::ROLE_EXPERT),
                $request->int('unit'),
                $request->str('county'),
                $request->str('phone'),
                Auth::normaliseDevice($request->str('device_code')),
                $hash,
                $request->int('active', 1),
                $existing['created_at'] ?? $now,
                $now,
                $request->str('note'),
            ]
        );

        // ثبت دستگاه، درخواست در صف را می‌بندد: وگرنه مدیر برای همیشه یک
        // درخواست باز می‌بیند که قبلاً به آن رسیدگی کرده.
        $device = Auth::normaliseDevice($request->str('device_code'));
        if ($device !== '') {
            Db::run(
                'UPDATE device_requests SET status = 1, decided_at = ?, decided_by = ?
                 WHERE device_code = ? AND status = 0',
                [$now, $manager['id'], $device]
            );
        }

        Response::json(['id' => $id]);
    }

    public function delete(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $id = $request->str('id');
        if ($id === '') {
            Response::fail(400, 'missing_id', 'شناسه کاربر ارسال نشده است.');
        }
        // غیرفعال، نه حذف: پرونده‌های ثبت‌شده به کد این کاربر ارجاع دارند و
        // پاک کردن ردیف، سابقه را بی‌صاحب می‌کند.
        Db::run('UPDATE users SET active = 0, updated_at = ? WHERE id = ?', [Db::now(), $id]);
        Db::run('DELETE FROM tokens WHERE user_id = ?', [$id]);
        Response::json(['deactivated' => true]);
    }

    public function requests(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        Response::json([
            'requests' => Db::all(
                'SELECT * FROM device_requests WHERE status = 0 ORDER BY created_at DESC'
            ),
        ]);
    }

    public function decideRequest(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        $device = Auth::normaliseDevice($request->str('device_code'));
        $status = $request->int('status', 2);
        if ($device === '') {
            Response::fail(400, 'missing_device', 'کد دستگاه ارسال نشده است.');
        }
        Db::run(
            'UPDATE device_requests SET status = ?, decided_at = ?, decided_by = ? WHERE device_code = ?',
            [$status, Db::now(), $manager['id'], $device]
        );
        Response::json(['done' => true]);
    }
}
