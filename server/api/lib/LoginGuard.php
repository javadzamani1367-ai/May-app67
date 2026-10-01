<?php
declare(strict_types=1);

/**
 * قفل ورود ناموفق.
 *
 * دو شمارنده، نه یکی: یکی برای هر کد کاربری، تا حدس زدن رمز یک نفر بعد از
 * چند بار متوقف شود، و یکی برای هر نشانی شبکه با سقف بالاتر، تا کسی نتواند
 * با یک رمز رایج روی صدها کد کاربری بچرخد بی‌آنکه هیچ‌کدام قفل شود.
 *
 * کد کاربری‌ای که وجود ندارد هم دقیقاً مثل یک کد واقعی شمرده و قفل می‌شود:
 * اگر فقط کدهای واقعی قفل می‌شدند، همین تفاوت فهرست کاربران را لو می‌داد.
 */
final class LoginGuard
{
    /** با پاسخ ۴۲۹ متوقف می‌شود اگر کد یا نشانی شبکه قفل باشد. */
    public static function check(string $userCode): void
    {
        $message = self::lockedMessage($userCode);
        if ($message !== null) {
            Response::fail(429, 'locked', $message);
        }
    }

    /** پیام قفل برای نمایش، یا تهی اگر قفل نیست — پرتال صفحه می‌سازد، نه JSON. */
    public static function lockedMessage(string $userCode): ?string
    {
        $now = Db::now();
        foreach (self::scopes($userCode) as $scope) {
            $row = Db::one('SELECT locked_until FROM login_attempts WHERE scope = ?', [$scope['key']]);
            $until = (int) ($row['locked_until'] ?? 0);
            if ($until > $now) {
                $minutes = (int) ceil(($until - $now) / 60000);
                return "تعداد تلاش‌های ناموفق زیاد بود. $minutes دقیقه دیگر دوباره امتحان کنید.";
            }
        }
        return null;
    }

    public static function failed(string $userCode): void
    {
        $now = Db::now();
        $window = self::lockMillis();
        foreach (self::scopes($userCode) as $scope) {
            $row = Db::one('SELECT failures, first_at FROM login_attempts WHERE scope = ?', [$scope['key']]);
            // پنجره شمارش از اولین خطا شروع می‌شود؛ خطایی که بعد از پنجره بیاید
            // شمارش را از نو شروع می‌کند، نه اینکه به خطاهای کهنه اضافه شود.
            $fresh = $row === null || $now - (int) $row['first_at'] > $window;
            $failures = $fresh ? 1 : (int) $row['failures'] + 1;
            $firstAt = $fresh ? $now : (int) $row['first_at'];
            $lockedUntil = $failures >= $scope['limit'] ? $now + $window : 0;
            Db::run(
                'INSERT INTO login_attempts (scope, failures, first_at, locked_until) VALUES (?, ?, ?, ?)
                 ON DUPLICATE KEY UPDATE failures = VALUES(failures), first_at = VALUES(first_at),
                                         locked_until = VALUES(locked_until)',
                [$scope['key'], $failures, $firstAt, $lockedUntil]
            );
        }
    }

    /** ورود درست شمارنده همان کد را پاک می‌کند؛ شمارنده نشانی شبکه می‌ماند. */
    public static function succeeded(string $userCode): void
    {
        Db::run('DELETE FROM login_attempts WHERE scope = ?', [self::scopes($userCode)[0]['key']]);
    }

    /** @return array<int, array{key: string, limit: int}> */
    private static function scopes(string $userCode): array
    {
        $ip = (string) ($_SERVER['REMOTE_ADDR'] ?? '');
        return [
            ['key' => 'u:' . hash('sha256', mb_strtolower(trim($userCode))),
             'limit' => max(1, (int) Config::get('login_max_failures', 5))],
            ['key' => 'ip:' . hash('sha256', $ip),
             'limit' => max(1, (int) Config::get('login_ip_max_failures', 20))],
        ];
    }

    private static function lockMillis(): int
    {
        return max(1, (int) Config::get('login_lock_minutes', 15)) * 60000;
    }
}
