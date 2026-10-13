<?php
declare(strict_types=1);

/**
 * آرشیو وب پرونده‌های بازدید در پنل مدیر: فهرست با فیلتر، صفحه پرونده با
 * هفت بخش، فرم رسمی چاپی، Word با جدول و تصویر، اکسل دوبرگه‌ای و آمار.
 *
 * از تست‌های قبلی: $reportId و $mediaId (case_push) و $storage.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $storage
 */

echo "\n— آرشیو وب پرونده‌ها —\n";
// یک JPEG واقعی جای بایت‌های تصادفی آزمون قبلی، تا Word تصویر را جاسازی کند.
$path = $storage . '/' . (string) $pdo->query("SELECT file_path FROM media WHERE id = '$mediaId'")->fetchColumn();
file_put_contents($path, base64_decode('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAADAAQDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDkKKKK8U/TD//Z'));

$jar = tempnam(sys_get_temp_dir(), 'admin-cases');
equals('بی‌ورود، آرشیو باز نمی‌شود', 302, $probe->page('admin/cases.php', $jar)['status']);
equals('بی‌ورود، فایل پرونده داده نمی‌شود', 302, $probe->page("admin/case_file.php?kind=media&id=$mediaId", $jar)['status']);
$probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);

$list = $probe->page('admin/cases.php', $jar)['body'];
check('فهرست، پرونده را با کد و نوع نشان می‌دهد', str_contains($list, 'M-401-050614-482917') && str_contains($list, 'گزارش مردمی'));
check('شاخص‌ها: کیلووات کشف‌شده', str_contains($list, 'کیلووات توان کشف‌شده'));
check('جستجو با رقم فارسی', str_contains($probe->page('admin/cases.php?q=' . urlencode('۴۸۲۹۱۷'), $jar)['body'], 'M-401-050614-482917'));
check('فیلتر نوع، پرونده‌های نوع دیگر را کنار می‌گذارد',
    !str_contains($probe->page('admin/cases.php?type=1', $jar)['body'], 'M-401-050614-482917'));
check('فیلتر تاریخ شمسی', !str_contains($probe->page('admin/cases.php?from=' . urlencode('۱۴۱۰/۰۱/۰۱'), $jar)['body'],
    'M-401-050614-482917'));

$page = $probe->page("admin/case.php?id=$reportId", $jar)['body'];
check('صفحه پرونده هفت بخش را دارد', str_contains($page, '۴ — مشخصات فنی') && str_contains($page, '۷ — شرح بازدید'));
check('ناحیه کنار شهرستان', str_contains($page, 'دره‌شهر — ناحیه ۴۱۰'));
check('توان هر فاز = ولتاژ × جریان', str_contains($page, 'توان فاز R') && str_contains($page, '۷٬۲۹۶ وات'));
check('دستگاه و حاضر', str_contains($page, 'S19') && str_contains($page, 'سرگرد نمونه') && str_contains($page, 'پلیس امنیت'));
check('گالری تصویر', str_contains($page, "case_file.php?kind=media&amp;id=$mediaId"));
equals('پرونده ناموجود ۴۰۴', 404, $probe->page('admin/case.php?id=' . str_repeat('0', 8) . '-0000-0000-0000-' . str_repeat('0', 12), $jar)['status']);

$image = $probe->page("admin/case_file.php?kind=media&id=$mediaId", $jar);
check('تصویر پرونده برای مدیر', $image['status'] === 200 && str_starts_with($image['body'], "\xFF\xD8"));

$print = $probe->page("admin/case_print.php?id=$reportId", $jar)['body'];
check('فرم رسمی چاپی', str_contains($print, 'فرم گزارش بازدید مراکز استخراج رمزارز') && str_contains($print, 'پیوست تصاویر'));
check('فرم رسمی نام نرم‌افزار ندارد', !str_contains($print, 'توان‌کاو') && !str_contains($print, 'کاراکو'));

$docx = tempnam(sys_get_temp_dir(), 'case') . '.docx';
file_put_contents($docx, $probe->page("admin/case_docx.php?id=$reportId", $jar)['body']);
$zip = new ZipArchive();
check('Word باز می‌شود', $zip->open($docx) === true);
$xml = (string) $zip->getFromName('word/document.xml');
check('Word: جدول دستگاه‌ها و تصویر جاسازی‌شده',
    str_contains($xml, 'S19') && $zip->locateName('word/media/image1.jpg') !== false && str_contains($xml, 'r:embed="rImg1"'));
check('Word: XML سالم', @simplexml_load_string($xml) !== false);
$zip->close();
@unlink($docx);

$xlsx = tempnam(sys_get_temp_dir(), 'cases') . '.xlsx';
file_put_contents($xlsx, $probe->page('admin/cases_export.php', $jar)['body']);
$zip = new ZipArchive();
check('اکسل دو برگه دارد: پرونده‌ها و آمار',
    $zip->open($xlsx) === true && $zip->locateName('xl/worksheets/sheet2.xml') !== false
    && str_contains((string) $zip->getFromName('xl/workbook.xml'), 'آمار'));
check('اکسل: سطر پرونده', str_contains((string) $zip->getFromName('xl/worksheets/sheet1.xml'), 'M-401-050614-482917'));
$zip->close();
@unlink($xlsx);

$stats = $probe->page('admin/case_stats.php', $jar)['body'];
check('آمار: تفکیک نوع، شهرستان و کارشناس', str_contains($stats, 'گزارش مردمی') && str_contains($stats, 'دره‌شهر')
    && str_contains($stats, 'یافته‌های فنی'));
$probe->page('admin/logout.php', $jar);
@unlink($jar);
