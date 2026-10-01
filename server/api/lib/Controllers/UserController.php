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
                    permissions, created_at, updated_at, note
             FROM users ORDER BY role, full_name'
        );
        Response::json(['users' => $rows]);
    }

    /** ثبت یا ویرایش یک کاربر؛ قاعده‌ها در Users::save، مشترک با پنل وب. */
    public function save(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        Response::json(['id' => Users::save($request->body, (string) $manager['id'])]);
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
