<?php
declare(strict_types=1);

/**
 * اشخاص و کاربری‌ها در API — همان که صفحه شخص پنل وب دارد، برای اپ مدیر. فقط مدیر:
 * مجری‌ها کاربری نمی‌سازند و حذف نمی‌کنند. قاعده‌ها در Persons، PersonAccounts،
 * PersonDevices و DeviceRequests، مشترک با پنل.
 */
final class PersonController
{
    public function index(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        Response::json(PersonDirectory::listing($request->str('q'), Areas::code($request->str('area')), $request->str('kind') ?: null,
            $request->str('inactive') === '1') + ['catalog' => PersonDirectory::catalog()]);
    }

    public function one(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $person = Persons::find($request->str('id'));
        if ($person === null) {
            Response::fail(404, 'not_found', 'شخص پیدا نشد.');
        }
        $to = $request->int('to') ?? Db::now();
        $from = $request->int('from') ?? $to - 90 * 86_400_000;
        Response::json(['person' => self::present($person), 'accounts' => Persons::accounts((string) $person['id']),
            'devices' => PersonDevices::of((string) $person['id']), 'work' => PersonWork::of((string) $person['id'], $from, $to),
            'catalog' => PersonDirectory::catalog()]);
    }

    public function save(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $id = $request->str('id') ?: null;
        $result = Persons::save(['full_name' => $request->str('full_name'), 'national_id' => $request->str('national_id'),
            'phone' => $request->str('phone'), 'home_area' => $request->str('home_area'), 'note' => $request->str('note'),
            'auto_bind' => $request->int('auto_bind', 0), 'active' => $request->int('active', 1)], $id);
        if (is_array($result)) {
            Response::fail(400, 'invalid_person', $result['error']);
        }
        Response::json(['id' => $result]);
    }

    /** تیک‌های ناحیه × نقش: کاربری‌های تازه با رمز یک‌بارمصرف، و کاربری‌های بسته‌شده. */
    public function accounts(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        $permissions = [];
        foreach ($request->arr('permissions') as $key => $value) {
            $permissions[(string) $key] = (int) $value;
        }
        $result = PersonAccounts::apply($request->str('person_id'), array_map('strval', $request->arr('ticks')), $permissions, $manager);
        if ($result['error'] !== null) {
            Response::fail(400, 'invalid_account', $result['error']);
        }
        Response::json($result);
    }

    public function password(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        $password = PersonAccounts::resetPassword($request->str('user_id'), $manager);
        if ($password === null) {
            Response::fail(404, 'not_found', 'کاربری پیدا نشد.');
        }
        Response::json(['password' => $password]);
    }

    public function device(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        if (Persons::find($request->str('person_id')) === null) {
            Response::fail(404, 'not_found', 'شخص پیدا نشد.');
        }
        $error = PersonDevices::set($request->str('person_id'), $request->str('app'), $request->str('device_code'), (string) $manager['id']);
        if ($error !== null) {
            Response::fail(400, 'invalid_device', $error);
        }
        Response::json(['devices' => PersonDevices::of($request->str('person_id'))]);
    }

    public function attach(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $error = PersonAccounts::attach($request->str('user_code'), $request->str('person_id'));
        if ($error !== null) {
            Response::fail(400, 'invalid_attach', $error);
        }
        Response::json(['done' => true]);
    }

    public function requests(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        Response::json(['requests' => DeviceRequests::pending()]);
    }

    public function decide(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        $result = DeviceRequests::decide($request->str('id'), $request->str('action'), $request->str('person_id') ?: null, $manager,
            ['full_name' => $request->str('full_name'), 'national_id' => $request->str('national_id')]);
        if (isset($result['error'])) {
            Response::fail(400, 'invalid_request', $result['error']);
        }
        Response::json($result + ['done' => true]);
    }

    private static function present(array $person): array
    {
        return ['id' => $person['id'], 'number' => (int) $person['number'], 'national_id' => $person['national_id'],
            'full_name' => $person['full_name'], 'phone' => $person['phone'], 'home_area' => $person['home_area'],
            'home_area_label' => $person['home_area'] === null ? null : Areas::label($person['home_area']),
            'note' => $person['note'], 'auto_bind' => (int) $person['auto_bind'] === 1, 'active' => (int) $person['active'] === 1];
    }
}
