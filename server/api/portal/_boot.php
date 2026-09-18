<?php
declare(strict_types=1);

/** راه‌اندازی مشترک صفحات پرتال: کتابخانه، نشست، و کاربر واردشده. */
foreach (['Response', 'Config', 'Db', 'Request', 'Auth', 'Storage', 'Notifications', 'Jalali'] as $class) {
    require_once dirname(__DIR__) . '/lib/' . $class . '.php';
}
require_once __DIR__ . '/_layout.php';

if (session_status() === PHP_SESSION_NONE) {
    session_set_cookie_params([
        'httponly' => true,
        'samesite' => 'Lax',
        // پرتال پشت HTTPS هاست است؛ اگر نبود، کوکی همچنان کار می‌کند.
        'secure' => (($_SERVER['HTTPS'] ?? '') !== ''),
    ]);
    session_start();
}

function portal_user(): ?array
{
    $id = $_SESSION['user_id'] ?? null;
    if (!is_string($id)) {
        return null;
    }
    return Db::one('SELECT * FROM users WHERE id = ? AND active = 1', [$id]);
}

function portal_require_user(): array
{
    $user = portal_user();
    if ($user === null) {
        header('Location: login.php');
        exit;
    }
    return $user;
}

const UNIT_NAMES = ['واحد فروش', 'واحد حراست', 'واحد حقوقی', 'برق شهرستان'];

function unit_name(?int $unit): string
{
    return UNIT_NAMES[$unit ?? -1] ?? 'نامشخص';
}

/** تاریخ شمسی برای نمایش — همان الگوریتمی که اپ و ویندوز استفاده می‌کنند. */
function jalali(?int $millis): string
{
    return Jalali::format($millis);
}
