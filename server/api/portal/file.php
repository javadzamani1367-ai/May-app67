<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$user = portal_require_user();
$id = (string) ($_GET['id'] ?? '');
$kind = (string) ($_GET['kind'] ?? '');

// فایل‌ها بیرون از ریشه وب هستند و فقط از همین‌جا بیرون می‌روند، بعد از
// اینکه ثابت شد این فایل به ارسالی مربوط است که برای واحد همین کاربر رفته.
$row = match ($kind) {
    'media' => Db::one(
        'SELECT m.file_path, m.caption AS name FROM media m
         JOIN dispatches d ON d.report_id = m.report_id
         WHERE m.id = ? AND d.unit = ? LIMIT 1',
        [$id, (int) $user['unit']]
    ),
    'attachment' => Db::one(
        'SELECT a.file_path, a.title AS name FROM attachments a
         JOIN dispatches d ON d.report_id = a.report_id
         WHERE a.id = ? AND d.unit = ? LIMIT 1',
        [$id, (int) $user['unit']]
    ),
    'response' => Db::one(
        'SELECT f.file_path, f.file_name AS name FROM response_files f
         JOIN dispatch_responses r ON r.id = f.response_id
         JOIN dispatches d ON d.id = r.dispatch_id
         WHERE f.id = ? AND d.unit = ? LIMIT 1',
        [$id, (int) $user['unit']]
    ),
    default => null,
};

if ($row === null) {
    http_response_code(404);
    exit('فایل پیدا نشد.');
}

$path = Storage::resolve((string) $row['file_path']);
if ($path === null || !is_file($path)) {
    http_response_code(404);
    exit('فایل روی سرور نیست.');
}

$name = trim((string) ($row['name'] ?? '')) ?: basename($path);
$name = preg_replace('/[\r\n"]/', '', $name) . '.' . pathinfo($path, PATHINFO_EXTENSION);

header('Content-Type: application/octet-stream');
header('Content-Length: ' . filesize($path));
header('Content-Disposition: attachment; filename="' . $name . '"');
header('X-Content-Type-Options: nosniff');
readfile($path);
