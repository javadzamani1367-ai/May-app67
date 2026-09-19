<?php
declare(strict_types=1);

/**
 * تست‌های سمت سرور.
 *
 * بدون PHPUnit، چون روی هاست اشتراکی composer نیست و این فایل باید همان‌جا
 * هم اجرا شود:
 *
 *     php server/tests/run.php
 *
 * چیزهایی را تست می‌کند که به پایگاه داده نیاز ندارند: تبدیل تاریخ شمسی،
 * درست بودن نحو همه فایل‌های PHP، و اینکه اسکیمای سرور با اسکیمای گوشی
 * یکی است. اتصال به MySQL اینجا تست نمی‌شود — آن کار `install-check.php`
 * روی خود هاست است.
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
    check(
        $name,
        $expected === $actual,
        sprintf('expected %s, got %s', var_export($expected, true), var_export($actual, true))
    );
}

// ---------------------------------------------------------------------------
echo "— تاریخ شمسی —\n";
require_once __DIR__ . '/../api/lib/Jalali.php';

$reference = [
    ['2024-03-20', [1403, 1, 1]],
    ['2025-03-21', [1404, 1, 1]],
    ['2026-03-21', [1405, 1, 1]],
    ['2023-03-21', [1402, 1, 1]],
    ['2021-03-21', [1400, 1, 1]],
    ['2000-03-20', [1379, 1, 1]],
    ['1979-02-11', [1357, 11, 22]],
    ['2024-12-31', [1403, 10, 11]],
    ['2026-09-18', [1405, 6, 27]],
];
foreach ($reference as [$gregorian, $expected]) {
    [$y, $m, $d] = array_map('intval', explode('-', $gregorian));
    equals("$gregorian به شمسی", $expected, Jalali::fromGregorian($y, $m, $d));
}

// پیوستگی: هر روز میلادی باید دقیقاً یک روز شمسی جلو برود
$previous = null;
$breaks = 0;
$start = new DateTimeImmutable('2020-01-01');
for ($i = 0; $i < 4000; $i++) {
    $day = $start->modify("+$i day");
    $jalali = Jalali::fromGregorian(
        (int) $day->format('Y'),
        (int) $day->format('n'),
        (int) $day->format('j')
    );
    if ($previous !== null) {
        $sameMonth = $jalali[0] === $previous[0]
            && $jalali[1] === $previous[1]
            && $jalali[2] === $previous[2] + 1;
        $rolled = $jalali[2] === 1
            && ($jalali[1] === $previous[1] + 1 || ($jalali[1] === 1 && $previous[1] === 12));
        if (!$sameMonth && !$rolled) {
            $breaks++;
        }
    }
    $previous = $jalali;
}
check('۴۰۰۰ روز متوالی بدون پرش', $breaks === 0, "$breaks پرش");

check(
    'اعداد فارسی در نمایش',
    Jalali::digits('1405/06/27') === '۱۴۰۵/۰۶/۲۷'
);
equals('تاریخ تهی خط تیره می‌شود', '—', Jalali::format(null));

// ---------------------------------------------------------------------------
echo "\n— نحو فایل‌های PHP —\n";
$phpFiles = [];
$directory = new RecursiveIteratorIterator(
    new RecursiveDirectoryIterator(__DIR__ . '/../api', FilesystemIterator::SKIP_DOTS)
);
foreach ($directory as $file) {
    if ($file->getExtension() === 'php') {
        $phpFiles[] = $file->getPathname();
    }
}
sort($phpFiles);
foreach ($phpFiles as $file) {
    $output = [];
    exec('php -l ' . escapeshellarg($file) . ' 2>&1', $output, $status);
    check('نحو ' . basename($file), $status === 0, implode(' ', $output));
}
check('همه فایل‌های PHP پیدا شدند', count($phpFiles) >= 15, count($phpFiles) . ' فایل');

// ---------------------------------------------------------------------------
echo "\n— یکسانی اسکیما با گوشی —\n";
$sql = file_get_contents(__DIR__ . '/../schema.sql');
$entity = file_get_contents(
    __DIR__ . '/../../android/app/src/main/java/ir/ilam/inspection/data/db/ReportEntity.kt'
);

preg_match_all('/@ColumnInfo\(name = "([a-z_]+)"\)/', $entity, $matches);
$phoneColumns = $matches[1];

preg_match('/CREATE TABLE IF NOT EXISTS reports \((.*?)\n\) ENGINE/s', $sql, $block);
$serverBlock = preg_replace('/--[^\n]*/', '', $block[1] ?? '');
preg_match_all('/(?m)^\s*([a-z_]+)\s+(?:CHAR|VARCHAR|TEXT|INT|BIGINT|TINYINT|DOUBLE)/', $serverBlock, $serverMatches);
$serverColumns = $serverMatches[1];

$missingOnServer = array_values(array_diff($phoneColumns, $serverColumns));
$extraOnServer = array_values(array_diff($serverColumns, $phoneColumns));
check('هر ستون گوشی روی سرور هست', $missingOnServer === [], implode(', ', $missingOnServer));
check('سرور ستون اضافه ندارد', $extraOnServer === [], implode(', ', $extraOnServer));

$tableCount = preg_match_all('/CREATE TABLE IF NOT EXISTS/', $sql);
check('هر ۱۴ جدول در schema.sql هست', $tableCount === 14, "$tableCount جدول");

// نسخه اسکیما در سه زبان نوشته شده و هیچ کامپایلری آن سه را با هم مقایسه
// نمی‌کند. اگر یکی جا بماند، سرور در /ping عددی را اعلام می‌کند که اسکیمای
// واقعی‌اش نیست و طرف مقابل بی‌دلیل ادغام را رد یا قبول می‌کند.
function declaredVersion(string $relativePath, string $pattern): ?int
{
    $text = @file_get_contents(__DIR__ . '/' . $relativePath);
    if ($text === false) {
        return null;
    }
    return preg_match($pattern, $text, $m) === 1 ? (int) $m[1] : null;
}

$phoneVersion = declaredVersion(
    '../../android/app/src/main/java/ir/ilam/inspection/data/db/AppDatabase.kt',
    '/const val SCHEMA_VERSION = (\d+)/'
);
$serverVersion = declaredVersion(
    '../api/lib/Controllers/SystemController.php',
    '/public const SCHEMA_VERSION = (\d+)/'
);
$windowsVersion = declaredVersion(
    '../../windows/CryptoInspection.Archive/Data/Schema.cs',
    '/public const int Version = (\d+)/'
);
$documentedVersion = declaredVersion(
    '../../windows/SCHEMA.md',
    '/`SCHEMA_VERSION = (\d+)`/'
);

check('نسخه اسکیمای گوشی خوانده شد', $phoneVersion !== null);
equals('نسخه اسکیمای سرور با گوشی یکی است', $phoneVersion, $serverVersion);
equals('نسخه اسکیمای ویندوز با گوشی یکی است', $phoneVersion, $windowsVersion);
equals('نسخه مستندشده با گوشی یکی است', $phoneVersion, $documentedVersion);

// ---------------------------------------------------------------------------
echo "\n— مسیر فایل‌های آمده از گوشی —\n";

// مسیر رسانه از گوشی می‌آید و فایل روی همان مسیر نوشته می‌شود، پس یک مسیر
// ساخته‌شده می‌تواند روی هر فایلی بنویسد. `Storage::safeRelative()` قبل از هر
// نوشتنی جلوی آن را می‌گیرد، و اینجا تست می‌شود که واقعاً می‌گیرد.
//
// در حالت بد، `Response::fail()` صدا زده می‌شود که `exit` دارد — و با کد صفر،
// پس کد خروج چیزی نمی‌گوید. هر مورد در یک پروسه جدا اجرا می‌شود و نشانه‌ای
// چاپ می‌کند که مسیر رد‌شده هرگز به آن نمی‌رسد.
$probe = __DIR__ . '/probe-path.php';
file_put_contents($probe, <<<'PHP'
<?php
declare(strict_types=1);
foreach (['Response', 'Config', 'Db', 'Storage'] as $class) {
    require_once __DIR__ . '/../api/lib/' . $class . '.php';
}
Storage::safeRelative((string) ($argv[1] ?? ''));
echo "ACCEPTED";
PHP);

function pathAccepted(string $candidate): bool
{
    $output = [];
    exec(
        'php ' . escapeshellarg(__DIR__ . '/probe-path.php') . ' ' . escapeshellarg($candidate) . ' 2>&1',
        $output
    );
    return str_contains(implode("\n", $output), 'ACCEPTED');
}

$goodPaths = [
    'media/11111111-1111-1111-1111-111111111111/1757600500000.jpg',
    'attachments/11111111-1111-1111-1111-111111111111/minutes.pdf',
    'media/abc/clip.mp4',
];
foreach ($goodPaths as $candidate) {
    check("مسیر درست پذیرفته می‌شود: $candidate", pathAccepted($candidate));
}

$badPaths = [
    '../../config.php'           => 'بیرون رفتن از پوشه',
    'media/../../api/config.php' => 'بیرون رفتن با نقطه‌نقطه',
    '/etc/passwd'                => 'مسیر مطلق',
    'exports/report.pdf'         => 'پوشه غیرمجاز',
    'media/x/script.php'         => 'پسوند اجرایی',
    'media/x/.htaccess'          => 'بازنویسی تنظیمات وب',
    ''                           => 'مسیر خالی',
];
foreach ($badPaths as $candidate => $why) {
    check("مسیر بد رد می‌شود ($why)", !pathAccepted($candidate));
}
unlink($probe);

// ---------------------------------------------------------------------------
echo "\n";
echo "$passed تست موفق";
if ($failed > 0) {
    echo "، $failed تست ناموفق\n";
    exit(1);
}
echo "، بدون خطا\n";
