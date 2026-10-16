<?php
declare(strict_types=1);

/**
 * فهرست‌هایی که مدیر برای اپ میدانی تعریف می‌کند: اعضای اکیپ (مسئول، سیم‌بان،
 * راننده) و فیدرهای فشار متوسط. اپ از همین فهرست انتخاب می‌کند تا یک نفر یا یک
 * فیدر با دو املای مختلف دو بار شمرده نشود — گزارش عملکرد هر نفر به همین بسته است.
 */
final class FieldRoster
{
    public const ROLE_LEAD = 0;
    public const ROLE_LINEMAN = 1;
    public const ROLE_DRIVER = 2;
    public const ROLE_NAMES = [self::ROLE_LEAD => 'مسئول اکیپ', self::ROLE_LINEMAN => 'سیم‌بان', self::ROLE_DRIVER => 'راننده'];

    public const TABLES = [
        "CREATE TABLE IF NOT EXISTS crew_members (
           id         CHAR(36)     NOT NULL PRIMARY KEY,
           full_name  VARCHAR(191) NOT NULL,
           role       TINYINT      NOT NULL,
           county     VARCHAR(100) NULL,
           active     TINYINT      NOT NULL DEFAULT 1,
           created_at BIGINT       NOT NULL,
           KEY idx_crew_role (role, active)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        "CREATE TABLE IF NOT EXISTS mv_feeders (
           id         CHAR(36)     NOT NULL PRIMARY KEY,
           name       VARCHAR(191) NOT NULL,
           county     VARCHAR(100) NULL,
           active     TINYINT      NOT NULL DEFAULT 1,
           created_at BIGINT       NOT NULL,
           UNIQUE KEY uq_mv_feeders_name (name, county)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    ];

    /** آنچه گوشی می‌گیرد: فقط فعال‌ها، مرتب به نام. */
    public static function forPhone(): array
    {
        return [
            'crew' => array_map(static fn (array $r): array => [
                'id' => $r['id'], 'name' => $r['full_name'], 'role' => (int) $r['role'], 'county' => $r['county'],
            ], Db::all('SELECT id, full_name, role, county FROM crew_members WHERE active = 1 ORDER BY role, full_name')),
            'mv_feeders' => array_map(static fn (array $r): array => [
                'id' => $r['id'], 'name' => $r['name'], 'county' => $r['county'],
            ], Db::all('SELECT id, name, county FROM mv_feeders WHERE active = 1 ORDER BY county, name')),
        ];
    }

    /** همه، برای صفحه مدیریت. */
    public static function members(): array
    {
        return Db::all('SELECT * FROM crew_members ORDER BY active DESC, role, full_name');
    }

    public static function feeders(): array
    {
        return Db::all('SELECT * FROM mv_feeders ORDER BY active DESC, county, name');
    }

    /** یک عضو تازه یا ویرایش؛ خطا به فارسی، یا تهی. */
    public static function saveMember(?string $id, string $name, int $role, string $county, bool $active): ?string
    {
        $name = trim(preg_replace('/\s+/u', ' ', $name) ?? '');
        if ($name === '' || mb_strlen($name) > 191) {
            return 'نام عضو اکیپ را بنویسید.';
        }
        if (!isset(self::ROLE_NAMES[$role])) {
            return 'نقش عضو اکیپ را انتخاب کنید.';
        }
        $county = mb_substr(trim($county), 0, 100);
        if ($id !== null && Field::isUuid($id)) {
            Db::run('UPDATE crew_members SET full_name = ?, role = ?, county = ?, active = ? WHERE id = ?',
                [$name, $role, $county === '' ? null : $county, $active ? 1 : 0, $id]);
        } else {
            Db::run('INSERT INTO crew_members (id, full_name, role, county, active, created_at) VALUES (?, ?, ?, ?, 1, ?)',
                [Db::uuid(), $name, $role, $county === '' ? null : $county, Db::now()]);
        }
        return null;
    }

    public static function saveFeeder(?string $id, string $name, string $county, bool $active): ?string
    {
        $name = trim(preg_replace('/\s+/u', ' ', $name) ?? '');
        if ($name === '' || mb_strlen($name) > 191) {
            return 'نام فیدر را بنویسید.';
        }
        $county = mb_substr(trim($county), 0, 100);
        $duplicate = Db::one('SELECT id FROM mv_feeders WHERE name = ? AND COALESCE(county, \'\') = ?', [$name, $county]);
        if ($duplicate !== null && $duplicate['id'] !== $id) {
            return 'این فیدر در همین شهرستان از قبل ثبت شده است.';
        }
        if ($id !== null && Field::isUuid($id)) {
            Db::run('UPDATE mv_feeders SET name = ?, county = ?, active = ? WHERE id = ?',
                [$name, $county === '' ? null : $county, $active ? 1 : 0, $id]);
        } else {
            Db::run('INSERT INTO mv_feeders (id, name, county, active, created_at) VALUES (?, ?, ?, 1, ?)',
                [Db::uuid(), $name, $county === '' ? null : $county, Db::now()]);
        }
        return null;
    }
}
