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

// ---------------------------------------------------------------------------
echo "\n";
echo "$passed تست موفق";
if ($failed > 0) {
    echo "، $failed تست ناموفق\n";
    exit(1);
}
echo "، بدون خطا\n";
