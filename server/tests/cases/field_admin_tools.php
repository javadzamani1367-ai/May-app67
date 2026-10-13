<?php
declare(strict_types=1);

/**
 * ابزارهای بخش دوم پنل: موارد تکراری و ادغام (و برگرداندنش)، برگه ارجاع با QR،
 * خروجی Word و نقشه. SAVE_PAGES=<پوشه> برگه و نقشه را برای دیدن ذخیره می‌کند.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $reporter
 */

echo "\n— پنل مدیر: موارد تکراری و ادغام —\n";
$jar = tempnam(sys_get_temp_dir(), 'admin-tools');
$probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$newId = static fn (): string => vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex(random_bytes(16)), 4));
$push = static function (float $lat, float $lon, int $kind = 1) use ($probe, $reporter, $newId): array {
    $item = ['id' => $newId(), 'kind' => $kind, 'created_at' => 1760000000000, 'updated_at' => 1760000000000,
             'latitude' => $lat, 'longitude' => $lon, 'accuracy' => 5, 'description' => 'گزارش آزمون ادغام',
             'address' => 'ایلام، بلوار آزمون', 'payload' => ['signs' => ['fan_noise']]];
    $code = $probe->json('POST', 'field/item', ['item' => $item, 'files' => []], $reporter)['body']['data']['tracking_code'] ?? '';
    return $item + ['code' => (string) $code];
};
$a = $push(33.6400, 46.4300);
$b = $push(33.6405, 46.4305);       // حدود ۷۰ متر
$far = $push(33.6600, 46.4300);     // بیش از دو کیلومتر
$other = $push(33.6401, 46.4301, 2); // برق غیرمجاز، همان‌جا

$dups = $probe->page('admin/duplicates.php', $jar)['body'];
check('دو گزارش نزدیک جفت تکراری‌اند', str_contains($dups, $a['code']) && str_contains($dups, $b['code']));
check('گزارش دور و نوع دیگر جفت نمی‌شوند', !str_contains($dups, $far['code']) && !str_contains($dups, $other['code']));
check('داشبورد شمار تکراری‌ها را دارد', str_contains($probe->page('admin/index.php', $jar)['body'], 'احتمالاً تکراری'));

$bUrl = 'admin/item.php?id=' . $b['id'];
$bPage = $probe->page($bUrl, $jar)['body'];
check('صفحه مورد، تکراری احتمالی را با فاصله‌اش نشان می‌دهد',
    str_contains($bPage, $a['code']) && str_contains($bPage, 'متر') && !str_contains($bPage, $far['code']));
preg_match('/name="csrf" value="([0-9a-f]{64})"/', $bPage, $m);
$token = $m[1] ?? '';

$wrongKind = $probe->page($bUrl, $jar, ['action' => 'merge', 'target' => $other['id'], 'csrf' => $token]);
check('دو نوع متفاوت ادغام نمی‌شوند', str_contains($wrongKind['body'], 'از یک نوع'));
equals('بدون توکن ادغام نمی‌شود', 400, $probe->page($bUrl, $jar, ['action' => 'merge', 'target' => $a['id']])['status']);

$merged = $probe->page($bUrl, $jar, ['action' => 'merge', 'target' => $a['id'], 'note' => 'همان محل', 'csrf' => $token]);
check('ادغام ثبت شد', str_contains($merged['location'], 'saved=1'));
$row = $pdo->query("SELECT merged_into, status FROM field_items WHERE id = '{$b['id']}'")->fetch(PDO::FETCH_ASSOC);
equals('مورد تکراری بسته شد و به اصلی اشاره می‌کند', [$a['id'], 4], [$row['merged_into'], (int) $row['status']]);
equals('تاریخچه هر دو مورد ادغام را دارد', 2, (int) $pdo->query(
    "SELECT COUNT(*) FROM field_events WHERE action = 'merged' AND item_id IN ('{$a['id']}', '{$b['id']}')")->fetchColumn());
check('مورد ادغام‌شده در فهرست نیست', !str_contains($probe->page('admin/items.php', $jar)['body'], $b['code']));
check('مورد اصلی، ادغام‌شده‌ها را فهرست می‌کند',
    str_contains($probe->page('admin/item.php?id=' . $a['id'], $jar)['body'], 'موردهای ادغام‌شده در این مورد'));
$bAfter = $probe->page($bUrl, $jar)['body'];
check('مورد ادغام‌شده فرم وضعیت ندارد، فقط جدا کردن', str_contains($bAfter, 'جدا کردن از ادغام') && !str_contains($bAfter, 'name="status"'));
check('جفت ادغام‌شده دیگر تکراری شمرده نمی‌شود', !str_contains($probe->page('admin/duplicates.php', $jar)['body'], $b['code']));
$phone = $probe->json('GET', 'field/items&since=0', [], $reporter)['body']['data']['items'] ?? [];
equals('گوشی کاربر «بسته‌شده» را می‌گیرد', [4], array_values(array_map(
    static fn ($i) => $i['status'], array_filter($phone, static fn ($i) => $i['id'] === $b['id']))));

$probe->page($bUrl, $jar, ['action' => 'unmerge', 'csrf' => $token]);
$row = $pdo->query("SELECT merged_into, status FROM field_items WHERE id = '{$b['id']}'")->fetch(PDO::FETCH_ASSOC);
equals('جدا کردن، مورد را دوباره باز می‌کند', [null, 1], [$row['merged_into'], (int) $row['status']]);

echo "\n— پنل مدیر: برگه ارجاع، Word و نقشه —\n";
$sheet = $probe->page('admin/sheet.php?id=' . $a['id'], $jar)['body'];
check('برگه ارجاع کد و دو QR دارد', str_contains($sheet, $a['code']) && substr_count($sheet, '<svg') === 2);
check('نام گزارش‌دهنده به اکیپ گفته نمی‌شود', str_contains($sheet, 'گزارش همکار'));
check('نام نرم‌افزار در سند اداری نیست', !str_contains($sheet, 'توان‌کاو') && !str_contains($sheet, 'TavanKav'));
$pdo->exec("INSERT INTO field_settings (k, v) VALUES ('show_reporter_to_team', '1') ON DUPLICATE KEY UPDATE v = '1'");
$reporterName = (string) $pdo->query("SELECT u.full_name FROM users u JOIN field_items i ON i.user_id = u.id WHERE i.id = '{$a['id']}'")->fetchColumn();
check('با تنظیم مدیر، نام گزارش‌دهنده می‌آید', str_contains($probe->page('admin/sheet.php?id=' . $a['id'], $jar)['body'], $reporterName));
$pdo->exec("DELETE FROM field_settings WHERE k = 'show_reporter_to_team'");

$docx = $probe->page('admin/item_docx.php?id=' . $a['id'], $jar)['body'];
$docxPath = tempnam(sys_get_temp_dir(), 'docx');
file_put_contents($docxPath, $docx);
$zip = new ZipArchive();
$document = $zip->open($docxPath) === true ? (string) $zip->getFromName('word/document.xml') : '';
check('خروجی Word یک سند خوانا با کد مورد است', $document !== '' && simplexml_load_string($document) !== false
    && str_contains($document, $a['code']));
check('و چک‌لیست را با برچسب فارسی دارد', str_contains($document, 'صدای مداوم فن یا وزوز'));
@unlink($docxPath);

$map = $probe->page('admin/map.php', $jar)['body'];
check('نقشه، موردهای دارای موقعیت را دارد', str_contains($map, $a['code']) && str_contains($map, $far['code']));
check('فیلتر نوع روی نقشه هم اعمال می‌شود', !str_contains($probe->page('admin/map.php?kind=2', $jar)['body'], $a['code']));

if (getenv('SAVE_PAGES')) {
    file_put_contents(getenv('SAVE_PAGES') . '/sheet.html', $sheet);
    file_put_contents(getenv('SAVE_PAGES') . '/map.html', $map);
    file_put_contents(getenv('SAVE_PAGES') . '/item.html', $probe->page('admin/item.php?id=' . $b['id'], $jar)['body']);
}
$probe->page('admin/logout.php', $jar);
@unlink($jar);
