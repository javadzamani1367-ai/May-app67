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

// شمسی به میلادی، برعکس همان الگوریتم؛ هر روز ده سال رفت و برگشت می‌خورد.
$roundTrip = true;
for ($day = 0; $day < 3653; $day++) {
    $g = (new DateTimeImmutable('2020-03-01'))->modify("+$day day");
    $j = Jalali::fromGregorian((int) $g->format('Y'), (int) $g->format('n'), (int) $g->format('j'));
    if (Jalali::toGregorian(...$j) !== [(int) $g->format('Y'), (int) $g->format('n'), (int) $g->format('j')]) {
        $roundTrip = false;
        break;
    }
}
check('شمسی به میلادی و برگشت، ده سال روزبه‌روز', $roundTrip);
equals('آغاز روز به وقت تهران', '۱۴۰۵/۰۷/۱۳ - ۰۰:۰۰', Jalali::format(Jalali::startOfDay(1405, 7, 13)));
equals('تاریخ با رقم فارسی خوانده می‌شود', [1405, 7, 13], Jalali::parse('۱۴۰۵/۷/۱۳'));
equals('با خط تیره هم', [1405, 12, 29], Jalali::parse('1405-12-29'));
equals('روز ۳۱ مهر نداریم', null, Jalali::parse('1405/07/31'));
equals('ماه ۱۳ نداریم', null, Jalali::parse('1405/13/01'));
equals('متن بی‌ربط تاریخ نیست', null, Jalali::parse('فردا'));
equals('اسفند سال کبیسه ۳۰ روز دارد', [1403, 12, 30], Jalali::parse('1403/12/30'));
equals('و سال عادی ۲۹ روز', null, Jalali::parse('1404/12/30'));

// قاعده پروژه: هیچ فایلی بیش از ۳۰۰ خط. اندروید را tools/verify-resources.py می‌سنجد، سرور را این.
$long = [];
$files = new RecursiveIteratorIterator(new RecursiveDirectoryIterator(__DIR__ . '/../api', FilesystemIterator::SKIP_DOTS));
foreach ($files as $file) {
    if ($file->getExtension() === 'php' && count(file($file->getPathname())) > 300) {
        $long[] = basename($file->getPathname());
    }
}
equals('هیچ فایل PHP سرور بیش از ۳۰۰ خط نیست', [], $long);

// ---------------------------------------------------------------------------
echo "\n— کد QR —\n";
require_once __DIR__ . '/../api/lib/Qr.php';
// اثر انگشت ماتریس‌هایی که کتابخانه qrcode پایتون با همان داده و ماسک ساخت؛
// همه ماسک‌ها و نسخه‌های ۱ تا ۱۰ یک بار ماژول به ماژول مقایسه و با zxing خوانده شدند.
$qrHash = static fn (string $data, int $mask): string => hash('sha256', implode("\n",
    array_map(static fn (array $row): string => implode('', array_map(static fn (bool $v): string => $v ? '1' : '0', $row)),
        Qr::matrix($data, $mask))));
foreach ([
    ['TKV:1:5f0c7a32-9b1e-4c6d-8e2f-1a2b3c4d5e6f', 2, '74ec6fd3720547b29853699ec7ef7d59879e966609e30edf709e956fc1834ef1'],
    ['geo:33.637400,46.422700', 5, 'dbf1740d18e56cdc063bb5a0ae71bcf61c31d32b0ea39fc8cd5d891c0323b2e6'],
    [str_repeat('x', 120), 0, '94b9cf7559b2e47b5b9c8b45bb07936ec3c95db30771c7cc29ed897d30ec6828'],
    [str_repeat('z', 213), 7, '253da557eea8cedaf26e8a042c46688716a06331417decfd6bb98da9637e5220'],
] as [$data, $mask, $expected]) {
    equals('QR ' . strlen($data) . ' بایتی با ماسک ' . $mask . ' همان مرجع است', $expected, $qrHash($data, $mask));
}
equals('اندازه نسخه ۳', 29, count(Qr::matrix('TKV:1:5f0c7a32-9b1e-4c6d-8e2f-1a2b3c4d5e6f')));
check('SVG حاشیه سفید دارد', str_contains(Qr::svg('x'), 'viewBox="0 0 29 29"'));
$tooLong = false;
try {
    Qr::matrix(str_repeat('a', 214));
} catch (InvalidArgumentException $e) {
    $tooLong = true;
}
check('داده بیش از نسخه ۱۰ پذیرفته نمی‌شود', $tooLong);

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
check('هر ۲۱ جدول در schema.sql هست', $tableCount === 21, "$tableCount جدول");

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
echo "\n— نسخه پایگاه داده —\n";
// نصب تازه نسخه‌اش را از schema.sql می‌گیرد و نصب قدیمی از گام‌های
// Migrations. اگر این دو عدد از هم جدا شوند، نصب تازه یا گامی را دوباره اجرا
// می‌کند یا گامی را هرگز نمی‌بیند.
require_once __DIR__ . '/../api/lib/FieldSchema.php';
require_once __DIR__ . '/../api/lib/Migrations.php';
preg_match('/INSERT IGNORE INTO schema_meta \(id, version\) VALUES \(1, (\d+)\)/',
    (string) file_get_contents(__DIR__ . '/../schema.sql'), $versionMatch);
equals('نسخه schema.sql با آخرین گام به‌روزرسانی یکی است', Migrations::latest(), (int) ($versionMatch[1] ?? -1));

// جدول‌های میدانی دو جا نوشته شده‌اند: گام به‌روزرسانی و schema.sql. اگر یکی
// عوض شود و دیگری نه، نصب تازه و نصب به‌روزشده دو پایگاه داده متفاوت دارند.
$flat = static fn (string $s): string => preg_replace('/\s+/', ' ', trim($s));
$schemaFlat = $flat((string) file_get_contents(__DIR__ . '/../schema.sql'));
foreach (FieldSchema::TABLES as $statement) {
    preg_match('/CREATE TABLE IF NOT EXISTS (\w+)/', $statement, $tableName);
    check("جدول {$tableName[1]} در schema.sql عیناً همان گام به‌روزرسانی است",
        str_contains($schemaFlat, $flat($statement)));
}
check('ستون permissions در جدول کاربران schema.sql هست',
    str_contains($schemaFlat, 'permissions TINYINT NOT NULL DEFAULT 0'));

// ---------------------------------------------------------------------------
echo "\n— موردهای اپ میدانی —\n";
foreach (['Response', 'Config', 'Db', 'Auth', 'Field', 'FieldCodes'] as $class) {
    require_once __DIR__ . '/../api/lib/' . $class . '.php';
}
equals('قالب کد رهگیری', 'RZ-1405-000123', FieldCodes::format('RZ', 1405, 123));
// ۲۹ اسفند ۱۴۰۴ و ۱ فروردین ۱۴۰۵، دو طرف مرز سال، به وقت ایران.
equals('سال شمسی آخرین روز سال', 1404, FieldCodes::jalaliYear(strtotime('2026-03-20 12:00:00 Asia/Tehran') * 1000));
equals('سال شمسی اولین روز سال', 1405, FieldCodes::jalaliYear(strtotime('2026-03-21 00:30:00 Asia/Tehran') * 1000));

$report = [
    'id' => '11111111-2222-3333-4444-555555555555', 'kind' => Field::KIND_CRYPTO, 'created_at' => 1759000000000,
    'latitude' => 33.63, 'longitude' => 46.42, 'description' => 'صدای مداوم فن در شب',
];
$photo = ['id' => '11111111-2222-3333-4444-666666666666', 'role' => Field::ROLE_PHOTO,
          'mime' => 'image/jpeg', 'size' => 10, 'sha256' => str_repeat('a', 64)];
equals('گزارش کامل پذیرفته می‌شود', null, Field::problem($report, [$photo]));
check('گزارش بی‌شرح رد می‌شود', Field::problem(['description' => '  '] + $report, []) !== null);
check('گزارش بی‌موقعیت رد می‌شود', Field::problem(['latitude' => null] + $report, []) !== null);
check('شناسه غیر UUID رد می‌شود', Field::problem(['id' => '42'] + $report, []) !== null);
check('نوع فایل ناشناخته رد می‌شود', Field::problem($report, [['mime' => 'application/x-php'] + $photo]) !== null);
check('فایل بی‌اثر انگشت رد می‌شود', Field::problem($report, [['sha256' => 'xyz'] + $photo]) !== null);

$feeder = ['id' => '11111111-2222-3333-4444-777777777777', 'kind' => Field::KIND_FEEDER, 'created_at' => 1759000000000,
           'latitude' => 33.63, 'longitude' => 46.42, 'plate' => 'T-1024'];
equals('آمپرگیری با پلاک پذیرفته می‌شود', null, Field::problem($feeder, []));
check('آمپرگیری بی‌پلاک رد می‌شود', Field::problem(['plate' => ''] + $feeder, []) !== null);

$thermal = ['id' => '11111111-2222-3333-4444-888888888888', 'kind' => Field::KIND_THERMAL, 'created_at' => 1759000000000];
$frame = ['id' => '11111111-2222-3333-4444-999999999999', 'role' => Field::ROLE_THERMAL, 'mime' => 'image/jpeg',
          'size' => 10, 'sha256' => str_repeat('b', 64), 'asset_type' => Field::ASSET_POLE, 'plate' => '7781'];
equals('تصویر ترموویژن با نوع و پلاک پذیرفته می‌شود', null, Field::problem($thermal, [$frame]));
check('تصویر ترموویژن بی‌پلاک رد می‌شود', Field::problem($thermal, [['plate' => ''] + $frame]) !== null);
check('تصویر ترموویژن بی‌نوع رد می‌شود', Field::problem($thermal, [['asset_type' => null] + $frame]) !== null);

// ---------------------------------------------------------------------------
echo "\n";
echo "$passed تست موفق";
if ($failed > 0) {
    echo "، $failed تست ناموفق\n";
    exit(1);
}
echo "، بدون خطا\n";
