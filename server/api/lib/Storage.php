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

    /**
     * فایل را **دقیقاً روی همان مسیر نسبی** گوشی ذخیره می‌کند.
     *
     * قرارداد پروژه این است که مسیر رسانه نسبی و در هر سه انبار یکسان باشد
     * (`media/<report_id>/<millis>.jpg`). آرشیو ویندوز از اول همین کار را
     * می‌کند. اگر سرور مسیر خودش را می‌ساخت، مدیری که پرونده را می‌کشد مسیری
     * می‌گرفت که روی گوشی خودش معنا نداشت، و `portal/file.php` هم که مسیر را
     * از همان ستون می‌خواند باید عوض می‌شد.
     */
    public static function putAt(array $file, string $relative): array
    {
        $relative = self::safeRelative($relative);
        self::guard($file);

        $target = self::root() . '/' . $relative;
        if (!is_dir(dirname($target)) && !mkdir(dirname($target), 0750, true)) {
            Response::fail(500, 'storage_unwritable', 'پوشه ذخیره‌سازی قابل نوشتن نیست.');
        }
        if (!move_uploaded_file((string) $file['tmp_name'], $target)) {
            Response::fail(500, 'storage_failed', 'ذخیره فایل روی سرور ناموفق بود.');
        }

        return ['path' => $relative, 'size' => (int) ($file['size'] ?? 0)];
    }

    /**
     * مسیر نسبی مجاز: فقط داخل پوشه‌های شناخته‌شده، بدون `..` و بدون مسیر
     * مطلق. مسیر از گوشی می‌آید و یک مسیر ساخته‌شده می‌تواند روی هر فایلی
     * بنویسد، پس این بررسی قبل از نوشتن انجام می‌شود نه بعدش.
     */
    public static function safeRelative(string $relative): string
    {
        $clean = str_replace('\\', '/', trim($relative));
        $clean = ltrim($clean, '/');
        $parts = array_values(array_filter(explode('/', $clean), static fn(string $p): bool => $p !== ''));

        $bad = $parts === []
            || in_array('..', $parts, true)
            || in_array('.', $parts, true)
            || !in_array($parts[0], self::ROOTS, true)
            || count($parts) > 6;
        if ($bad) {
            Response::fail(400, 'bad_path', 'مسیر فایل پذیرفته نمی‌شود.');
        }

        $extension = strtolower(pathinfo(end($parts), PATHINFO_EXTENSION));
        if (!in_array($extension, self::ALLOWED, true)) {
            Response::fail(415, 'bad_type', 'این نوع فایل پذیرفته نمی‌شود.');
        }
        return implode('/', $parts);
    }

    /** مسیر نسبی، هرگز مطلق — همان قاعده گوشی و ویندوز. */
    public static function put(array $file, string $folder): array
    {
        self::guard($file);

        // این راه، فایل آمده از مرورگر واحدها را می‌گیرد، پس نوع فایل همین‌جا
        // بررسی می‌شود؛ راه دیگر آن را در `safeRelative()` بررسی می‌کند.
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

    /** خطای بارگذاری و سقف حجم — مشترک بین هر دو راه ذخیره. */
    private static function guard(array $file): void
    {
        $maxBytes = ((int) Config::get('max_upload_mb', 32)) * 1024 * 1024;
        if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
            Response::fail(400, 'upload_failed', 'بارگذاری فایل ناموفق بود.');
        }
        if (($file['size'] ?? 0) > $maxBytes) {
            Response::fail(413, 'too_large', 'حجم فایل از حد مجاز بیشتر است.');
        }
    }

    /** پوشه‌هایی که یک فایل آمده از گوشی اجازه دارد داخلشان بنشیند. */
    private const ROOTS = ['media', 'attachments'];

    private const ALLOWED = [
        'jpg', 'jpeg', 'png', 'webp', 'mp4', 'pdf', 'docx', 'xlsx', 'txt', 'csv', 'zip', 'cvz',
    ];
}
