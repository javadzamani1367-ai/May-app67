<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// فایل با شناسه خواسته می‌شود، نه با مسیر، و فقط بعد از ورود مدیر داده می‌شود.
$id = (string) ($_GET['id'] ?? '');
$file = Field::isUuid($id) ? Db::one('SELECT path, mime, size FROM field_files WHERE id = ? AND complete = 1', [$id]) : null;
$path = $file === null || $file['path'] === null ? null : Storage::resolve((string) $file['path']);
if ($path === null || !is_file($path)) {
    http_response_code(404);
    exit('فایل پیدا نشد.');
}
$mime = (string) $file['mime'];
// فقط عکس، فیلم و صدا در مرورگر باز می‌شوند؛ بقیه دانلود.
$inline = preg_match('#^(image/(jpeg|png)|video/mp4|audio/(mp4|aac))$#', $mime) === 1;
header('Content-Type: ' . ($inline ? $mime : 'application/octet-stream'));
header('Content-Length: ' . filesize($path));
header('Content-Disposition: ' . ($inline ? 'inline' : 'attachment') . '; filename="' . $id . '.' . pathinfo($path, PATHINFO_EXTENSION) . '"');
header('X-Content-Type-Options: nosniff');
header('Cache-Control: private, max-age=3600');
readfile($path);
