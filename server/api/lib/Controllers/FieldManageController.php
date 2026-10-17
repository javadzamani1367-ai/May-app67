<?php
declare(strict_types=1);

/**
 * موردهای میدانی در اپ مدیر و اپ مجری‌ها: فهرست، یک مورد، عکس، تغییر وضعیت یا
 * ارجاع، سپردن به مجری و پیگیری. محدوده هر نقش را Scope تعیین می‌کند: مجری رمزارز
 * ناحیه‌اش بی گزارش رمزارز، مجری غیرمجاز فقط برق غیرمجاز. قاعده‌ها در FieldAdmin،
 * FieldManage و FieldHandOff، مشترک با پنل وب.
 */
final class FieldManageController
{
    private const ROLES = [Auth::ROLE_MANAGER, Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL];

    public function index(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::ROLES));
        $get = [];
        foreach (['kind', 'status', 'q', 'overdue', 'assigned', 'user', 'from', 'to'] as $key) {
            $get[$key] = $request->str($key);
        }
        Response::json(FieldManage::list($get, $request->int('page', 0) ?? 0, $scope) + ['server_time' => Db::now()]);
    }

    public function item(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::ROLES));
        $item = FieldManage::item($request->str('id'), $scope);
        if ($item === null) {
            Response::fail(404, 'not_found', 'این مورد پیدا نشد.');
        }
        Response::json(['item' => $item]);
    }

    public function file(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::ROLES));
        $id = $request->str('id');
        $owner = Field::isUuid($id) ? Db::one('SELECT i.* FROM field_files f JOIN field_items i ON i.id = f.item_id WHERE f.id = ?', [$id]) : null;
        $path = $owner !== null && $scope->canSeeField($owner) ? FieldManage::photoPath($id) : null;
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
        $user = Auth::requireAny($request, self::ROLES);
        $item = $this->open($request, new Scope($user));
        $assignee = $request->str('assigned_to');
        $error = FieldAdmin::transition($item, $request->int('status', -1) ?? -1, $user, $request->str('note'),
            Field::isUuid($assignee) ? $assignee : null, $request->int('due_at'));
        $this->done($error, (string) $item['id'], new Scope($user));
    }

    /** سپردن به مجری رمزارز یک ناحیه (مدیر و مجری غیرمجاز). */
    public function hand(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::ROLES));
        $item = $this->open($request, $scope);
        $this->done(FieldHandOff::hand($item, $request->str('executor_id'), $request->str('note'), $scope), (string) $item['id'], $scope);
    }

    public function followUp(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::ROLES));
        $item = $this->open($request, $scope);
        $this->done(FieldHandOff::followUp($item, $request->str('note'), $scope), (string) $item['id'], $scope);
    }

    private function open(Request $request, Scope $scope): array
    {
        $id = $request->str('id');
        $item = Field::isUuid($id) ? Db::one('SELECT * FROM field_items WHERE id = ?', [$id]) : null;
        if ($item === null || !$scope->canSeeField($item)) {
            Response::fail(404, 'not_found', 'این مورد پیدا نشد.');
        }
        if ($item['merged_into'] !== null) {
            Response::fail(409, 'merged', 'این مورد در مورد دیگری ادغام شده؛ وضعیت آن را تغییر دهید.');
        }
        return $item;
    }

    private function done(?string $error, string $id, Scope $scope): void
    {
        if ($error !== null) {
            Response::fail(409, 'refused', $error);
        }
        Response::json(['item' => FieldManage::item($id, $scope)]);
    }
}
