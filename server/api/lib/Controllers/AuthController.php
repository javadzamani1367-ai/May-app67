<?php
declare(strict_types=1);

final class AuthController
{
    /**
     * ورود: اول کد و رمز، بعد اینکه کاربری مال همین اپ است، و آخر قفل دستگاه. به همین
     * ترتیب، تا حسابی که برای اپ دیگری است پیام «دستگاه ثبت نشده» نگیرد و کسی را
     * سراغ کار اشتباهی نفرستد.
     */
    public function login(Request $request): void
    {
        $userCode = $request->str('user_code');
        $password = $request->str('password');
        $device = $request->str('device_code');
        $app = $request->str('app');
        if ($userCode === '' || $password === '') {
            Response::fail(400, 'missing_fields', 'کد کاربری و رمز عبور لازم است.');
        }

        $user = Auth::login($userCode, $password);
        $wrong = self::wrongApp($app, (int) $user['role']);
        if ($wrong !== null) {
            Response::fail(403, 'wrong_app', $wrong);
        }
        PersonDevices::check($user, $app, $device);

        $token = Auth::issueToken((string) $user['id'], $device !== '' ? Auth::normaliseDevice($device) : null, (int) $user['role']);
        if ($device !== '') {
            Db::run(
                'INSERT INTO sync_log (device_code, user_id, last_seen_at)
                 VALUES (?, ?, ?)
                 ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), last_seen_at = VALUES(last_seen_at)',
                [Auth::normaliseDevice($device), $user['id'], Db::now()]
            );
        }

        Response::json([
            'token' => $token,
            'user' => self::publicUser($user),
        ]);
    }

    public function logout(Request $request): void
    {
        $token = $request->bearer();
        if ($token !== null) {
            Db::run('DELETE FROM tokens WHERE token_hash = ?', [Auth::hashToken($token)]);
        }
        Response::json(['done' => true]);
    }

    public function me(Request $request): void
    {
        Response::json(['user' => self::publicUser(Auth::require($request, null, true))]);
    }

    /**
     * «درخواست کاربری» از هر اپ: نام، کد ملی، تلفن، توضیح و کد همین نصب. هیچ کاربری‌ای
     * از این مسیر ساخته نمی‌شود؛ درخواست در صف مدیر می‌نشیند (DeviceRequests).
     */
    public function requestDevice(Request $request): void
    {
        $error = DeviceRequests::submit([
            'device_code' => $request->str('device_code'), 'full_name' => $request->str('full_name'),
            'national_id' => $request->str('national_id'), 'phone' => $request->str('phone'),
            'county' => $request->str('county'), 'note' => $request->str('note'), 'app' => $request->str('app'),
        ]);
        if ($error !== null) {
            Response::fail(400, 'invalid_request', $error);
        }
        Response::json(['pending' => true]);
    }

    /**
     * پیام «حساب برای این اپ نیست»، یا تهی. اپ میدانی همیشه خودش را معرفی کرده؛ اپ‌های
     * کارشناس و مدیر پیش از نسخه ۱.۶ چیزی نمی‌فرستند و مثل قبل پذیرفته می‌شوند — جز حساب
     * میدانی که فقط اپ خودش را دارد.
     */
    private static function wrongApp(string $app, int $role): ?string
    {
        $name = Users::ROLE_NAMES[$role] ?? '';
        if ($app === '') {
            return $role === Auth::ROLE_FIELD ? 'این حساب برای اپ «بازرسی میدانی» است.' : null;
        }
        $allowed = PersonDevices::APP_ROLES[$app] ?? ($app === 'windows' ? [Auth::ROLE_MANAGER, Auth::ROLE_EXECUTOR] : null);
        if ($allowed === null || in_array($role, $allowed, true)) {
            return null;
        }
        $own = PersonDevices::APPS[PersonDevices::appOfRole($role) ?? ''] ?? 'پرتال واحدها (مرورگر)';
        return match ($app) {
            'field' => 'این حساب «کاربر میدانی» نیست. مدیر باید برای شما در همین شهرستان کاربری «کاربر میدانی» یا «اکیپ» بسازد.',
            'windows' => 'دریافت آرشیو ویندوز از سرور با حساب مدیر یا مجری رمزارز ناحیه است؛ این حساب «' . $name . '» است.',
            default => 'این کاربری «' . $name . '» است و با ' . $own . ' باز می‌شود، نه این اپ.',
        };
    }

    public static function publicUser(array $row): array
    {
        $person = ($row['person_id'] ?? null) === null ? null
            : Db::one('SELECT number, national_id, full_name FROM persons WHERE id = ?', [$row['person_id']]);
        return [
            'id' => $row['id'],
            'user_code' => $row['user_code'],
            'full_name' => $row['full_name'],
            'role' => (int) $row['role'],
            'unit' => $row['unit'] === null ? null : (int) $row['unit'],
            'county' => $row['county'],
            'areas' => Areas::parse($row['areas'] ?? ''),
            'permissions' => (int) ($row['permissions'] ?? 0),
            'crew_role' => ($row['crew_role'] ?? null) === null ? null : (int) $row['crew_role'],
            'person_number' => $person === null ? null : (int) $person['number'],
        ];
    }
}
