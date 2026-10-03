<?php
declare(strict_types=1);

/**
 * پنل وب مدیر: فقط مدیر وارد می‌شود، هر تغییر توکن CSRF می‌خواهد، و کاربری
 * که اینجا ساخته شود همان کاربری است که API می‌سازد.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— پنل وب مدیر —\n";
$pdo->exec('DELETE FROM login_attempts');
$jar = tempnam(sys_get_temp_dir(), 'admin-jar');
$csrf = static function (string $html): string {
    preg_match('/name="csrf" value="([0-9a-f]{64})"/', $html, $m);
    return $m[1] ?? '';
};

$anon = $probe->page('admin/users.php', $jar);
equals('بدون ورود به صفحه ورود فرستاده می‌شود', 'login.php', $anon['location']);

$field = $probe->page('admin/login.php', $jar, ['user_code' => 'f-100', 'password' => 'field-pass-1']);
check('کاربر میدانی وارد پنل مدیر نمی‌شود', str_contains($field['body'], 'فقط برای مدیر'));

$in = $probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
equals('مدیر وارد می‌شود', 'users.php', $in['location']);
$list = $probe->page('admin/users.php', $jar);
check('فهرست کاربران، کاربر میدانی را با مجوزش نشان می‌دهد',
    str_contains($list['body'], 'همکار f-200') && str_contains($list['body'], 'بازرسی و گزارش'));

$form = $probe->page('admin/user.php', $jar);
$token = $csrf($form['body']);
check('فرم توکن CSRF دارد', $token !== '');

$newUser = ['user_code' => 'f-300', 'full_name' => 'بازرس سوم', 'role' => '3', 'perm_inspect' => 'on',
            'password' => 'field-pass-3', 'active' => 'on'];
$forged = $probe->page('admin/user.php', $jar, $newUser);
equals('بدون توکن CSRF چیزی ساخته نمی‌شود', 400, $forged['status']);
equals('و در پایگاه داده هم نیست', 0, (int) $pdo->query("SELECT COUNT(*) FROM users WHERE user_code = 'f-300'")->fetchColumn());

$made = $probe->page('admin/user.php', $jar, $newUser + ['csrf' => $token]);
check('با توکن، کاربر میدانی ساخته شد', str_contains($made['location'], 'saved=1'), (string) $made['status']);
$row = $pdo->query("SELECT role, permissions, active FROM users WHERE user_code = 'f-300'")->fetch(PDO::FETCH_ASSOC);
equals('نقش و مجوز درست ذخیره شد', ['role' => 3, 'permissions' => 1, 'active' => 1],
    array_map('intval', $row ?: []));
$login = $probe->json('POST', 'auth/login', ['user_code' => 'f-300', 'password' => 'field-pass-3', 'device_code' => 'P3', 'app' => 'field']);
equals('کاربر ساخته‌شده در پنل از اپ وارد می‌شود', 1, $login['body']['data']['user']['permissions'] ?? null);

$short = $probe->page('admin/user.php', $jar, ['user_code' => 'f-301', 'full_name' => 'کوتاه', 'role' => '3',
    'password' => 'short', 'csrf' => $token]);
check('رمز کوتاه همان‌جا با پیام رد می‌شود', str_contains($short['body'], 'دست‌کم ۸ نویسه'));

// عوض شدن رمز، نشست‌های باز را می‌بندد.
$before = (int) $pdo->query("SELECT COUNT(*) FROM tokens t JOIN users u ON u.id = t.user_id WHERE u.user_code = 'f-300'")->fetchColumn();
$probe->page('admin/user.php?code=f-300', $jar, ['full_name' => 'بازرس سوم', 'role' => '3', 'perm_inspect' => 'on',
    'perm_report' => 'on', 'password' => 'field-pass-new', 'active' => 'on', 'csrf' => $token]);
$after = (int) $pdo->query("SELECT COUNT(*) FROM tokens t JOIN users u ON u.id = t.user_id WHERE u.user_code = 'f-300'")->fetchColumn();
check('عوض شدن رمز نشست باز را بست', $before > 0 && $after === 0, "$before → $after");
equals('مجوز گزارش هم اضافه شد', 3, (int) $pdo->query("SELECT permissions FROM users WHERE user_code = 'f-300'")->fetchColumn());

// تیک مجوز روی نقشی که مجوز نمی‌گیرد: قبلاً «ذخیره شد» می‌گفت و تیک‌ها را دور می‌ریخت.
$ownTicks = $probe->page('admin/user.php?code=mgr', $jar, ['full_name' => 'مدیر', 'role' => '1',
    'perm_inspect' => 'on', 'perm_report' => 'on', 'active' => 'on', 'csrf' => $token]);
check('تیک مجوز روی حساب مدیر، به‌جای «ذخیره شد» دلیل را می‌گوید',
    str_contains($ownTicks['body'], 'کاربر جدا با نقش «کاربر میدانی»') && $ownTicks['location'] === '');
$expertTicks = $probe->page('admin/user.php?code=f-300', $jar, ['full_name' => 'بازرس سوم', 'role' => '0',
    'perm_inspect' => 'on', 'active' => 'on', 'csrf' => $token]);
check('تیک مجوز با نقش کارشناس هم رد می‌شود', str_contains($expertTicks['body'], 'نقش این کاربر «کارشناس»'));
equals('و نقش کاربر میدانی دست نخورد', 3, (int) $pdo->query("SELECT role FROM users WHERE user_code = 'f-300'")->fetchColumn());
check('صفحه حساب مدیر می‌گوید این حساب برای اپ میدانی نیست',
    str_contains($probe->page('admin/user.php?code=mgr', $jar)['body'], 'نمی‌شود وارد اپ'));

$self = $probe->page('admin/user.php?code=mgr', $jar, ['full_name' => 'مدیر', 'role' => '0', 'csrf' => $token]);
equals('مدیر حساب خودش را غیرفعال یا کارشناس نمی‌کند', ['role' => 1, 'active' => 1], array_map('intval',
    $pdo->query("SELECT role, active FROM users WHERE user_code = 'mgr'")->fetch(PDO::FETCH_ASSOC) ?: []));

$settings = $probe->page('admin/settings.php', $jar, ['amp_tolerance_pct' => '15', 'amp_tolerance_min_a' => '4',
    'reporter_result_detail' => '2', 'csrf' => $token]);
check('تنظیمات ذخیره شد', str_contains($settings['body'], 'ذخیره شد'));
$fieldToken = (string) ($probe->json('POST', 'auth/login', ['user_code' => 'f-300', 'password' => 'field-pass-new',
    'device_code' => 'P3', 'app' => 'field'])['body']['data']['token'] ?? '');
$me = $probe->json('GET', 'field/me', [], $fieldToken)['body']['data']['settings'] ?? [];
equals('گوشی تنظیم تازه را می‌گیرد', [15, 4], [$me['amp_tolerance_pct'] ?? null, $me['amp_tolerance_min_a'] ?? null]);

$probe->page('admin/logout.php', $jar);
equals('بعد از خروج، دوباره به صفحه ورود', 'login.php', $probe->page('admin/users.php', $jar)['location']);
@unlink($jar);
