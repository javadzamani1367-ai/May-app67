<?php
declare(strict_types=1);

/**
 * ورود و نشست.
 *
 * رمز عبور با password_hash خود PHP نگه‌داری می‌شود و توکن هرگز خام ذخیره
 * نمی‌شود: فقط SHA-256 آن در پایگاه داده می‌نشیند، پس دسترسی به جدول هم
 * نشست کسی را نمی‌دزدد.
 */
final class Auth
{
    public const ROLE_EXPERT = 0;
    public const ROLE_MANAGER = 1;
    public const ROLE_UNIT = 2;

    public static function hashToken(string $token): string
    {
        return hash('sha256', $token . Config::get('app_key', ''));
    }

    public static function issueToken(string $userId, ?string $deviceCode): string
    {
        $token = bin2hex(random_bytes(32));
        $now = Db::now();
        $days = (int) Config::get('token_days', 30);
        Db::run(
            'INSERT INTO tokens (token_hash, user_id, device_code, created_at, expires_at)
             VALUES (?, ?, ?, ?, ?)',
            [self::hashToken($token), $userId, $deviceCode, $now, $now + $days * 86400000]
        );
        return $token;
    }

    /** کاربر پشت توکن، یا خطای ۴۰۱. هیچ مسیری بدون این فراخوانی محافظت نشده است. */
    public static function require(Request $request, ?int $role = null): array
    {
        $token = $request->bearer();
        if ($token === null || $token === '') {
            Response::fail(401, 'no_token', 'برای این درخواست باید وارد شوید.');
        }
        $row = Db::one(
            'SELECT u.* FROM tokens t
             JOIN users u ON u.id = t.user_id
             WHERE t.token_hash = ? AND t.expires_at > ? AND u.active = 1',
            [self::hashToken($token), Db::now()]
        );
        if ($row === null) {
            Response::fail(401, 'bad_token', 'نشست شما منقضی شده است. دوباره وارد شوید.');
        }
        if ($role !== null && (int) $row['role'] !== $role) {
            Response::fail(403, 'forbidden', 'این بخش برای نقش کاربری شما نیست.');
        }
        return $row;
    }

    public static function login(string $userCode, string $password, ?string $deviceCode): array
    {
        $user = Db::one('SELECT * FROM users WHERE user_code = ? AND active = 1', [$userCode]);

        // پیام یکسان برای «کاربر نیست» و «رمز غلط»: وگرنه می‌شود فهرست
        // کدهای کاربری معتبر را از همین تفاوت پیام بیرون کشید.
        $invalid = 'کد کاربری یا رمز عبور درست نیست.';
        if ($user === null || empty($user['password_hash'])) {
            Response::fail(401, 'bad_credentials', $invalid);
        }
        if (!password_verify($password, (string) $user['password_hash'])) {
            Response::fail(401, 'bad_credentials', $invalid);
        }

        // قفل دستگاه: حساب کارشناس به همان نصبی بسته است که مدیر ثبت کرده.
        if ((int) $user['role'] === self::ROLE_EXPERT) {
            $registered = (string) ($user['device_code'] ?? '');
            if ($registered === '') {
                Response::fail(403, 'device_unregistered',
                    'کد دستگاه شما هنوز توسط مدیر ثبت نشده است.');
            }
            if ($deviceCode === null || strcasecmp($registered, $deviceCode) !== 0) {
                Response::fail(403, 'device_mismatch',
                    'این حساب روی دستگاه دیگری ثبت شده است. برای ثبت دستگاه جدید با مدیر تماس بگیرید.');
            }
        }

        return $user;
    }
}
