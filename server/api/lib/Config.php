<?php
declare(strict_types=1);

/** تنظیمات، یک بار خوانده و در حافظه نگه داشته می‌شود. */
final class Config
{
    private static ?array $values = null;

    public static function all(): array
    {
        if (self::$values === null) {
            $path = dirname(__DIR__) . '/config.php';
            if (!is_file($path)) {
                Response::fail(500, 'config_missing', 'فایل config.php ساخته نشده است.');
            }
            self::$values = require $path;
        }
        return self::$values;
    }

    public static function get(string $key, $fallback = null)
    {
        $all = self::all();
        return array_key_exists($key, $all) ? $all[$key] : $fallback;
    }
}
