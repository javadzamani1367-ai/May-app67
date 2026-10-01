<?php
declare(strict_types=1);

/**
 * پنل وب مدیر: راه‌اندازی، نشست، و محافظت فرم‌ها.
 *
 * نشست جدا از پرتال واحدها (نام کوکی دیگر)، تا ورود به یکی هرگز معنای ورود به
 * دیگری را ندهد. فقط نقش مدیر وارد می‌شود. هر فرمی که چیزی را عوض می‌کند یک
 * توکن CSRF دارد: SameSite=Lax جلوی بیشتر حمله‌ها را می‌گیرد ولی این صفحه‌ها
 * کاربر می‌سازند و رمز عوض می‌کنند، و یک لایه کافی نیست.
 */
foreach (['Response', 'Config', 'Db', 'Request', 'Auth', 'LoginGuard', 'FieldSchema', 'Migrations', 'Storage',
          'Notifications', 'Jalali', 'Field', 'FieldSettings', 'Users'] as $class) {
    require_once dirname(__DIR__) . '/lib/' . $class . '.php';
}
require_once dirname(__DIR__) . '/portal/_layout.php';

Migrations::ensure();

if (session_status() === PHP_SESSION_NONE) {
    session_name('tavankav_admin');
    session_set_cookie_params([
        'httponly' => true,
        'samesite' => 'Strict',
        'secure' => (($_SERVER['HTTPS'] ?? '') !== ''),
    ]);
    session_start();
}

/** مدیر واردشده، یا تهی. حساب غیرفعال‌شده همان لحظه بیرون می‌افتد. */
function admin_user(): ?array
{
    $id = $_SESSION['admin_id'] ?? null;
    if (!is_string($id)) {
        return null;
    }
    return Db::one('SELECT * FROM users WHERE id = ? AND active = 1 AND role = ?', [$id, Auth::ROLE_MANAGER]);
}

function admin_require(): array
{
    $user = admin_user();
    if ($user === null) {
        header('Location: login.php');
        exit;
    }
    return $user;
}

function csrf_token(): string
{
    if (!isset($_SESSION['csrf']) || !is_string($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function csrf_field(): string
{
    return '<input type="hidden" name="csrf" value="' . e(csrf_token()) . '">';
}

/** درخواست POST بدون توکن درست، پیش از هر تغییری متوقف می‌شود. */
function csrf_check(): void
{
    $sent = (string) ($_POST['csrf'] ?? '');
    if ($sent === '' || !hash_equals(csrf_token(), $sent)) {
        http_response_code(400);
        exit('درخواست نامعتبر است. صفحه را دوباره باز کنید.');
    }
}

function admin_header(string $title): void
{
    portal_header($title, true, 'پنل مدیر', [
        ['users.php', 'کاربران'],
        ['settings.php', 'تنظیمات میدانی'],
        ['logout.php', 'خروج'],
    ]);
}

const ROLE_NAMES = [
    Auth::ROLE_EXPERT => 'کارشناس',
    Auth::ROLE_MANAGER => 'مدیر',
    Auth::ROLE_UNIT => 'واحد مقصد',
    Auth::ROLE_FIELD => 'کاربر میدانی',
];

/** مجوزهای کاربر میدانی، به زبان آدم. */
function permission_label(int $bits): string
{
    $parts = [];
    if (($bits & Auth::PERM_INSPECT) !== 0) {
        $parts[] = 'بازرسی';
    }
    if (($bits & Auth::PERM_REPORT) !== 0) {
        $parts[] = 'گزارش';
    }
    return $parts === [] ? 'بدون مجوز' : implode(' و ', $parts);
}
