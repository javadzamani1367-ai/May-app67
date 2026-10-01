<?php
declare(strict_types=1);

/**
 * قفل ورود ناموفق، و به‌روزرسانی خودکار پایگاه داده که آن را آورد.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

require_once __DIR__ . '/../../api/lib/Migrations.php';

echo "\n— قفل ورود ناموفق —\n";
$pdo->exec('DELETE FROM login_attempts');

$wrong = static fn (string $code) => $probe->json('POST', 'auth/login', [
    'user_code' => $code, 'password' => 'not-the-password', 'device_code' => 'FD420243ABAC4856',
]);
$right = static fn () => $probe->json('POST', 'auth/login', [
    'user_code' => '2077', 'password' => 'second-expert-1', 'device_code' => 'FD420243ABAC4856',
]);

$statuses = [];
for ($i = 0; $i < 5; $i++) {
    $statuses[] = $wrong('2077')['status'];
}
equals('پنج رمز غلط، پنج بار ۴۰۱', [401, 401, 401, 401, 401], $statuses);
$locked = $right();
equals('بعد از پنج بار، حتی رمز درست قفل است', 429, $locked['status']);
equals('کد خطا locked است', 'locked', $locked['body']['error']['code'] ?? null);

// کدی که وجود ندارد باید عیناً همان رفتار را داشته باشد، وگرنه قفل نشدنش
// نشان می‌داد که این کد روی سامانه نیست.
for ($i = 0; $i < 5; $i++) {
    $wrong('no-such-user');
}
equals('کد ناموجود هم همان‌طور قفل می‌شود', 429, $wrong('no-such-user')['status']);

// گذشت زمان قفل، بدون صبر کردن: پنجره را به گذشته می‌بریم.
$pdo->exec('UPDATE login_attempts SET locked_until = 1, first_at = 1');
$after = $right();
check('بعد از پایان قفل، رمز درست وارد می‌شود', ($after['body']['ok'] ?? false) === true, $after['raw']);
equals('ورود درست شمارنده همان کد را پاک کرد', 0, (int) $pdo->query(
    "SELECT COUNT(*) FROM login_attempts WHERE scope = 'u:" . hash('sha256', '2077') . "'"
)->fetchColumn());

// چرخیدن روی کدهای مختلف با یک نشانی: هیچ کدی به پنج نمی‌رسد، ولی نشانی قفل می‌شود.
$pdo->exec('DELETE FROM login_attempts');
for ($i = 0; $i < 20; $i++) {
    $wrong("spray-$i");
}
equals('بیست خطا از یک نشانی، نشانی را قفل می‌کند', 429, $right()['status']);

$portal = $probe->formHtml('portal/login.php', ['user_code' => 'unit-x', 'password' => 'whatever']);
check('پرتال هم پیام قفل را نشان می‌دهد', str_contains($portal, 'تعداد تلاش‌های ناموفق'), mb_substr($portal, 0, 200));
$pdo->exec('DELETE FROM login_attempts');

echo "\n— به‌روزرسانی خودکار پایگاه داده —\n";
// نصبی از پیش از این تغییر: نه جدول نسخه دارد، نه جدول قفل.
$pdo->exec('DROP TABLE login_attempts');
$pdo->exec('DROP TABLE schema_meta');
$ping = $probe->json('GET', 'ping');
check('سرویس روی نصب قدیمی بالا می‌آید', ($ping['body']['ok'] ?? false) === true, $ping['raw']);
equals('جدول قفل خودکار ساخته شد', 1, (int) $pdo->query(
    "SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'login_attempts'"
)->fetchColumn());
equals('نسخه پایگاه داده به آخرین گام رسید', Migrations::latest(), (int) $pdo->query(
    'SELECT version FROM schema_meta WHERE id = 1'
)->fetchColumn());
$again = $probe->json('GET', 'ping');
check('درخواست بعدی گام‌ها را دوباره اجرا نمی‌کند و خطا نمی‌دهد', ($again['body']['ok'] ?? false) === true);
