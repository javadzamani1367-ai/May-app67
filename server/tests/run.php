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

// بارگذاری بی composer یک فهرست صریح است: کلاسی که فقط در فهرست جا بماند،
// فقط وقتی آن مسیر صدا زده شود خطای داخلی سرور می‌دهد. هر کلاس lib که از
// ورودی (index.php یا پنل مدیر) یا از کلاس‌های بارشده‌اش صدا زده شود باید در فهرست باشد.
$lib = __DIR__ . '/../api/lib/';
$loadedBy = static function (string $entry) use ($lib): array {
    preg_match('/foreach \(\[(.*?)\] as \$class\)/s', (string) file_get_contents($entry), $m);
    preg_match_all("/'([A-Za-z]+)'/", $m[1] ?? '', $names);
    return $names[1];
};
$referenced = static function (array $files) use ($lib): array {
    $found = [];
    foreach ($files as $file) {
        preg_match_all('/\b(?:new\s+)?([A-Z][A-Za-z0-9]+)::|new\s+([A-Z][A-Za-z0-9]+)\(/', (string) file_get_contents($file), $m);
        foreach (array_filter(array_merge($m[1], $m[2])) as $name) {
            if (is_file($lib . $name . '.php')) {
                $found[$name] = basename($file);
            }
        }
    }
    return $found;
};
foreach ([
    'index.php' => [__DIR__ . '/../api/index.php', glob($lib . 'Controllers/*.php') ?: []],
    'پنل مدیر' => [__DIR__ . '/../api/admin/_boot.php', glob(__DIR__ . '/../api/admin/*.php') ?: []],
] as $label => [$entry, $pages]) {
    $loaded = $loadedBy($entry);
    // کلاسی که خود یک فایل lib با require_once می‌آورد (QrLayout در Qr) هم بارشده است.
    foreach ($loaded as $name) {
        preg_match_all("/require_once __DIR__ \\. '\\/([A-Za-z]+)\\.php'/", (string) file_get_contents($lib . $name . '.php'), $req);
        $loaded = array_merge($loaded, $req[1]);
    }
    $files = array_merge($pages, array_map(static fn ($n) => $lib . $n . '.php', $loaded));
    $missing = array_diff_key($referenced($files), array_flip($loaded));
    check("هر کلاس lib که $label به کار می‌برد بار می‌شود", $missing === [],
        implode(', ', array_map(static fn ($k, $v) => "$k ($v)", array_keys($missing), $missing)));
}

// ---------------------------------------------------------------------------
echo "\n— یکسانی اسکیما با گوشی —\n";
$sql = file_get_contents(__DIR__ . '/../schema.sql');
$entity = file_get_contents(
    __DIR__ . '/../../android/app/src/main/java/ir/ilam/inspection/data/db/ReportEntity.kt'
);

preg_match_all('/@ColumnInfo\(name = "([a-z_]+)"[^)]*\)/', $entity, $matches);
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
check('هر ۳۲ جدول در schema.sql هست', $tableCount === 32, "$tableCount جدول");

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

$burn = ['kind' => Field::KIND_BURN, 'payload' => ['equipment' => ['main_switch' => 1, 'meter' => 0]]] + $report;
equals('سوختگی با تجهیز پذیرفته می‌شود', null, Field::problem($burn, []));
check('سوختگی بی‌تجهیز رد می‌شود', Field::problem(['payload' => ['equipment' => ['meter' => 0]]] + $burn, []) !== null);
equals('سوختگی با تجهیز نام‌برده پذیرفته می‌شود', null,
    Field::problem(['payload' => ['other_equipment' => 'سرکابل']] + $burn, []));
check('سوختگی بی‌شرح رد می‌شود', Field::problem(['description' => ''] + $burn, []) !== null);
equals('پیشوند کد سوختگی', 'SK', Field::KINDS[Field::KIND_BURN]['prefix']);
equals('پیشوند کد تیربه‌تیر', 'FT', Field::KINDS[Field::KIND_LINE]['prefix']);

$pole = static fn (float $r, float $s, float $t): array => ['state' => 'measured', 'phases' => ['r' => $r, 's' => $s, 't' => $t]];
$line = ['kind' => Field::KIND_LINE, 'payload' => ['feeder' => 'A', 'start' => ['r' => 100, 's' => 100, 't' => 100],
    'poles' => [$pole(95, 96, 94), ['state' => 'not_measured', 'reason' => 'ارتفاع'], $pole(40, 45, 38)],
    'analysis' => ['heavy' => [['from' => 1, 'to' => 3, 'drop' => 162]], 'rises' => []]]] + $feeder;
equals('تیربه‌تیر کامل پذیرفته می‌شود', null, Field::problem($line, []));
check('تیربه‌تیر با یک تیر رد می‌شود',
    Field::problem(['payload' => ['feeder' => 'A', 'poles' => [$pole(1, 2, 3)]]] + $line, []) !== null);
check('تیربه‌تیر بی‌فیدر رد می‌شود',
    Field::problem(['payload' => ['poles' => [$pole(1, 2, 3), $pole(1, 2, 3)]]] + $line, []) !== null);
check('تیربه‌تیر بی‌پلاک تابلو رد می‌شود', Field::problem(['plate' => ''] + $line, []) !== null);
foreach (['FieldPresenter', 'FieldLabels', 'FieldLine', 'FieldRoster', 'CrewCalendar', 'GeoPlaces', 'CrewWork', 'CrewDays'] as $class) {
    require_once __DIR__ . '/../api/lib/' . $class . '.php';
}
equals('تابلو و دو تیر اندازه‌گیری‌شده', [0, 1, 3], array_column(FieldLine::points($line['payload']), 'pole'));
$table = FieldLine::poleTable($line['payload']);
equals('افت تیر ۱ نسبت به تابلو', 'افت ۱۵', $table[0][3]);
equals('افت تیر ۳ نسبت به آخرین تیر اندازه‌گیری‌شده', 'افت ۱۶۲', $table[2][3]);
equals('تیر بی زمان ثبت، ستون ثبت خالی', '', $table[0][6]);
// ثبت موقعیت روی نقشه: فاصله کاربر از نقطه، و متن خوانا برای پنل.
$hundredNorth = 33.6 + 100 / 6_371_000 * 180 / M_PI;
check('فاصله صد متری درست است', abs(PositionAudit::distance(33.6, 46.4, $hundredNorth, 46.4) - 100) < 0.5);
equals('بی موقعیت کاربر، فاصله‌ای نیست', null, PositionAudit::observerDistance(33.6, 46.4, null, null));
check('متن ثبت با فاصله و دقت', str_contains((string) PositionAudit::text(1760000000000, 33.6, 46.4, $hundredNorth, 46.4, 4.2),
    'کاربر ۱۰۰ متر از نقطه (دقت ±۴ متر)'));
check('متن ثبت بی موقعیت دقیق کاربر', str_contains((string) PositionAudit::text(1760000000000, 33.6, 46.4, null, null, null),
    'در دست نبود'));
equals('داده قدیمی بی زمان ثبت، متنی ندارد', null, PositionAudit::text(null, 33.6, 46.4, null, null, null));
$withAt = $line['payload'];
$withAt['poles'][0] += ['lat' => 33.6, 'lon' => 46.4601, 'at' => 1760000000000, 'olat' => 33.6, 'olon' => 46.46, 'oacc' => 5];
check('جدول تیرها زمان و فاصله ثبت را دارد', str_contains(FieldLine::poleTable($withAt)[0][6], 'متر از نقطه'));
$svg = FieldLine::svg($line['payload']);
check('نمودار سه خط دارد', substr_count($svg, '<polyline') === 3);
check('بازه پرافت سایه می‌خورد', str_contains($svg, '#fee2e2'));
$burnRows = FieldPresenter::rows(Field::KIND_BURN, ['equipment' => ['nh_fuse' => 3], 'outage' => true, 'team' => []]);
check('سوختگی: تجهیز با تعداد', in_array(['تجهیزات سوخته', 'فیوز کاردی (فیدر) × ۳'], $burnRows, true)
    || in_array(['تجهیزات سوخته', FieldLabels::EQUIPMENT['nh_fuse'] . ' × ۳'], $burnRows, true));
check('سوختگی: قطع برق', in_array(['برق مشترکین قطع است', 'بله'], $burnRows, true));

// نسخه ب: اکیپ، فیدر فشار متوسط، انشعاب تکفاز و سه فاز، مشترکان نامتعارف.
$crew = ['lead' => ['id' => '3', 'name' => 'رضا کریمی'], 'lineman' => ['name' => 'علی نوری'], 'vehicle' => '۱۲ب۳۴۵'];
$v2 = ['payload' => ['v' => 2, 'crew' => $crew] + $line['payload']] + $line;
equals('تیربه‌تیر نسخه ۲ با اکیپ پذیرفته می‌شود', null, Field::problem($v2, []));
check('نسخه ۲ بی‌سیم‌بان رد می‌شود', Field::problem(['payload' => ['crew' => ['lead' => $crew['lead']]] + $v2['payload']] + $v2, []) !== null);
check('آمپرگیری تابلو نسخه ۲ بی‌اکیپ رد می‌شود', Field::problem(['payload' => ['v' => 2]] + $feeder, []) !== null);
equals('آمپرگیری تابلو نسخه قدیم بی‌اکیپ هم پذیرفته می‌شود', null, Field::problem(['payload' => []] + $feeder, []));
$house = ['phase' => 3, 'amps' => ['r' => 30, 's' => 45, 't' => 12], 'lat' => 33.61, 'lon' => 46.41, 'name' => 'ساختمان نور',
          'postal_codes' => ['6931234567'], 'file_number' => '12', 'abnormal' => true, 'amp_photos' => [1, 2], 'amp_videos' => [3]];
$withSub = $v2;
$withSub['payload']['poles'][0] += ['b1' => 1, 'b3' => 1, 'branches' => 2, 'subscribers' => [$house]];
$withSub['payload']['abnormal_a'] = ['single' => 25, 'three' => 40];
equals('مشترک نامتعارف کامل پذیرفته می‌شود', null, Field::problem($withSub, []));
$bad = static function (array $change) use ($withSub): ?string {
    $copy = $withSub;
    $copy['payload']['poles'][0]['subscribers'][0] = $change + $copy['payload']['poles'][0]['subscribers'][0];
    return Field::problem($copy, []);
};
check('مشترک بی‌موقعیت منزل رد می‌شود', $bad(['lat' => null]) !== null);
check('کد پستی نه‌رقمی رد می‌شود', $bad(['postal_codes' => ['693123456']]) !== null);
check('بیش از ۲۰ کد پستی رد می‌شود', $bad(['postal_codes' => array_fill(0, 21, '6931234567')]) !== null);
$crowded = $withSub;
$crowded['payload']['poles'][0]['subscribers'] = [$house, $house, $house];
check('مشترک بیش از انشعاب رد می‌شود', Field::problem($crowded, []) !== null);
$subs = FieldSubscribers::table($withSub['payload']);
equals('جدول مشترکان یک ردیف', 1, count($subs));
equals('آمپر سه فاز مشترک', 'سه فاز R ۳۰ / S ۴۵ / T ۱۲', $subs[0][3]);
equals('داوری گوشی نشان داده می‌شود', 'نامتعارف', $subs[0][4]);
equals('تعداد عکس و فیلم مشترک', 'عکس آمپراژ ۲، فیلم آمپراژ ۱', $subs[0][9]);
equals('حد مجاز روز ارسال', 'حد مجاز: تکفاز ۲۵ آمپر، سه فاز ۴۰ آمپر هر فاز', FieldSubscribers::limitsText($withSub['payload']));
equals('انشعاب تکفاز و سه فاز در جدول تیرها', 'تکفاز ۱، سه فاز ۱ · ۱ مشترک نامتعارف', FieldLine::poleTable($withSub['payload'])[0][4]);
equals('انشعاب داده قدیمی همان عدد', '۳', FieldLine::poleTable(['poles' => [['branches' => 3]]])[0][4]);
$lineRows = FieldPresenter::rows(Field::KIND_LINE, $withSub['payload'] + ['mv_feeder' => ['name' => 'فیدر ۲۰ کیلوولت چوار']]);
check('ردیف مسئول اکیپ', in_array(['مسئول اکیپ', 'رضا کریمی'], $lineRows, true));
check('ردیف پلاک خودرو', in_array(['پلاک خودرو', '۱۲ب۳۴۵'], $lineRows, true));
check('ردیف فیدر فشار متوسط', in_array(['فیدر فشار متوسط', 'فیدر ۲۰ کیلوولت چوار'], $lineRows, true));
check('ترموویژن فیدر فشار متوسط دارد', in_array(['فیدر فشار متوسط', 'چوار ۲'],
    FieldPresenter::rows(Field::KIND_THERMAL, ['mv_feeder' => ['name' => 'چوار ۲']]), true));

// نسخه ج: نقش‌های تازه اکیپ و زمان کار.
$crypto = ['kind' => Field::KIND_CRYPTO, 'payload' => ['v' => 2, 'crew' => ['lead' => ['name' => 'الف'], 'lineman' => ['name' => 'ب'],
    'crypto' => ['name' => 'مجری'], 'security' => ['name' => 'حراست']]]] + $report;
equals('گزارش رمزارز نسخه ۲ با اکیپ پذیرفته می‌شود', null, Field::problem($crypto, []));
check('گزارش رمزارز نسخه ۲ بی‌سیم‌بان رد می‌شود',
    Field::problem(['payload' => ['v' => 2, 'crew' => ['lead' => ['name' => 'الف']]]] + $crypto, []) !== null);
equals('گزارش رمزارز قدیمی بی‌اکیپ پذیرفته می‌ماند', null, Field::problem(['payload' => []] + $crypto, []));
check('ترموویژن نسخه ۲ بی‌اکیپ رد می‌شود', Field::problem(['payload' => ['v' => 2]] + $thermal, [$frame]) !== null);
check('ردیف مجری رمزارز', in_array(['مجری رمزارز', 'مجری'], FieldPresenter::rows(Field::KIND_CRYPTO, $crypto['payload']), true));
$tehran = static fn (string $local): int => (new DateTimeImmutable($local, new DateTimeZone('Asia/Tehran')))->getTimestamp() * 1000;
$sunday = $tehran('2026-10-18 09:30'); // یکشنبه ۲۶ مهر ۱۴۰۵
$calendar = new CrewCalendar(420, 870, ['1405-07-27' => 'تعطیلی آزمون']);
equals('روز شمسی به وقت ایران', '1405-07-26', CrewCalendar::day($sunday));
equals('ساعت ۹:۳۰ صبح است', 'morning', CrewCalendar::bucket($sunday));
equals('ساعت ۱۲ ظهر است', 'noon', CrewCalendar::bucket($tehran('2026-10-18 12:00')));
equals('ساعت ۱:۲۰ بامداد نیمه‌شب است', 'midnight', CrewCalendar::bucket($tehran('2026-10-18 01:20')));
check('یکشنبه ۹:۳۰ اداری است', !$calendar->overtime($sunday));
check('یکشنبه ۱۶:۰۰ اضافه‌کار است', $calendar->overtime($tehran('2026-10-18 16:00')));
check('پنجشنبه اضافه‌کار است', $calendar->overtime($tehran('2026-10-22 10:00')));
equals('جمعه تعطیل است', 'جمعه', $calendar->holiday($tehran('2026-10-23 10:00')));
equals('تعطیلی نوشته‌شده مدیر', 'تعطیلی آزمون', $calendar->holiday($tehran('2026-10-19 10:00')));
equals('نوروز تعطیل ثابت است', 'نوروز', $calendar->holiday($tehran('2027-03-22 10:00')));
equals('۱۳:۰۰ تا ۱۶:۳۰ یکشنبه: دو ساعت اضافه‌کار', 2.0, $calendar->overtimeHours($tehran('2026-10-18 13:00'), $tehran('2026-10-18 16:30')));
equals('سه ساعت روز جمعه همه اضافه‌کار', 3.0, $calendar->overtimeHours($tehran('2026-10-23 08:00'), $tehran('2026-10-23 11:00')));

// نقشه آبادی‌ها: یک فایل TKGEO کوچک با شهر ایلام و یک شهرستان.
$e6 = static fn (float $v): string => pack('N', (int) round($v * 1e6) & 0xFFFFFFFF);
$geoBytes = 'TKGEO' . chr(1) . $e6(33.0) . $e6(46.0) . $e6(34.0) . $e6(47.0)
    . pack('N', 2) . pack('n', strlen('ایلام')) . 'ایلام' . pack('n', strlen('شهرستان ایلام')) . 'شهرستان ایلام'
    . pack('N', 1) . pack('N', 0) . chr(0) . $e6(33.6379) . $e6(46.4203)
    . pack('N', 0)
    . pack('N', 1) . pack('N', 1) . chr(5) . pack('N', 1) . chr(1) . pack('N', 3)
    . $e6(33.5) . $e6(46.3) . $e6(33.8) . $e6(46.3) . $e6(33.8) . $e6(46.6);
$geo = GeoPlaces::parse($geoBytes);
check('فایل آبادی‌ها خوانده می‌شود', $geo !== null && count($geo->places) === 1 && count($geo->counties) === 1);
check('مرکز ایلام درون شهر است', $geo->locate(33.64, 46.42)['outside'] === false);
check('۴۰ کیلومتر دورتر بیرون از شهر است', $geo->locate(33.30, 46.80)['outside'] === true);
equals('فایل خراب تهی است', null, GeoPlaces::parse('TKGEO' . chr(9)));

// یک روز کار: تابلو، تیربه‌تیر از همان تابلو، ترموویژن تیرها، و یک بازرسی رمزارز بیرون از شهر.
$crewAB = ['lead' => ['id' => 'm1', 'name' => 'رضا'], 'lineman' => ['name' => 'علی']];
$t = static fn (string $hm): int => $tehran('2026-10-18 ' . $hm);
$ph = ['r' => 10, 's' => 11, 't' => 12];
$items = [
    ['id' => 'fd', 'kind' => Field::KIND_FEEDER, 'tracking_code' => 'FD-1', 'plate' => 'P-100', 'latitude' => 33.6379,
     'longitude' => 46.4203, 'position_at' => $t('09:00'), 'created_at' => $t('08:55'), 'payload' => ['v' => 2, 'crew' => $crewAB]],
    ['id' => 'ft', 'kind' => Field::KIND_LINE, 'tracking_code' => 'FT-1', 'plate' => 'p100', 'latitude' => 33.6380,
     'longitude' => 46.4204, 'position_at' => $t('09:20'), 'created_at' => $t('09:15'), 'payload' => ['v' => 2, 'crew' => $crewAB,
        'start' => $ph, 'poles' => [
            ['lat' => 33.639, 'lon' => 46.421, 'at' => $t('09:30'), 'state' => 'measured', 'phases' => $ph],
            ['lat' => 33.640, 'lon' => 46.422, 'at' => $t('09:40'), 'state' => 'measured', 'phases' => $ph, 'subscribers' => [[], []]],
            ['state' => 'not_measured', 'reason' => 'ارتفاع'],
            ['lat' => 33.641, 'lon' => 46.423, 'at' => $t('09:50'), 'state' => 'not_measured', 'reason' => 'باران'],
        ]]],
    ['id' => 'tv', 'kind' => Field::KIND_THERMAL, 'tracking_code' => 'TV-1', 'plate' => null, 'latitude' => null, 'longitude' => null,
     'position_at' => null, 'created_at' => $t('09:58'), 'payload' => ['v' => 2, 'crew' => $crewAB], 'frames' => [
        ['captured_at' => $t('10:05'), 'latitude' => 33.639, 'longitude' => 46.421, 'asset_type' => Field::ASSET_POLE, 'plate' => '7781'],
        ['captured_at' => $t('10:00'), 'latitude' => 33.639, 'longitude' => 46.421, 'asset_type' => Field::ASSET_POLE, 'plate' => '7781'],
     ]],
    ['id' => 'rz', 'kind' => Field::KIND_CRYPTO, 'tracking_code' => 'RZ-1', 'plate' => '', 'latitude' => 33.30, 'longitude' => 46.80,
     'position_at' => $t('16:30'), 'created_at' => $t('16:20'),
     'payload' => ['v' => 2, 'crew' => ['lead' => $crewAB['lead'], 'lineman' => $crewAB['lineman'], 'crypto' => ['id' => 'm3', 'name' => 'مجری']]]],
];
$work = CrewDays::build($items, $calendar, $geo, $t('00:00'), $t('23:59'));
$byKey = array_column($work, null, 'key');
$lead = $byKey['id:m1']['total'] ?? [];
equals('مسئول اکیپ: دو بازدید (تابلو و رمزارز)', 2, $lead['visits'] ?? null);
equals('مسئول اکیپ: شش بازرسی', 6, $lead['inspections'] ?? null);
equals('تفکیک بازرسی‌ها', ['panel' => 2, 'pole' => 2, 'thermal' => 1, 'crypto' => 1, 'illegal' => 0], $lead['types'] ?? null);
equals('مشترکان نامتعارف', 2, $lead['subscribers'] ?? null);
equals('بازه‌های روز', ['midnight' => 0, 'morning' => 5, 'noon' => 0, 'afternoon' => 1, 'night' => 0], $lead['buckets'] ?? null);
equals('اضافه‌کار ۹ تا ۱۶:۳۰ یکشنبه', 2.0, $lead['overtime'] ?? null);
equals('یک بازرسی بیرون از شهر', 1, $lead['outside'] ?? null);
equals('شروع کار ساعت ۹', 540, $lead['avg_start'] ?? null);
check('مسافت پیموده‌شده حساب شده', ($lead['km'] ?? 0) > 30);
equals('سیم‌بان بی‌شناسه با نامش شمرده می‌شود', 6, $byKey['name:علی']['total']['inspections'] ?? null);
equals('مجری رمزارز فقط کار خودش را دارد', [1, 1], [$byKey['id:m3']['total']['visits'] ?? null, $byKey['id:m3']['total']['inspections'] ?? null]);
$visit = $byKey['id:m1']['days'][0]['visit_list'][0] ?? [];
equals('بازدید تابلو با ترموویژن تیرهایش یکی است', [5, 'P-100'], [$visit['inspections'] ?? null, $visit['label'] ?? null]);
equals('همه کار در یک روز', '1405-07-26', $byKey['id:m1']['days'][0]['day'] ?? null);
equals('ساعت به رقم فارسی', '۰۹:۰۵', CrewDays::clock(545));
equals('پلاک یکسان‌شده', 'P100', CrewWork::key('p-۱۰۰'));

// ---------------------------------------------------------------------------
echo "\n";
// ---------------------------------------------------------------------------
echo "\n— سوابق بازرسی —\n";
require_once __DIR__ . '/../api/lib/FieldHistory.php';
equals('پلاک یکسان: رقم فارسی، فاصله و خط تیره', ['77100', '77100', 'P5521'],
    [FieldReadings::key('۷۷-۱۰۰'), FieldReadings::key(' 77 100 '), FieldReadings::key('p-5521')]);
check('افزایش ۶۰٪ و ۶۰ آمپر نامتعارف', FieldHistory::isRise(160.0, 100.0, 102.5, 30, 5));
check('افزایش ۵٪ عادی', !FieldHistory::isRise(105.0, 100.0, 100.0, 30, 5));
check('بار کم: ۲ به ۴ آمپر زیر کف آمپر است', !FieldHistory::isRise(4.0, 2.0, 2.0, 30, 5));
check('نسبت به میانگین، حتی اگر بار قبل هم بالا بود', FieldHistory::isRise(150.0, 145.0, 100.0, 30, 5));
check('بی سابقه، نامتعارف نیست', !FieldHistory::isRise(150.0, null, null, 30, 5));
$feederItem = ['kind' => Field::KIND_FEEDER, 'plate' => '۷۷-۱۰۰', 'created_at' => 1000, 'position_at' => 2000,
    'payload' => json_encode(['main' => ['r' => 10, 's' => 11, 't' => 12], 'feeders' => [
        'A' => ['state' => 'measured', 'phases' => ['r' => 5]], 'B' => ['state' => 'not_measured', 'reason' => 'قفل']]])];
$readings = FieldReadings::extract($feederItem);
equals('تابلو: کلید کل و فیدر اندازه‌گرفته، نه فیدر بی‌خوانش', [['main', 10.0, 2000], ['A', 5.0, 2000]],
    array_map(static fn ($r) => [$r['part'], $r['r'], $r['measured_at']], $readings));
$lineItem = ['kind' => Field::KIND_LINE, 'plate' => 'T1', 'created_at' => 1000, 'payload' => json_encode([
    'feeder' => 'other', 'start' => ['r' => 50], 'poles' => [
        ['plate' => '۱۲۳', 'state' => 'measured', 'phases' => ['r' => 7, 's' => 8, 't' => 9], 'neutral' => 1, 'at' => 3000],
        ['plate' => '', 'state' => 'measured', 'phases' => ['r' => 1]],
        ['plate' => '124', 'state' => 'not_measured', 'reason' => 'دسترسی نبود']]])];
equals('تیربه‌تیر: فقط تیر پلاک‌دار اندازه‌گرفته؛ فیدر «سایر» در تابلو نیست', [[0, '123', 'pole', 1.0, 3000]],
    array_map(static fn ($r) => [$r['asset'], $r['plate_key'], $r['part'], $r['n'], $r['measured_at']], FieldReadings::extract($lineItem)));
$thermalItem = ['id' => 'x', 'kind' => Field::KIND_THERMAL, 'created_at' => 1000,
    'payload' => json_encode(['files' => ['f1' => ['temperatures' => ['max' => 71.5, 'r1_max' => 60]]]])];
$thermal = FieldReadings::extract($thermalItem, [
    ['id' => 'f1', 'role' => Field::ROLE_THERMAL, 'asset_type' => Field::ASSET_POLE, 'plate' => '۱۲۳', 'captured_at' => 4000],
    ['id' => 'f2', 'role' => Field::ROLE_THERMAL, 'asset_type' => Field::ASSET_PANEL, 'plate' => 'T1', 'captured_at' => null],
    ['id' => 'f3', 'role' => Field::ROLE_THERMAL_EXIF, 'asset_type' => Field::ASSET_POLE, 'plate' => '۱۲۳', 'captured_at' => 4000]]);
equals('ترموویژن: هر تصویر اصلی، با دما اگر وارد شده و بی دما اگر نه', [['f1', 71.5, 0], ['f2', null, 1]],
    array_map(static fn ($r) => [$r['file_id'], $r['temp_max'], $r['asset']], $thermal));

echo "$passed تست موفق";
if ($failed > 0) {
    echo "، $failed تست ناموفق\n";
    exit(1);
}
echo "، بدون خطا\n";
