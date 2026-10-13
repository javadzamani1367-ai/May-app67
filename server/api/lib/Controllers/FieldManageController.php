<?php
declare(strict_types=1);

/**
 * موردهای میدانی در اپ مدیر: فهرست، یک مورد، عکس، و تغییر وضعیت یا ارجاع.
 * فقط نقش مدیر. قاعده‌ها در FieldAdmin و FieldManage، مشترک با پنل وب.
 */
final class FieldManageController
{
    public function index(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $get = [];
        foreach (['kind', 'status', 'q', 'overdue', 'assigned', 'user', 'from', 'to'] as $key) {
            $get[$key] = $request->str($key);
        }
        Response::json(FieldManage::list($get, $request->int('page', 0) ?? 0) + ['server_time' => Db::now()]);
    }

    public function item(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $item = FieldManage::item($request->str('id'));
        if ($item === null) {
            Response::fail(404, 'not_found', 'این مورد پیدا نشد.');
        }
        Response::json(['item' => $item]);
    }

    public function file(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $path = FieldManage::photoPath($request->str('id'));
        if ($path === null) {
            Response::fail(404, 'no_file', 'این فایل هنوز به سرور نرسیده.');
        }
        header('Content-Type: application/octet-stream');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        exit;
    }

    /** تغییر وضعیت؛ ارجاع با کارشناس و مهلت (یونیکس میلی‌ثانیه، پایان همان روز را گوشی می‌فرستد). */
    public function status(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        $id = $request->str('id');
        $item = Field::isUuid($id) ? Db::one('SELECT * FROM field_items WHERE id = ?', [$id]) : null;
        if ($item === null) {
            Response::fail(404, 'not_found', 'این مورد پیدا نشد.');
        }
        if ($item['merged_into'] !== null) {
            Response::fail(409, 'merged', 'این مورد در مورد دیگری ادغام شده؛ وضعیت آن را تغییر دهید.');
        }
        $assignee = $request->str('assigned_to');
        $error = FieldAdmin::transition($item, $request->int('status', -1) ?? -1, $manager, $request->str('note'),
            Field::isUuid($assignee) ? $assignee : null, $request->int('due_at'));
        if ($error !== null) {
            Response::fail(409, 'refused', $error);
        }
        Response::json(['item' => FieldManage::item($id)]);
    }
}
