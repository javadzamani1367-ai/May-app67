<?php
declare(strict_types=1);

/**
 * بررسی نصب. بعد از کپی کردن پوشه روی هاست، این صفحه را یک بار باز کنید:
 * می‌گوید چه چیزی آماده است و چه چیزی نیست.
 *
 * بعد از اینکه همه ردیف‌ها سبز شد، این فایل را پاک کنید — نسخه PHP و تعداد
 * جدول‌ها را نشان می‌دهد و لازم نیست روی اینترنت بماند.
 */
header('Content-Type: text/html; charset=utf-8');

$configPath = __DIR__ . '/config.php';
$config = is_file($configPath) ? require $configPath : null;

$checks = [];
$checks[] = ['نسخه PHP (۸٫۰ یا بالاتر)', PHP_VERSION_ID >= 80000, PHP_VERSION];
$checks[] = ['افزونه PDO MySQL', extension_loaded('pdo_mysql'), ''];
$checks[] = ['افزونه mbstring', extension_loaded('mbstring'), ''];
$checks[] = ['فایل config.php', $config !== null, ''];
$checks[] = ['پوشه storage قابل نوشتن', is_writable(__DIR__ . '/storage'), __DIR__ . '/storage'];
$checks[] = [
    'بازنویسی مسیر (mod_rewrite)',
    function_exists('apache_get_modules') ? in_array('mod_rewrite', apache_get_modules(), true) : true,
    'اگر خاموش باشد، کلاینت از مسیر index.php?route=... استفاده می‌کند و باز هم کار می‌کند',
];

$dbOk = false;
$dbNote = 'config.php ساخته نشده است';
if (is_array($config)) {
    try {
        $pdo = new PDO(
            sprintf('mysql:host=%s;dbname=%s;charset=utf8mb4', $config['db_host'], $config['db_name']),
            (string) $config['db_user'],
            (string) $config['db_pass'],
            [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
        );
        $tables = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
        $dbOk = count($tables) >= 14;
        $dbNote = 'تعداد جدول‌ها: ' . count($tables) . ($dbOk ? '' : ' — فایل schema.sql را وارد کنید');
    } catch (Throwable $e) {
        $dbNote = 'اتصال برقرار نشد — نام پایگاه داده، نام کاربری و رمز را بررسی کنید';
    }
}
$checks[] = ['اتصال به پایگاه داده و جدول‌ها', $dbOk, $dbNote];

$appKey = is_array($config) ? (string) ($config['app_key'] ?? '') : '';
$checks[] = [
    'کلید app_key عوض شده',
    $appKey !== '' && !str_contains($appKey, 'CHANGE-ME'),
    'یک رشته تصادفی طولانی بگذارید و دیگر تغییرش ندهید',
];
?>
<!DOCTYPE html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<title>بررسی نصب</title>
<style>
 body{font-family:Tahoma,sans-serif;max-width:800px;margin:30px auto;line-height:2;padding:0 14px}
 table{width:100%;border-collapse:collapse}
 td{padding:8px;border-bottom:1px solid #ddd;vertical-align:top}
 .ok{color:#14532d;font-weight:bold}.no{color:#8a1616;font-weight:bold}
</style>
</head><body>
<h2>بررسی نصب سرور</h2>
<table>
<?php foreach ($checks as [$label, $ok, $note]): ?>
  <tr>
    <td><?= htmlspecialchars((string) $label, ENT_QUOTES, 'UTF-8') ?></td>
    <td class="<?= $ok ? 'ok' : 'no' ?>"><?= $ok ? 'آماده' : 'ناقص' ?></td>
    <td><?= htmlspecialchars((string) $note, ENT_QUOTES, 'UTF-8') ?></td>
  </tr>
<?php endforeach; ?>
</table>
<p>وقتی همه ردیف‌ها «آماده» شد، این فایل را از روی هاست حذف کنید.</p>
</body></html>
