<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require(CASE_ROLES);

// بسته یک ارسال برای شبکه اجتماعی یا انتقال آفلاین: فرم رسمی Word و مدارکی که در
// همان ارسال انتخاب شده، در یک zip. ارسال پیش‌تر در سابقه پرونده ثبت شده است.
$dispatch = Db::one('SELECT * FROM dispatches WHERE id = ?', [(string) ($_GET['dispatch'] ?? '')]);
if ($dispatch === null || !class_exists('ZipArchive')) {
    http_response_code(404);
    exit('بسته پیدا نشد.');
}
$case = admin_case((string) $dispatch['report_id']);
$code = preg_replace('/[^A-Za-z0-9-]/', '', CaseLabels::code($case['report'])) ?: 'case';
$items = json_decode((string) $dispatch['included_items'], true);
$items = is_array($items) ? array_map('strval', $items) : [];

$dir = sys_get_temp_dir();
$docx = tempnam($dir, 'case');
Docx::write($docx, CaseReport::wordBlocks($case));
$zipPath = tempnam($dir, 'bundle');
$zip = new ZipArchive();
$zip->open($zipPath, ZipArchive::OVERWRITE);
$zip->addFile($docx, $code . '.docx');
$n = 0;
foreach (array_merge($case['media'], $case['attachments']) as $file) {
    if (!in_array((string) $file['id'], $items, true)) {
        continue;
    }
    $path = Storage::resolve((string) $file['file_path']);
    if ($path !== null && is_file($path)) {
        $title = preg_replace('/[\\\\\/:*?"<>|\r\n]/u', '', (string) ($file['title'] ?? $file['caption'] ?? ''));
        $zip->addFile($path, sprintf('%02d', ++$n) . ($title !== '' ? '-' . $title : '') . '.' . pathinfo($path, PATHINFO_EXTENSION));
    }
}
$zip->close();
@unlink($docx);
header('Content-Type: application/zip');
header('Content-Disposition: attachment; filename="' . $code . '-' . ((int) $dispatch['unit']) . '.zip"');
header('Content-Length: ' . filesize($zipPath));
header('X-Content-Type-Options: nosniff');
readfile($zipPath);
@unlink($zipPath);
