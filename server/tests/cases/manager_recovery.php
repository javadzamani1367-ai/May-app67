<?php
declare(strict_types=1);

/**
 * بازیابی حساب مدیر — همان راهی که مدیر قفل‌شده از آن برمی‌گردد.
 *
 * این مسیر روی یک هاست واقعی لازم شد: setup.php بعد از اولین کاربر خودش را
 * می‌بندد، و مدیر تنها کسی است که می‌تواند رمز عوض کند. اگر رمز خودش گم شود،
 * هیچ در دیگری نیست.
 *
 * چیزی که اینجا واقعاً سنجیده می‌شود این است که دروازه‌اش بسته باشد: بدون
 * app_key درست، این صفحه نباید هیچ کاری بکند — وگرنه یک در باز روی اینترنت است.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— بازیابی حساب مدیر —\n";

$before = (int) $pdo->query('SELECT COUNT(*) FROM users')->fetchColumn();

$refused = $probe->formHtml('reset-manager.php', [
    'app_key' => 'wrong-key',
    'user_code' => 'intruder',
    'full_name' => 'نفوذی',
    'password' => 'intruder-pass-1',
]);
check('کلید غلط، کاربری نمی‌سازد', str_contains($refused, 'کلید امنیتی درست نیست'));
equals(
    'کلید غلط، هیچ ردی نمی‌گذارد',
    $before,
    (int) $pdo->query('SELECT COUNT(*) FROM users')->fetchColumn()
);

$key = str_repeat('k', 64); // همان app_key که integration.php در config تست می‌نویسد

$listing = $probe->formHtml('reset-manager.php', ['app_key' => $key, 'list' => '1']);
check('با کلید درست، فهرست کاربران را نشان می‌دهد', str_contains($listing, 'mgr'));
check('فهرست، هش رمز را لو نمی‌دهد', !str_contains($listing, 'password_hash')
    && !str_contains($listing, '$2y$'));

$created = $probe->formHtml('reset-manager.php', [
    'app_key' => $key,
    'user_code' => 'mgr2',
    'full_name' => 'مدیر دوم',
    'password' => 'second-manager-1',
]);
check('حساب مدیر تازه ساخته می‌شود', str_contains($created, 'ساخته شد'), $created);

$login = $probe->json('POST', 'auth/login', [
    'user_code' => 'mgr2',
    'password' => 'second-manager-1',
]);
check('مدیر تازه وارد می‌شود', ($login['body']['ok'] ?? false) === true, $login['raw']);
equals('نقشش مدیر است', 1, (int) ($login['body']['data']['user']['role'] ?? -1));

// حساب موجود: رمز عوض می‌شود و نشست‌های قبلی همان حساب باطل.
$oldToken = $login['body']['data']['token'] ?? '';
$changed = $probe->formHtml('reset-manager.php', [
    'app_key' => $key,
    'user_code' => 'mgr2',
    'password' => 'third-manager-1',
]);
check('رمز حساب موجود عوض می‌شود', str_contains($changed, 'عوض شد'), $changed);

$old = $probe->json('POST', 'auth/login', ['user_code' => 'mgr2', 'password' => 'second-manager-1']);
equals('رمز قدیمی دیگر کار نمی‌کند', 401, $old['status']);
$new = $probe->json('POST', 'auth/login', ['user_code' => 'mgr2', 'password' => 'third-manager-1']);
check('رمز تازه کار می‌کند', ($new['body']['ok'] ?? false) === true, $new['raw']);
equals('نشست قدیمی باطل شد', 401, $probe->json('GET', 'auth/me', [], $oldToken)['status']);

// حساب واحد به مدیر ارتقا پیدا می‌کند: مدیری که کد کاربری واحد را وارد کند
// نباید حسابی بگیرد که در اپ مدیر به جایی نمی‌رسد.
$probe->formHtml('reset-manager.php', [
    'app_key' => $key,
    'user_code' => 'unit1',
    'password' => 'unit-to-manager-1',
]);
$promoted = $probe->json('POST', 'auth/login', [
    'user_code' => 'unit1',
    'password' => 'unit-to-manager-1',
]);
equals('حساب واحد، مدیر می‌شود', 1, (int) ($promoted['body']['data']['user']['role'] ?? -1));

$short = $probe->formHtml('reset-manager.php', [
    'app_key' => $key,
    'user_code' => 'mgr3',
    'password' => 'short',
]);
check('رمز کوتاه رد می‌شود', str_contains($short, 'حداقل هشت نویسه'));
equals('حساب با رمز کوتاه ساخته نشد', 0, (int) $pdo->query(
    "SELECT COUNT(*) FROM users WHERE user_code = 'mgr3'"
)->fetchColumn());
