<?php
declare(strict_types=1);

/**
 * به‌روزرسانی خودکار پایگاه داده.
 *
 * روی هاست اشتراکی، هر تغییر اسکیما یعنی کسی باید phpMyAdmin را باز کند و
 * فایلی را دوباره وارد کند — کاری که فراموش می‌شود و نتیجه‌اش سرویسی است که
 * با جدولی که وجود ندارد خطا می‌دهد. اینجا هر درخواست اول یک عدد را می‌خواند
 * و اگر پایگاه داده عقب بود، گام‌های جاافتاده را خودش اجرا می‌کند.
 *
 * هر گام باید تکرارپذیر باشد (IF NOT EXISTS، یا بررسی ستون پیش از افزودن):
 * نصب تازه همه جدول‌ها را از schema.sql گرفته و نسخه‌اش را هم همان‌جا ثبت
 * کرده، ولی اگر نصبی نسخه را نداشته باشد، اجرای دوباره گام‌ها نباید چیزی را
 * خراب کند.
 */
final class Migrations
{
    /**
     * هر کلید یک نسخه؛ مقدار، کارهایی که پایگاه داده را به آن نسخه می‌رسانند:
     * یک دستور SQL، یا تابعی برای کاری که SQL خام نمی‌تواند تکرارپذیر انجامش
     * دهد (افزودن ستون فقط وقتی هنوز نیست).
     *
     * @return array<int, array<int, string|callable>>
     */
    private static function steps(): array
    {
        return [
            1 => [
                // قفل ورود ناموفق: کلید، هش کد کاربری یا نشانی شبکه است.
                "CREATE TABLE IF NOT EXISTS login_attempts (
                   scope        VARCHAR(80) NOT NULL PRIMARY KEY,
                   failures     INT         NOT NULL DEFAULT 0,
                   first_at     BIGINT      NOT NULL,
                   locked_until BIGINT      NOT NULL DEFAULT 0
                 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
            ],
            // اپ بازرسی میدانی: مجوز کاربر، موردها، فایل‌ها، تاریخچه، کدها.
            2 => array_merge(
                [static fn () => self::addColumn('users', 'permissions', 'TINYINT NOT NULL DEFAULT 0')],
                FieldSchema::TABLES
            ),
            // ارجاع: به کدام کارشناس، و مهلت نتیجه. «گذشتن مهلت» ذخیره نمی‌شود.
            3 => [
                static fn () => self::addColumn('field_items', 'assigned_to', 'CHAR(36) NULL'),
                static fn () => self::addColumn('field_items', 'due_at', 'BIGINT NULL'),
                static fn () => self::addIndex('field_items', 'idx_field_items_assigned', 'assigned_to'),
            ],
        ];
    }

    private static function addColumn(string $table, string $column, string $definition): void
    {
        $exists = Db::one(
            'SELECT 1 AS present FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?',
            [$table, $column]
        );
        if ($exists === null) {
            Db::run("ALTER TABLE `$table` ADD COLUMN `$column` $definition");
        }
    }

    private static function addIndex(string $table, string $index, string $columns): void
    {
        $exists = Db::one(
            'SELECT 1 AS present FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?',
            [$table, $index]
        );
        if ($exists === null) {
            Db::run("ALTER TABLE `$table` ADD KEY `$index` ($columns)");
        }
    }

    public static function latest(): int
    {
        return max(array_keys(self::steps()));
    }

    public static function ensure(): void
    {
        $current = self::current();
        if ($current >= self::latest()) {
            return;
        }
        // دو درخواست هم‌زمان نباید یک گام را دو بار اجرا کنند.
        $lock = Db::one("SELECT GET_LOCK('inspection_migrate', 20) AS got");
        if ((int) ($lock['got'] ?? 0) !== 1) {
            Response::fail(503, 'upgrading', 'سرور در حال به‌روزرسانی است. چند لحظه دیگر دوباره امتحان کنید.');
        }
        try {
            $current = self::current();
            foreach (self::steps() as $version => $statements) {
                if ($version <= $current) {
                    continue;
                }
                foreach ($statements as $statement) {
                    is_callable($statement) ? $statement() : Db::run($statement);
                }
                Db::run('UPDATE schema_meta SET version = ? WHERE id = 1', [$version]);
            }
        } finally {
            Db::run("SELECT RELEASE_LOCK('inspection_migrate')");
        }
    }

    /** نسخه فعلی پایگاه داده؛ نصبی که هنوز جدول نسخه ندارد، نسخه صفر است. */
    private static function current(): int
    {
        try {
            $row = Db::one('SELECT version FROM schema_meta WHERE id = 1');
        } catch (PDOException $e) {
            Db::run(
                'CREATE TABLE IF NOT EXISTS schema_meta (
                   id      TINYINT NOT NULL PRIMARY KEY,
                   version INT     NOT NULL
                 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci'
            );
            $row = null;
        }
        if ($row === null) {
            Db::run('INSERT IGNORE INTO schema_meta (id, version) VALUES (1, 0)');
            return 0;
        }
        return (int) $row['version'];
    }
}
