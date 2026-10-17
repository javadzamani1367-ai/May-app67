<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require(CASE_ROLES);

// تصویر، فیلم یا مدرک یک پرونده بازدید، با شناسه سطر، فقط برای مدیر واردشده.
// عکس در صفحه باز می‌شود؛ بقیه، و هر چه با download=1 خواسته شود، دانلود.
$id = (string) ($_GET['id'] ?? '');
$kind = (string) ($_GET['kind'] ?? '');
$row = !Field::isUuid($id) ? null : match ($kind) {
    'media' => Db::one('SELECT m.file_path, m.type, m.captured_at, r.area_code FROM media m JOIN reports r ON r.id = m.report_id WHERE m.id = ?', [$id]),
    'attachment' => Db::one('SELECT a.file_path, a.mime_type, a.title, r.area_code FROM attachments a JOIN reports r ON r.id = a.report_id WHERE a.id = ?', [$id]),
    default => null,
};
$path = $row === null || !admin_scope()->canSeeReport($row) ? null : Storage::resolve((string) $row['file_path']);
if ($path === null || !is_file($path)) {
    http_response_code(404);
    exit('فایل روی سرور نیست.');
}
$extension = strtolower(pathinfo($path, PATHINFO_EXTENSION));
$types = ['jpg' => 'image/jpeg', 'jpeg' => 'image/jpeg', 'png' => 'image/png', 'mp4' => 'video/mp4', 'pdf' => 'application/pdf'];
$mime = $types[$extension] ?? 'application/octet-stream';
$inline = !isset($_GET['download']) && str_starts_with($mime, 'image/');
$name = $kind === 'attachment' && trim((string) $row['title']) !== ''
    ? preg_replace('/[\\\\\/:*?"<>|\r\n]/u', '', (string) $row['title']) : $id;
header('Content-Type: ' . ($inline ? $mime : 'application/octet-stream'));
header('Content-Length: ' . filesize($path));
header('Content-Disposition: ' . ($inline ? 'inline' : 'attachment') . "; filename*=UTF-8''"
    . rawurlencode($name . '.' . $extension));
header('X-Content-Type-Options: nosniff');
header('Cache-Control: private, max-age=3600');
readfile($path);
