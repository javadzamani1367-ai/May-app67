<?php
declare(strict_types=1);

/**
 * قفل سال: سطرهای سالی که مدیر بسته، دیگر نه ویرایش می‌شوند، نه حذف، نه با فایل
 * تازه عوض می‌شوند؛ تا گزارشی که برای توانیر رفته با عدد دیگری دوباره ساخته نشود.
 * فقط مدیر قفل را می‌زند و برمی‌دارد، و هر دو در تاریخچه تغییرات می‌مانند.
 */
final class CenterLocks
{
    /** @var array<int, true>|null */
    private static ?array $years = null;

    /** @return array<int, true> */
    public static function years(): array
    {
        if (self::$years === null) {
            self::$years = [];
            foreach (Db::all('SELECT year FROM center_locks') as $row) {
                self::$years[(int) $row['year']] = true;
            }
        }
        return self::$years;
    }

    public static function locked(int $year): bool
    {
        return isset(self::years()[$year]);
    }

    public static function set(int $year, bool $lock, array $user): void
    {
        if ($lock) {
            Db::run('INSERT IGNORE INTO center_locks (year, locked_by, locked_at) VALUES (?, ?, ?)', [$year, $user['id'], Db::now()]);
        } else {
            Db::run('DELETE FROM center_locks WHERE year = ?', [$year]);
        }
        self::$years = null;
    }

    /** سال‌هایی که سطر دارند، با وضعیت قفل. @return list<array{year:int, rows:int, locked:bool}> */
    public static function overview(): array
    {
        $locked = self::years();
        return array_map(static fn (array $r) => ['year' => (int) $r['year'], 'rows' => (int) $r['n'],
            'locked' => isset($locked[(int) $r['year']])],
            Db::all('SELECT year, COUNT(*) AS n FROM center_rows WHERE deleted_at IS NULL GROUP BY year ORDER BY year DESC'));
    }
}
