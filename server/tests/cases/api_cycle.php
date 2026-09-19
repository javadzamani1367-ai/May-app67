<?php
declare(strict_types=1);

/**
 * چرخه کامل، همان‌طور که در واقعیت اتفاق می‌افتد:
 *
 *   مدیر ساخته می‌شود → کارشناس ثبت می‌شود → کارشناس وارد می‌شود →
 *   پرونده را با تصویرش بالا می‌فرستد → برای تأیید می‌فرستد →
 *   مدیر در صف می‌بیند، می‌کشد، و تأیید می‌کند
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— ساخت حساب‌ها —\n";
equals('setup.php حساب مدیر را می‌سازد', 200, $probe->form('setup.php', [
    'user_code' => 'mgr',
    'full_name' => 'مدیر نمونه',
    'password' => 'manager-pass-1',
]));
$users = (int) $pdo->query('SELECT COUNT(*) FROM users')->fetchColumn();
equals('مدیر و چهار واحد ساخته شدند', 5, $users);

equals('setup.php بار دوم کار نمی‌کند', 5, (function () use ($probe, $pdo): int {
    $probe->form('setup.php', [
        'user_code' => 'intruder',
        'full_name' => 'نفوذی',
        'password' => 'another-pass-1',
    ]);
    return (int) $pdo->query('SELECT COUNT(*) FROM users')->fetchColumn();
})());

$login = $probe->json('POST', 'auth/login', ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
check('مدیر وارد می‌شود', ($login['body']['ok'] ?? false) === true, $login['raw']);
$managerToken = $login['body']['data']['token'] ?? '';
check('توکن مدیر صادر شد', $managerToken !== '');

echo "\n— رمز و نشست —\n";
$wrong = $probe->json('POST', 'auth/login', ['user_code' => 'mgr', 'password' => 'not-the-password']);
equals('رمز غلط رد می‌شود', 401, $wrong['status']);
$unknown = $probe->json('POST', 'auth/login', ['user_code' => 'nobody', 'password' => 'not-the-password']);
equals('کاربر ناشناس همان کد را می‌گیرد', $wrong['body']['error']['code'] ?? 'a', $unknown['body']['error']['code'] ?? 'b');
equals(
    'کاربر ناشناس همان پیام را می‌گیرد',
    $wrong['body']['error']['message'] ?? 'a',
    $unknown['body']['error']['message'] ?? 'b'
);

$noToken = $probe->json('GET', 'users');
equals('بدون توکن، دسترسی نیست', 401, $noToken['status']);

$stored = $pdo->query('SELECT token_hash FROM tokens LIMIT 1')->fetchColumn();
check('توکن خام در پایگاه داده نیست', $stored !== $managerToken && strlen((string) $stored) === 64);

echo "\n— ثبت کارشناس —\n";
$saved = $probe->json('POST', 'users/save', [
    'user_code' => '1042',
    'full_name' => 'کارشناس نمونه',
    'role' => 0,
    'county' => 'دره‌شهر',
    'phone' => '09181234567',
    'device_code' => 'DEVICE-AAA',
    'password' => 'expert-pass-1',
    'active' => 1,
], $managerToken);
check('مدیر کارشناس را ثبت می‌کند', ($saved['body']['ok'] ?? false) === true, $saved['raw']);

$expertLogin = $probe->json('POST', 'auth/login', [
    'user_code' => '1042',
    'password' => 'expert-pass-1',
    'device_code' => 'DEVICE-AAA',
]);
check('کارشناس از گوشی ثبت‌شده وارد می‌شود', ($expertLogin['body']['ok'] ?? false) === true, $expertLogin['raw']);
$expertToken = $expertLogin['body']['data']['token'] ?? '';

$wrongDevice = $probe->json('POST', 'auth/login', [
    'user_code' => '1042',
    'password' => 'expert-pass-1',
    'device_code' => 'DEVICE-BBB',
]);
equals('از گوشی دیگر وارد نمی‌شود', 403, $wrongDevice['status']);
equals('و دلیلش را می‌گوید', 'device_mismatch', $wrongDevice['body']['error']['code'] ?? '');

require __DIR__ . '/case_push.php';
