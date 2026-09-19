<?php
declare(strict_types=1);

/** تنظیمات، یک بار خوانده و در حافظه نگه داشته می‌شود. */
final class Config
{
    private static ?array $values = null;

    public static function all(): array
    {
        if (self::$values === null) {
            $path = self::path();
            if (!is_file($path)) {
                Response::fail(500, 'config_missing', 'فایل config.php ساخته نشده است.');
            }
            self::$values = require $path;
        }
        return self::$values;
    }

    /**
     * مسیر فایل تنظیمات.
     *
     * روی هاست همیشه `api/config.php` است. متغیر محیطی فقط برای تست یکپارچه
     * وجود دارد تا بتواند سرویس را به یک پایگاه داده آزمایشی وصل کند بدون
     * اینکه فایل تنظیمات واقعی را دست بزند. هاست اشتراکی سی‌پنل متغیر محیطی
     * برای PHP ست نمی‌کند، پس این مسیر آنجا هرگز فعال نمی‌شود.
     */
    private static function path(): string
    {
        $override = getenv('INSPECTION_CONFIG');
        if (is_string($override) && $override !== '' && is_file($override)) {
            return $override;
        }
        return dirname(__DIR__) . '/config.php';
    }

    public static function get(string $key, $fallback = null)
    {
        $all = self::all();
        return array_key_exists($key, $all) ? $all[$key] : $fallback;
    }
}
