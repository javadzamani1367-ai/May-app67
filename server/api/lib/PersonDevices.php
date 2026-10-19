<?php
declare(strict_types=1);

/**
 * قفل دستگاه: هر کاربری هر شخص فقط روی نصبی باز می‌شود که مدیر برای آن اپ آن شخص
 * ثبت کرده. هر نصب کد تازه‌ای می‌سازد (KeyStoreVault)، پس با نصب دوباره، مدیر کد
 * تازه را جای قبلی می‌نویسد و ورود باز می‌شود — کاربری‌ها و رمزها همان می‌مانند.
 *
 * پنل وب، پرتال واحدها و آرشیو ویندوز قفل دستگاه ندارند: در مرورگر و رایانه اداره‌اند.
 */
final class PersonDevices
{
    public const APPS = [
        'expert' => 'اپ کارشناس', 'manager' => 'اپ مدیر', 'executor' => 'اپ مجری رمزارز',
        'illegal' => 'اپ برق غیرمجاز', 'field' => 'اپ بازرسی میدانی',
    ];

    /** کدام اپ کدام نقش را می‌پذیرد. */
    public const APP_ROLES = [
        'expert' => [Auth::ROLE_EXPERT],
        'manager' => [Auth::ROLE_MANAGER],
        'executor' => [Auth::ROLE_EXECUTOR],
        'illegal' => [Auth::ROLE_ILLEGAL, Auth::ROLE_ILLEGAL_AREA],
        'field' => [Auth::ROLE_FIELD],
    ];

    /** اپی که این نقش با آن کار می‌کند؛ تهی برای واحد که فقط پرتال دارد. */
    public static function appOfRole(int $role): ?string
    {
        foreach (self::APP_ROLES as $app => $roles) {
            if (in_array($role, $roles, true)) {
                return $app;
            }
        }
        return null;
    }

    /** @return array<string, string> اپ => کد */
    public static function of(string $personId): array
    {
        $out = [];
        foreach (Db::all('SELECT app, device_code FROM person_devices WHERE person_id = ?', [$personId]) as $row) {
            $out[(string) $row['app']] = (string) $row['device_code'];
        }
        return $out;
    }

    /** کد یک اپ؛ خالی یعنی حذف. خطا به فارسی، یا تهی. */
    public static function set(string $personId, string $app, string $code, ?string $byUserId = null): ?string
    {
        if (!isset(self::APPS[$app])) {
            return 'اپ نامعتبر است.';
        }
        $code = Auth::normaliseDevice($code);
        if ($code === '') {
            Db::run('DELETE FROM person_devices WHERE person_id = ? AND app = ?', [$personId, $app]);
            return null;
        }
        if (strlen($code) < 4 || strlen($code) > 64) {
            return 'کد دستگاه کامل نیست؛ همان شانزده نویسه پایین صفحه ورود اپ را بنویسید.';
        }
        Db::run(
            'INSERT INTO person_devices (person_id, app, device_code, registered_at) VALUES (?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE device_code = VALUES(device_code), registered_at = VALUES(registered_at)',
            [$personId, $app, $code, Db::now()]
        );
        // نشست نصب قبلی بسته می‌شود: گوشی گم‌شده نباید با توکن قدیمی کار کند.
        Db::run('DELETE t FROM tokens t JOIN users u ON u.id = t.user_id
                 WHERE u.person_id = ? AND t.device_code IS NOT NULL AND t.device_code <> ? AND u.role IN (' .
            implode(', ', self::APP_ROLES[$app]) . ')', [$personId, $code]);
        Db::run("UPDATE device_requests SET status = 1, decided_at = ?, decided_by = ?, person_id = ?
                 WHERE device_code = ? AND status = 0", [Db::now(), $byUserId, $personId, $code]);
        return null;
    }

    /**
     * کاربری اجازه دارد از این نصب وارد شود؟ وگرنه خطای ۴۰۳ با راهنمای کار.
     *
     * $app همان است که اپ در ورود می‌فرستد. اپ‌های قدیمی چیزی نمی‌فرستند و از نقش
     * فهمیده می‌شوند؛ ورود بی کد دستگاه (مرورگر) برای کارشناس و کاربر میدانی پذیرفته نیست.
     */
    public static function check(array $user, string $app, string $device): void
    {
        $role = (int) $user['role'];
        $device = Auth::normaliseDevice($device);
        if ($app === 'windows') {
            return;
        }
        if (!isset(self::APPS[$app])) {
            if ($device === '') {
                if (in_array($role, [Auth::ROLE_EXPERT, Auth::ROLE_FIELD], true)) {
                    Response::fail(403, 'device_required', 'این کاربری فقط از اپ خودش روی گوشی ثبت‌شده باز می‌شود.');
                }
                return;
            }
            $app = self::appOfRole($role) ?? '';
            if ($app === '') {
                return;
            }
        }
        if ($device === '') {
            Response::fail(403, 'device_required', 'کد دستگاه این نصب ارسال نشده است.');
        }
        $personId = (string) ($user['person_id'] ?? '');
        $person = $personId === '' ? null : Db::one('SELECT auto_bind FROM persons WHERE id = ?', [$personId]);
        $registered = $person === null ? null
            : Db::one('SELECT device_code FROM person_devices WHERE person_id = ? AND app = ?', [$personId, $app]);
        if ($registered === null && $person !== null && (int) $person['auto_bind'] === 1) {
            // شخصی که پیش از قفل دستگاه همه اپ‌ها ثبت شده بود: اولین نصب هر اپش، همان نصب او.
            self::set($personId, $app, $device);
            return;
        }
        if ($registered === null) {
            Response::fail(403, 'device_unregistered',
                'این نصب هنوز برای شما ثبت نشده است. کد دستگاه پایین همین صفحه را با «درخواست کاربری» برای مدیر بفرستید.');
        }
        if ((string) $registered['device_code'] !== $device) {
            Response::fail(403, 'device_mismatch',
                'این کاربری روی نصب دیگری ثبت شده است. اگر اپ را دوباره نصب کرده‌اید، کد تازه پایین صفحه را برای مدیر بفرستید.');
        }
    }
}
