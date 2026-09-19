<?php
declare(strict_types=1);

/**
 * تست یکپارچه: سرویس واقعی، روی HTTP واقعی، روی MySQL واقعی.
 *
 *     php server/tests/integration.php
 *
 * تست‌های `run.php` به پایگاه داده نیاز ندارند و همه‌جا اجرا می‌شوند. این یکی
 * چیزی را می‌سنجد که آن‌ها نمی‌توانند: اینکه پرس‌وجوها واقعاً روی MySQL کار
 * می‌کنند. همان اولین اجرا، `row_number` را پیدا کرد — کلمه‌ای که از
 * MariaDB 10.2 رزرو شده و بدون بک‌کوت، وارد کردن `schema.sql` در phpMyAdmin
 * را با خطای نحوی متوقف می‌کرد. یعنی نصب روی هاست از قدم دوم جلوتر نمی‌رفت.
 *
 * اگر پایگاه داده‌ای در دسترس نباشد، با پیام «رد شد» تمام می‌شود و خطا
 * نمی‌دهد: توسعه‌دهنده‌ای که MySQL ندارد نباید نتواند بقیه را اجرا کند.
 *
 * متغیرهای محیطی: DB_HOST، DB_NAME، DB_USER، DB_PASS، DB_SOCKET
 */

$passed = 0;
$failed = 0;

function check(string $name, bool $ok, string $detail = ''): void
{
    global $passed, $failed;
    if ($ok) {
        $passed++;
        echo "ok   $name\n";
    } else {
        $failed++;
        echo "FAIL $name" . ($detail !== '' ? " — $detail" : '') . "\n";
    }
}

function equals(string $name, $expected, $actual): void
{
    check($name, $expected === $actual, sprintf(
        'expected %s, got %s',
        var_export($expected, true),
        var_export($actual, true)
    ));
}

function removeTree(string $path): void
{
    if (!is_dir($path)) {
        return;
    }
    $items = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($path, FilesystemIterator::SKIP_DOTS),
        RecursiveIteratorIterator::CHILD_FIRST
    );
    foreach ($items as $item) {
        $item->isDir() ? @rmdir($item->getPathname()) : @unlink($item->getPathname());
    }
    @rmdir($path);
}

function env(string $key, string $fallback): string
{
    $value = getenv($key);
    return is_string($value) && $value !== '' ? $value : $fallback;
}

// ---------------------------------------------------------------------------
$host = env('DB_HOST', '127.0.0.1');
$name = env('DB_NAME', 'inspection_test');
$user = env('DB_USER', 'root');
$pass = env('DB_PASS', '');
$socket = env('DB_SOCKET', '');

$dsn = $socket !== ''
    ? "mysql:unix_socket=$socket;charset=utf8mb4"
    : "mysql:host=$host;charset=utf8mb4";

try {
    $pdo = new PDO($dsn, $user, $pass, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
} catch (PDOException $e) {
    // روی ماشین توسعه‌دهنده‌ای که MySQL ندارد، رد شدن درست است. در CI نه:
    // آنجا رد شدن بی‌صدا یعنی چکی که همیشه سبز است و هیچ‌وقت چیزی نمی‌سنجد.
    if (env('DB_REQUIRED', '') !== '') {
        echo "FAIL پایگاه داده لازم بود و در دسترس نبود — " . $e->getMessage() . "\n";
        exit(1);
    }
    echo "رد شد — پایگاه داده‌ای در دسترس نیست. تست‌های بدون پایگاه داده: php server/tests/run.php\n";
    exit(0);
}

echo "— آماده‌سازی —\n";
$pdo->exec("DROP DATABASE IF EXISTS `$name`");
$pdo->exec("CREATE DATABASE `$name` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
$pdo->exec("USE `$name`");

// اسکیما دقیقاً همان فایلی است که کاربر در phpMyAdmin وارد می‌کند، بدون هیچ
// دست‌کاری — وگرنه این تست چیزی را می‌سنجد که روی هاست نصب نمی‌شود.
$schema = file_get_contents(__DIR__ . '/../schema.sql');
try {
    $pdo->exec($schema);
    check('schema.sql روی MySQL وارد می‌شود', true);
} catch (PDOException $e) {
    check('schema.sql روی MySQL وارد می‌شود', false, $e->getMessage());
    echo "\nبدون اسکیما ادامه ممکن نیست.\n";
    exit(1);
}

$tables = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
equals('هر ۱۴ جدول ساخته شد', 14, count($tables));

// پوشه فایل مخصوص همین اجرا. اگر تست روی پوشه واقعی می‌نوشت، اجرای بعدی
// فایل‌های اجرای قبلی را آنجا پیدا می‌کرد و «سرور این فایل را دارد» را درست
// نتیجه می‌گرفت در حالی که تست هیچ چیزی را نسنجیده بود — همین یک بار اتفاق
// افتاد و دو تست را بی‌دلیل سبز کرد.
$storage = sys_get_temp_dir() . '/inspection-test-storage-' . getmypid();
@mkdir($storage, 0750, true);

$config = sys_get_temp_dir() . '/inspection-test-config.php';
file_put_contents($config, '<?php return ' . var_export([
    'db_host' => $socket !== '' ? 'localhost' . ';unix_socket=' . $socket : $host,
    'db_name' => $name,
    'db_user' => $user,
    'db_pass' => $pass,
    'app_key' => str_repeat('k', 64),
    'token_days' => 30,
    'storage_path' => $storage,
    'max_upload_mb' => 32,
], true) . ';');

require __DIR__ . '/lib/HttpProbe.php';

$probe = new HttpProbe(__DIR__ . '/../api', $config);
if (!$probe->start()) {
    check('سرویس بالا می‌آید', false, 'وب‌سرور داخلی PHP بالا نیامد');
    exit(1);
}
check('سرویس بالا می‌آید', true);

try {
    require __DIR__ . '/cases/api_cycle.php';
} finally {
    $probe->stop();
    @unlink($config);
    removeTree($storage);
    $pdo->exec("DROP DATABASE IF EXISTS `$name`");
}

// ---------------------------------------------------------------------------
echo "\n";
echo "$passed تست موفق";
if ($failed > 0) {
    echo "، $failed تست ناموفق\n";
    exit(1);
}
echo "، بدون خطا\n";
