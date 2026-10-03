<?php
declare(strict_types=1);

final class AuthController
{
    public function login(Request $request): void
    {
        $userCode = $request->str('user_code');
        $password = $request->str('password');
        $device = $request->str('device_code');
        if ($userCode === '' || $password === '') {
            Response::fail(400, 'missing_fields', 'کد کاربری و رمز عبور لازم است.');
        }

        $user = Auth::login($userCode, $password, $device !== '' ? $device : null);

        // هر اپ فقط حساب‌های خودش را می‌پذیرد، و این بعد از بررسی رمز است تا پاسخ
        // چیزی درباره حسابی که رمزش را نمی‌دانیم نگوید. اپ میدانی خودش را معرفی
        // می‌کند؛ اپ کارشناس و مدیر (حتی نسخه‌های قدیمی) چیزی نمی‌فرستند.
        $fieldApp = $request->str('app') === 'field';
        $fieldUser = (int) $user['role'] === Auth::ROLE_FIELD;
        if ($fieldApp && !$fieldUser) {
            Response::fail(403, 'wrong_app',
                'این حساب «کاربر میدانی» نیست. مدیر باید در پنل مدیر یک کاربر با نقش «کاربر میدانی» بسازد و مجوز بازرسی یا گزارش بدهد.');
        }
        if (!$fieldApp && $fieldUser) {
            Response::fail(403, 'wrong_app', 'این حساب برای اپ «بازرسی میدانی» است.');
        }

        $token = Auth::issueToken((string) $user['id'], $device !== '' ? $device : null, (int) $user['role']);

        if ($device !== '') {
            Db::run(
                'INSERT INTO sync_log (device_code, user_id, last_seen_at)
                 VALUES (?, ?, ?)
                 ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), last_seen_at = VALUES(last_seen_at)',
                [$device, $user['id'], Db::now()]
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
     * ثبت‌نام دستگاه. کارشناس کد دستگاه و شماره همراهش را می‌فرستد و منتظر
     * می‌ماند؛ هیچ حسابی از این مسیر ساخته نمی‌شود — فقط یک درخواست در صف
     * مدیر می‌نشیند، وگرنه هر کسی می‌توانست برای خودش حساب بسازد.
     */
    public function requestDevice(Request $request): void
    {
        $device = Auth::normaliseDevice($request->str('device_code'));
        if ($device === '') {
            Response::fail(400, 'missing_device', 'کد دستگاه ارسال نشده است.');
        }
        $now = Db::now();
        Db::run(
            'INSERT INTO device_requests (id, device_code, full_name, phone, county, status, created_at)
             VALUES (?, ?, ?, ?, ?, 0, ?)
             ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone),
                                     county = VALUES(county), created_at = VALUES(created_at)',
            [
                Db::uuid(), $device, $request->str('full_name'), $request->str('phone'),
                $request->str('county'), $now,
            ]
        );
        Notifications::toRole(Auth::ROLE_MANAGER, 'device_request',
            'درخواست ثبت دستگاه جدید',
            'کد دستگاه ' . $device . ' در انتظار تأیید است.');

        Response::json(['pending' => true]);
    }

    public static function publicUser(array $row): array
    {
        return [
            'id' => $row['id'],
            'user_code' => $row['user_code'],
            'full_name' => $row['full_name'],
            'role' => (int) $row['role'],
            'unit' => $row['unit'] === null ? null : (int) $row['unit'],
            'county' => $row['county'],
            'permissions' => (int) ($row['permissions'] ?? 0),
        ];
    }
}
