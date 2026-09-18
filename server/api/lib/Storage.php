<?php
declare(strict_types=1);

/**
 * فایل‌های رسانه و مدارک. خارج از ریشه وب نگه داشته می‌شوند و فقط از مسیر
 * دانلود با بررسی نشست بیرون داده می‌شوند — لینک مستقیم به تصویر یک پرونده
 * یعنی کد ملی و نام مالک بدون ورود در دسترس باشد.
 */
final class Storage
{
    public static function root(): string
    {
        return dirname(__DIR__) . '/storage';
    }

    /** مسیر نسبی، هرگز مطلق — همان قاعده گوشی و ویندوز. */
    public static function put(array $file, string $folder): array
    {
        $maxBytes = ((int) Config::get('max_upload_mb', 32)) * 1024 * 1024;
        if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
            Response::fail(400, 'upload_failed', 'بارگذاری فایل ناموفق بود.');
        }
        if (($file['size'] ?? 0) > $maxBytes) {
            Response::fail(413, 'too_large', 'حجم فایل از حد مجاز بیشتر است.');
        }

        $extension = strtolower(pathinfo((string) ($file['name'] ?? ''), PATHINFO_EXTENSION));
        if (!in_array($extension, self::ALLOWED, true)) {
            Response::fail(415, 'bad_type', 'این نوع فایل پذیرفته نمی‌شود.');
        }

        $relative = trim($folder, '/') . '/' . Db::uuid() . '.' . $extension;
        $target = self::root() . '/' . $relative;
        if (!is_dir(dirname($target)) && !mkdir(dirname($target), 0750, true)) {
            Response::fail(500, 'storage_unwritable', 'پوشه ذخیره‌سازی قابل نوشتن نیست.');
        }
        if (!move_uploaded_file((string) $file['tmp_name'], $target)) {
            Response::fail(500, 'storage_failed', 'ذخیره فایل روی سرور ناموفق بود.');
        }

        return ['path' => $relative, 'size' => (int) ($file['size'] ?? 0)];
    }

    /** مسیر امن: هیچ ../ ای اجازه بیرون رفتن از پوشه ذخیره‌سازی را ندارد. */
    public static function resolve(string $relative): ?string
    {
        $path = realpath(self::root() . '/' . $relative);
        $root = realpath(self::root());
        if ($path === false || $root === false || !str_starts_with($path, $root)) {
            return null;
        }
        return $path;
    }

    private const ALLOWED = [
        'jpg', 'jpeg', 'png', 'webp', 'mp4', 'pdf', 'docx', 'xlsx', 'txt', 'csv', 'zip', 'cvz',
    ];
}
