<?php
declare(strict_types=1);

/**
 * کد دستگاه، همان‌طور که روی گوشی نمایش داده می‌شود، کار می‌کند.
 *
 * گوشی کد را با خط تیره و ارقام فارسی نشان می‌دهد. مدیری که همان را عیناً
 * در فرم کاربر تایپ می‌کرد رشته‌ای ثبت می‌کرد که هرگز با کد خام گوشی یکی
 * نمی‌شد، و کارشناس برای همیشه «این حساب روی دستگاه دیگری ثبت شده» می‌دید.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $managerToken
 */

echo "\n— کد دستگاه —\n";
$saved = $probe->json('POST', 'users/save', [
    'user_code' => '2077',
    'full_name' => 'کارشناس دوم',
    'role' => 0,
    // دقیقاً همان‌طور که صفحه تنظیمات گوشی نشانش می‌دهد.
    'device_code' => 'fd۴۲-۰۲۴۳-abac-۴۸۵۶',
    'password' => 'second-expert-1',
    'active' => 1,
], $managerToken);
check('کاربر با کد نمایشی ثبت می‌شود', ($saved['body']['ok'] ?? false) === true, $saved['raw']);
equals('کد به شکل خام ذخیره شد', 'FD420243ABAC4856', (string) $pdo->query(
    "SELECT device_code FROM users WHERE user_code = '2077'"
)->fetchColumn());

$login = $probe->json('POST', 'auth/login', [
    'user_code' => '2077',
    'password' => 'second-expert-1',
    'device_code' => 'FD420243ABAC4856',
]);
check('کارشناس از همان گوشی وارد می‌شود', ($login['body']['ok'] ?? false) === true, $login['raw']);

$other = $probe->json('POST', 'auth/login', [
    'user_code' => '2077',
    'password' => 'second-expert-1',
    'device_code' => 'FD420243ABAC4857',
]);
equals('گوشی دیگر همچنان رد می‌شود', 403, $other['status']);
