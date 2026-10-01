<?php
declare(strict_types=1);

/**
 * اپ بازرسی میدانی، از ورود تا رسیدن کامل فایل.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $managerToken
 * @var string $storage
 */

echo "\n— اپ میدانی: حساب و مجوز —\n";
$pdo->exec('DELETE FROM login_attempts');
foreach ([['f-100', 2], ['f-200', 3]] as [$code, $permissions]) {
    $saved = $probe->json('POST', 'users/save', [
        'user_code' => $code, 'full_name' => "همکار $code", 'role' => 3,
        'permissions' => $permissions, 'password' => 'field-pass-1', 'active' => 1,
    ], $managerToken);
    check("کاربر میدانی $code ساخته شد", ($saved['body']['ok'] ?? false) === true, $saved['raw']);
}
$fieldLogin = static fn (string $code) => $probe->json('POST', 'auth/login', [
    'user_code' => $code, 'password' => 'field-pass-1', 'device_code' => 'PHONE-' . $code,
]);
$login = $fieldLogin('f-100');
check('کاربر میدانی بدون ثبت دستگاه وارد می‌شود', ($login['body']['ok'] ?? false) === true, $login['raw']);
equals('مجوزها در پاسخ ورود هست', 2, $login['body']['data']['user']['permissions'] ?? null);
$reporter = (string) ($login['body']['data']['token'] ?? '');
$inspector = (string) ($fieldLogin('f-200')['body']['data']['token'] ?? '');

$expires = (int) $pdo->query("SELECT MAX(t.expires_at - t.created_at) FROM tokens t JOIN users u ON u.id = t.user_id
                              WHERE u.user_code = 'f-100'")->fetchColumn();
equals('نشست کاربر میدانی ۷ روز است', 7 * 86400000, $expires);

equals('کاربر میدانی به همگام‌سازی پرونده‌ها راه ندارد', 403,
    $probe->json('GET', 'sync/manifest&since=0', [], $reporter)['status']);
equals('کاربر میدانی پرونده رسمی نمی‌فرستد', 403,
    $probe->json('POST', 'sync/report', ['report' => ['id' => 'x']], $reporter)['status']);
equals('کارشناس و مدیر به مسیر میدانی راه ندارند', 403, $probe->json('GET', 'field/me', [], $managerToken)['status']);
$me = $probe->json('GET', 'field/me', [], $reporter);
equals('تنظیم حد مجاز آمپر به گوشی می‌رسد', 10, $me['body']['data']['settings']['amp_tolerance_pct'] ?? null);

echo "\n— اپ میدانی: ارسال مورد و کد رهگیری —\n";
$uuid = static fn (): string => vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex(random_bytes(16)), 4));
$photoBytes = random_bytes(300000);
$photo = ['id' => $uuid(), 'role' => 0, 'mime' => 'image/jpeg', 'size' => strlen($photoBytes),
          'sha256' => hash('sha256', $photoBytes), 'captured_at' => 1759000000000];
$report = ['id' => $uuid(), 'kind' => 1, 'created_at' => 1759000000000, 'updated_at' => 1759000000000,
           'latitude' => 33.6374, 'longitude' => 46.4227, 'accuracy' => 4.2, 'address' => 'ایلام، خیابان سعدی',
           'description' => 'صدای مداوم فن در شب', 'priority' => 0,
           'payload' => ['signs' => ['fan_noise', 'covered_windows']]];

equals('بدون شرح ۴۲۲', 422, $probe->json('POST', 'field/item',
    ['item' => ['description' => ''] + $report, 'files' => []], $reporter)['status']);
equals('ترموویژن بدون مجوز بازرسی ۴۰۳', 403, $probe->json('POST', 'field/item',
    ['item' => ['id' => $uuid(), 'kind' => 3, 'created_at' => 1759000000000], 'files' => []], $reporter)['status']);

$push = $probe->json('POST', 'field/item', ['item' => $report, 'files' => [$photo]], $reporter);
$code = (string) ($push['body']['data']['tracking_code'] ?? '');
check('کد رهگیری را سرور می‌دهد', preg_match('/^RZ-1404-\d{6}$/', $code) === 1, $push['raw']);
equals('فایل هنوز نرسیده در پاسخ می‌آید', [$photo['id']], $push['body']['data']['missing_files'] ?? null);
$again = $probe->json('POST', 'field/item', ['item' => $report, 'files' => [$photo]], $reporter);
equals('ارسال دوباره همان کد را می‌دهد', $code, $again['body']['data']['tracking_code'] ?? null);
equals('ارسال دوباره مورد دوم نمی‌سازد', 1, (int) $pdo->query('SELECT COUNT(*) FROM field_items')->fetchColumn());

$second = ['id' => $uuid(), 'kind' => 1] + $report;
$secondCode = (string) ($probe->json('POST', 'field/item', ['item' => $second, 'files' => []], $reporter)['body']['data']['tracking_code'] ?? '');
equals('مورد بعدی شماره بعدی را می‌گیرد', (int) substr($code, -6) + 1, (int) substr($secondCode, -6));
$illegal = ['id' => $uuid(), 'kind' => 2] + $report;
$illegalCode = (string) ($probe->json('POST', 'field/item', ['item' => $illegal, 'files' => []], $reporter)['body']['data']['tracking_code'] ?? '');
equals('برق غیرمجاز شمارنده خودش را دارد', 'GH-1404-000001', $illegalCode);

$stolen = $probe->json('POST', 'field/item', ['item' => $report + [], 'files' => []], $inspector);
equals('کاربر دیگر مورد کسی را بازنویسی نمی‌کند', 403, $stolen['status']);

echo "\n— اپ میدانی: بارگذاری قابل ادامه —\n";
$route = static fn (string $fileId, ?int $offset = null): string =>
    "field/upload&file_id=$fileId" . ($offset === null ? '' : "&offset=$offset");
$status = $probe->bytes($route($photo['id']), '', $reporter);
equals('اول، سرور می‌گوید چیزی نرسیده', 0, $status['body']['data']['received'] ?? null);
$half = intdiv(strlen($photoBytes), 2);
$first = $probe->bytes($route($photo['id'], 0), substr($photoBytes, 0, $half), $reporter);
equals('تکه اول پذیرفته شد', $half, $first['body']['data']['received'] ?? null);
$skipped = $probe->bytes($route($photo['id'], $half + 10), 'xx', $reporter);
equals('تکه‌ای که از جای غلط شروع شود پذیرفته نمی‌شود', false, $skipped['body']['data']['accepted'] ?? null);
equals('و سرور می‌گوید از کجا ادامه دهد', $half, $skipped['body']['data']['received'] ?? null);
equals('کاربر دیگر به فایل این مورد دسترسی ندارد', 404,
    $probe->bytes($route($photo['id'], $half), 'xx', $inspector)['status']);
$last = $probe->bytes($route($photo['id'], $half), substr($photoBytes, $half), $reporter);
equals('تکه آخر فایل را کامل کرد', true, $last['body']['data']['complete'] ?? null);
$path = (string) $pdo->query("SELECT path FROM field_files WHERE id = '{$photo['id']}' AND complete = 1")->fetchColumn();
check('فایل کامل، بایت‌به‌بایت همان است', $path !== '' && hash_file('sha256', "$storage/$path") === $photo['sha256'], $path);
equals('بعد از رسیدن، فایل در فهرست نرسیده‌ها نیست', [], $probe->json('POST', 'field/item',
    ['item' => $report, 'files' => [$photo]], $reporter)['body']['data']['missing_files'] ?? null);

$badBytes = random_bytes(1000);
$bad = ['id' => $uuid(), 'role' => 0, 'mime' => 'image/jpeg', 'size' => 1000, 'sha256' => str_repeat('0', 64)];
$probe->json('POST', 'field/item', ['item' => $report, 'files' => [$bad]], $reporter);
$mismatch = $probe->bytes($route($bad['id'], 0), $badBytes, $reporter);
equals('فایلی که اثر انگشتش نخواند پذیرفته نمی‌شود', 'hash_mismatch', $mismatch['body']['data']['error'] ?? null);
equals('و از صفر دوباره خواسته می‌شود', 0, $mismatch['body']['data']['received'] ?? null);
equals('فایل خراب کامل علامت نخورد', 0, (int) $pdo->query(
    "SELECT complete FROM field_files WHERE id = '{$bad['id']}'")->fetchColumn());

echo "\n— اپ میدانی: اصلاح و وضعیت —\n";
$edited = ['description' => 'شرح اصلاح‌شده', 'updated_at' => 1759000100000] + $report;
$probe->json('POST', 'field/item', ['item' => $edited, 'files' => []], $reporter);
equals('تا بررسی شروع نشده، کاربر اصلاح می‌کند', 'شرح اصلاح‌شده', $pdo->query(
    "SELECT description FROM field_items WHERE id = '{$report['id']}'")->fetchColumn());
$pdo->exec("UPDATE field_items SET status = 1 WHERE id = '{$report['id']}'");
$late = ['description' => 'تلاش برای تغییر بعد از بررسی', 'updated_at' => 1759000200000] + $report;
$probe->json('POST', 'field/item', ['item' => $late, 'files' => []], $reporter);
equals('بعد از شروع بررسی، اصلاح اثری ندارد', 'شرح اصلاح‌شده', $pdo->query(
    "SELECT description FROM field_items WHERE id = '{$report['id']}'")->fetchColumn());

$mine = $probe->json('GET', 'field/items&since=0', [], $reporter)['body']['data']['items'] ?? [];
equals('کاربر سه مورد خودش را می‌بیند', 3, count($mine));
equals('کاربر دیگر هیچ‌کدام را نمی‌بیند', [], $probe->json('GET', 'field/items&since=0', [], $inspector)['body']['data']['items'] ?? null);
equals('تاریخچه ثبت مورد نوشته شد', 'registered', $pdo->query(
    "SELECT action FROM field_events WHERE item_id = '{$report['id']}' ORDER BY id LIMIT 1")->fetchColumn());
