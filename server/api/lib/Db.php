<?php
declare(strict_types=1);

/**
 * یک اتصال PDO برای کل درخواست. بدون هیچ کتابخانه بیرونی، چون روی هاست
 * اشتراکی composer در دسترس نیست و نباید باشد.
 */
final class Db
{
    private static ?PDO $pdo = null;

    public static function conn(): PDO
    {
        if (self::$pdo === null) {
            $dsn = sprintf(
                'mysql:host=%s;dbname=%s;charset=utf8mb4',
                Config::get('db_host', 'localhost'),
                Config::get('db_name', '')
            );
            try {
                self::$pdo = new PDO($dsn, Config::get('db_user', ''), Config::get('db_pass', ''), [
                    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    // شبیه‌سازی خاموش: پارامترها واقعاً به سرور می‌روند و
                    // تزریق SQL از این مسیر ممکن نیست.
                    PDO::ATTR_EMULATE_PREPARES => false,
                ]);
            } catch (PDOException $e) {
                Response::fail(500, 'db_unavailable', 'اتصال به پایگاه داده برقرار نشد.');
            }
        }
        return self::$pdo;
    }

    public static function run(string $sql, array $params = []): PDOStatement
    {
        $statement = self::conn()->prepare($sql);
        $statement->execute($params);
        return $statement;
    }

    public static function one(string $sql, array $params = []): ?array
    {
        $row = self::run($sql, $params)->fetch();
        return $row === false ? null : $row;
    }

    public static function all(string $sql, array $params = []): array
    {
        return self::run($sql, $params)->fetchAll();
    }

    /** میلی‌ثانیه یونیکس — همان واحدی که گوشی و ویندوز ذخیره می‌کنند. */
    /**
     * نام ستون یا جدول، آماده برای گذاشتن داخل SQL.
     *
     * `row_number` از MariaDB 10.2 و MySQL 8 به بعد کلمه رزرو است و بدون
     * بک‌کوت، پرس‌وجو خطای نحوی می‌دهد. نام‌ها از کد خودمان می‌آیند نه از
     * کاربر، ولی یک کلمه رزرو تازه در نسخه بعدی پایگاه داده همین بلا را سر
     * جای دیگری می‌آورد، پس هر جا فهرست ستون به SQL تبدیل می‌شود از این رد
     * می‌شود.
     */
    public static function col(string $name): string
    {
        if (preg_match('/^[a-z_][a-z0-9_]*$/i', $name) !== 1) {
            throw new InvalidArgumentException('bad identifier');
        }
        return '`' . $name . '`';
    }

    /** @param list<string> $names */
    public static function cols(array $names): string
    {
        return implode(', ', array_map([self::class, 'col'], $names));
    }

    public static function now(): int
    {
        return (int) round(microtime(true) * 1000);
    }

    public static function uuid(): string
    {
        $bytes = random_bytes(16);
        $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
        $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($bytes), 4));
    }
}
