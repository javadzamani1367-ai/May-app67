<?php
declare(strict_types=1);

/**
 * سوابق و مقایسه خوانش‌های تابلو و تیر.
 *
 * کاربر میدانی: سوابق یک پلاک و بسته سوابق ناحیه‌اش برای کار بی‌اینترنت، بی نام ثبت‌کننده.
 * مدیر و مجری‌ها: جستجو، رشته هر کلید، سوابق یک مورد. خروجی اکسل و Word از اپ فقط برای
 * مدیر و مجری ستاد؛ ارجاع از سوابق به کارشناس رمزارز فقط برای مدیر و مجری رمزارز.
 */
final class HistoryController
{
    private const VIEW_ROLES = [Auth::ROLE_MANAGER, Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL, Auth::ROLE_ILLEGAL_AREA];
    private const REFER_ROLES = [Auth::ROLE_MANAGER, Auth::ROLE_EXECUTOR];

    public function phone(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_FIELD);
        $asset = $request->int('asset');
        Response::json(['rows' => FieldHistorySearch::forPhone($user, $asset, $request->str('plate'), $request->str('part') ?: null),
            'settings' => FieldSettings::forPhone()]);
    }

    public function bundle(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_FIELD);
        Response::json(FieldHistorySearch::bundle($user, $request->int('since', 0) ?? 0, $request->str('after')));
    }

    public function phoneImage(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_FIELD);
        self::sendImage(FieldHistorySearch::phoneImage($user, $request->str('id')));
    }

    public function search(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::VIEW_ROLES));
        Response::json(['keys' => FieldHistorySearch::search($scope, self::filters($request)), 'can_export' => CaseActions::canExport($scope->user),
            'can_refer' => in_array((int) $scope->user['role'], self::REFER_ROLES, true)]);
    }

    public function series(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::VIEW_ROLES));
        [$area, $asset, $key, $part] = self::key($request);
        if (!FieldHistorySearch::canSeeKey($scope, $area, $asset, $key, $part)) {
            Response::fail(404, 'not_found', 'سابقه‌ای برای این پلاک در محدوده شما نیست.');
        }
        Response::json(['rows' => FieldHistory::series($area, $asset, $key, $part, null, 200, true),
            'label' => FieldHistory::label($asset, $request->str('plate') ?: $key, $part)]);
    }

    public function item(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::VIEW_ROLES));
        $id = $request->str('id');
        $item = Field::isUuid($id) ? Db::one('SELECT * FROM field_items WHERE id = ?', [$id]) : null;
        if ($item === null || !$scope->canSeeField($item)) {
            Response::fail(404, 'not_found', 'این مورد پیدا نشد.');
        }
        Response::json(['groups' => FieldHistory::forItem($id)]);
    }

    public function image(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::VIEW_ROLES));
        $id = $request->str('id');
        $row = Field::isUuid($id) ? Db::one(
            'SELECT f.path, i.* FROM field_files f JOIN field_items i ON i.id = f.item_id
             WHERE f.id = ? AND f.complete = 1 AND f.role = ? AND f.mime LIKE \'image/%\'', [$id, Field::ROLE_THERMAL]) : null;
        $path = $row !== null && $scope->canSeeField($row) && $row['path'] !== null ? Storage::resolve((string) $row['path']) : null;
        self::sendImage($path !== null && is_file($path) ? $path : null);
    }

    /** اکسل یا Word همان جستجو؛ از اپ فقط مدیر و مجری ستاد. */
    public function export(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::VIEW_ROLES));
        if (!CaseActions::canExport($scope->user)) {
            Response::fail(403, 'forbidden', 'خروجی اکسل و Word سوابق از اپ برای مدیر و مجری ستاد است.');
        }
        $filters = self::filters($request);
        $keys = FieldHistorySearch::search($scope, $filters, 500);
        $name = 'history-' . date('Ymd-His');
        if ($request->str('format') === 'docx') {
            Docx::send($name, FieldHistoryExport::blocks($keys, FieldHistoryExport::filtersText($filters)));
        } else {
            Xlsx::sendBook($name, FieldHistoryExport::book($keys, $scope));
        }
        exit;
    }

    /** ارجاع برای بازرسی رمزارز: پرونده تازه «گزارش همکار» در ناحیه همان پلاک، به کارشناس رمزارز. */
    public function refer(Request $request): void
    {
        $user = Auth::requireAny($request, self::REFER_ROLES);
        $scope = new Scope($user);
        [$area, $asset, $key, $part] = self::key($request);
        $result = HistoryReferral::refer($scope, $area, $asset, $key, $part, $request->str('expert_id'), $request->str('note'),
            $request->int('due_days'));
        // شناسه پرونده تازه، یا متن خطا.
        if (!Field::isUuid($result)) {
            Response::fail(400, 'invalid_referral', $result);
        }
        Response::json(['report_id' => $result, 'flow' => CaseFlow::summary($result)]);
    }

    /** @return array{0: ?string, 1: int, 2: string, 3: string} */
    private static function key(Request $request): array
    {
        $plate = $request->str('plate_key') ?: $request->str('plate');
        return [Areas::code($request->str('area')), (int) ($request->int('asset') ?? 0), FieldReadings::key($plate), $request->str('part')];
    }

    private static function filters(Request $request): array
    {
        $asset = $request->int('asset');
        $kind = $request->int('kind');
        return ['area' => Areas::code($request->str('area')), 'asset' => in_array($asset, [0, 1], true) ? $asset : null,
            'plate' => $request->str('plate'), 'kind' => in_array($kind, Field::INSPECTION_KINDS, true) ? $kind : null,
            'from' => $request->int('from'), 'to' => $request->int('to'), 'anomaly' => $request->str('anomaly') === '1'];
    }

    private static function sendImage(?string $path): void
    {
        if ($path === null) {
            Response::fail(404, 'no_file', 'این تصویر روی سرور نیست.');
        }
        header('Content-Type: image/jpeg');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        exit;
    }
}
