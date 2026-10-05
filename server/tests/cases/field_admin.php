<?php
declare(strict_types=1);

/**
 * پنل مدیر برای موردهای میدانی: فهرست و فیلتر، خروجی اکسل، صفحه مورد با
 * چک‌لیست فارسی و فایل‌ها، چرخه وضعیت و ارجاع با مهلت، و به‌روزرسانی
 * پایگاه داده به گام ۳.
 *
 * از field_cycle.php: $report (رمزارز، در حال بررسی)، $illegal، $photo.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— پنل مدیر: موردهای میدانی —\n";
$pdo->exec('DELETE FROM login_attempts');
$jar = tempnam(sys_get_temp_dir(), 'admin-items');
$probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$code = (string) $pdo->query("SELECT tracking_code FROM field_items WHERE id = '{$report['id']}'")->fetchColumn();
$illegalCode = (string) $pdo->query("SELECT tracking_code FROM field_items WHERE id = '{$illegal['id']}'")->fetchColumn();

$dash = $probe->page('admin/index.php', $jar);
check('داشبورد باز می‌شود و کاربر میدانی را می‌شمارد', $dash['status'] === 200 && str_contains($dash['body'], 'کاربران میدانی'));

$list = $probe->page('admin/items.php', $jar);
check('فهرست، هر دو نوع گزارش را دارد', str_contains($list['body'], $code) && str_contains($list['body'], $illegalCode));
$onlyIllegal = $probe->page('admin/items.php?kind=2', $jar);
check('فیلتر نوع فقط برق غیرمجاز را نشان می‌دهد',
    str_contains($onlyIllegal['body'], $illegalCode) && !str_contains($onlyIllegal['body'], $code));
$search = $probe->page('admin/items.php?q=' . urlencode($code), $jar);
check('جستجوی کد همان مورد را می‌آورد', str_contains($search['body'], $code) && !str_contains($search['body'], $illegalCode));
$future = $probe->page('admin/items.php?from=' . urlencode('۱۴۲۰/۰۱/۰۱'), $jar);
check('فیلتر تاریخ شمسی کار می‌کند', str_contains($future['body'], 'موردی با این فیلترها نیست'));

$xlsx = $probe->page('admin/items_export.php?kind=1', $jar);
check('خروجی اکسل یک فایل zip است', str_starts_with($xlsx['body'], 'PK'));
$xlsxPath = tempnam(sys_get_temp_dir(), 'xlsx');
file_put_contents($xlsxPath, $xlsx['body']);
$zip = new ZipArchive();
$sheet = $zip->open($xlsxPath) === true ? (string) $zip->getFromName('xl/worksheets/sheet1.xml') : '';
check('و کد مورد در برگه است، راست‌به‌چپ', str_contains($sheet, $code) && str_contains($sheet, 'rightToLeft="1"'));
check('فیلتر در خروجی هم اعمال شد', !str_contains($sheet, $illegalCode));
@unlink($xlsxPath);

$page = $probe->page('admin/item.php?id=' . $report['id'], $jar);
check('صفحه مورد، نشانه‌ها را به فارسی گوشی می‌نویسد', str_contains($page['body'], 'صدای مداوم فن یا وزوز'));
check('و عکس را با پیوند امن نشان می‌دهد', str_contains($page['body'], 'file.php?id=' . $photo['id']));
check('و تاریخچه ثبت را دارد', str_contains($page['body'], 'ثبت از گوشی'));
equals('مورد ناموجود ۴۰۴', 404, $probe->page('admin/item.php?id=00000000-0000-0000-0000-000000000000', $jar)['status']);

$file = $probe->page('admin/file.php?id=' . $photo['id'], $jar);
equals('فایل همان بایت‌هاست', $photo['sha256'], hash('sha256', $file['body']));
$anonJar = tempnam(sys_get_temp_dir(), 'anon');
equals('بی‌ورود، فایلی داده نمی‌شود', 'login.php', $probe->page('admin/file.php?id=' . $photo['id'], $anonJar)['location']);
@unlink($anonJar);

echo "\n— پنل مدیر: وضعیت و ارجاع —\n";
preg_match('/name="csrf" value="([0-9a-f]{64})"/', $page['body'], $m);
$token = $m[1] ?? '';
$expertId = (string) $pdo->query('SELECT id FROM users WHERE role = 0 AND active = 1 ORDER BY user_code LIMIT 1')->fetchColumn();
$itemUrl = 'admin/item.php?id=' . $report['id'];
$statusOf = static fn (): int => (int) $pdo->query("SELECT status FROM field_items WHERE id = '{$report['id']}'")->fetchColumn();

equals('بدون توکن CSRF وضعیت عوض نمی‌شود', 400, $probe->page($itemUrl, $jar, ['status' => '2', 'assigned_to' => $expertId])['status']);
$noExpert = $probe->page($itemUrl, $jar, ['status' => '2', 'csrf' => $token]);
check('ارجاع بدون کارشناس رد می‌شود', str_contains($noExpert['body'], 'یک کارشناس فعال انتخاب کنید'));
$past = $probe->page($itemUrl, $jar, ['status' => '2', 'assigned_to' => $expertId, 'due' => '1400/01/01', 'csrf' => $token]);
check('مهلت گذشته پذیرفته نمی‌شود', str_contains($past['body'], 'بعد از امروز'));
$badDate = $probe->page($itemUrl, $jar, ['status' => '2', 'assigned_to' => $expertId, 'due' => 'فردا', 'csrf' => $token]);
check('تاریخ نامفهوم با راهنما رد می‌شود', str_contains($badDate['body'], '۱۴۰۵/۰۷/۲۰'));
equals('هیچ‌کدام وضعیت را عوض نکرد', 1, $statusOf());

$before = (int) $pdo->query("SELECT updated_at FROM field_items WHERE id = '{$report['id']}'")->fetchColumn();
$referred = $probe->page($itemUrl, $jar, ['status' => '2', 'assigned_to' => $expertId, 'due' => '1420/01/01',
    'note' => 'با پلیس هماهنگ شود', 'csrf' => $token]);
check('ارجاع ثبت شد', str_contains($referred['location'], 'saved=1'), (string) $referred['status']);
$row = $pdo->query("SELECT status, assigned_to, due_at, updated_at FROM field_items WHERE id = '{$report['id']}'")->fetch(PDO::FETCH_ASSOC);
equals('وضعیت و کارشناس ذخیره شد', [2, $expertId], [(int) $row['status'], $row['assigned_to']]);
check('مهلت تا پایان همان روز است', (int) $row['due_at'] % 1000 === 999);
check('updated_at جلو رفت تا گوشی وضعیت تازه را بگیرد', (int) $row['updated_at'] > $before);
equals('کارشناس اعلان ارجاع گرفت', 1, (int) $pdo->query(
    "SELECT COUNT(*) FROM notifications WHERE user_id = '$expertId' AND kind = 'field_referral'")->fetchColumn());
$event = $pdo->query("SELECT from_status, to_status, note FROM field_events WHERE item_id = '{$report['id']}' ORDER BY id DESC LIMIT 1")->fetch(PDO::FETCH_ASSOC);
check('تاریخچه، تغییر و توضیح را دارد', (int) $event['from_status'] === 1 && (int) $event['to_status'] === 2
    && str_contains((string) $event['note'], 'با پلیس هماهنگ شود'));
$phone = $probe->json('GET', 'field/items&since=' . $before, [], $reporter)['body']['data']['items'] ?? [];
equals('گوشی کاربر وضعیت «ارجاع‌شده» را می‌گیرد', [2], array_column($phone, 'status'));

$jump = $probe->page($itemUrl, $jar, ['status' => '5', 'note' => 'x', 'csrf' => $token]);
check('از ارجاع‌شده مستقیم به رد نمی‌شود رفت', str_contains($jump['body'], 'ممکن نیست'));
equals('وضعیت همان ارجاع‌شده ماند', 2, $statusOf());

$pdo->exec("UPDATE field_items SET due_at = 1000 WHERE id = '{$report['id']}'");
$overdue = $probe->page('admin/items.php?overdue=1', $jar);
check('فیلتر مهلت گذشته، ارجاع دیرشده را نشان می‌دهد', str_contains($overdue['body'], $code) && str_contains($overdue['body'], 'مهلت گذشته'));
check('و داشبورد فهرستش می‌کند', str_contains($probe->page('admin/index.php', $jar)['body'], 'ارجاع‌های با مهلت گذشته'));

$revisit = $probe->page($itemUrl, $jar, ['status' => '6', 'csrf' => $token]);
check('بازدید دوباره بی‌دلیل رد می‌شود', str_contains($revisit['body'], 'دلیل را بنویسید'));
$probe->page($itemUrl, $jar, ['status' => '3', 'note' => 'دستگاه‌ها جمع شد', 'csrf' => $token]);
$probe->page($itemUrl, $jar, ['status' => '4', 'csrf' => $token]);
equals('نتیجه و بستن', 4, $statusOf());
check('مورد بسته دیگر مهلت گذشته نیست', !str_contains($probe->page('admin/items.php?overdue=1', $jar)['body'], $code));

echo "\n— به‌روزرسانی پایگاه داده به گام ۳ —\n";
$pdo->exec('ALTER TABLE field_items DROP INDEX idx_field_items_assigned, DROP COLUMN assigned_to, DROP COLUMN due_at');
$pdo->exec('UPDATE schema_meta SET version = 2 WHERE id = 1');
$probe->page('admin/index.php', $jar);
equals('نصب قدیمی خودش به نسخه ۳ رسید', 3, (int) $pdo->query('SELECT version FROM schema_meta WHERE id = 1')->fetchColumn());
equals('ستون‌های ارجاع و نمایه برگشتند', 3, (int) $pdo->query(
    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'field_items'
     AND column_name IN ('assigned_to', 'due_at')")->fetchColumn()
    + (int) $pdo->query("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = DATABASE()
     AND table_name = 'field_items' AND index_name = 'idx_field_items_assigned'")->fetchColumn());

$probe->page('admin/logout.php', $jar);
@unlink($jar);
