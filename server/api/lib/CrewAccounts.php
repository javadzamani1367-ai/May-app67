<?php
declare(strict_types=1);

/**
 * عضو اکیپی که کاربری خودش را دارد: با ساخته شدن کاربری «اکیپ» یک شخص در یک ناحیه،
 * نامش خودکار در فهرست اکیپ همان شهرستان می‌نشیند، و با غیرفعال شدن کاربری از فهرست
 * بیرون می‌رود. عملکرد اکیپ به همین سطر فهرست بسته است، پس هر ناحیه کار خودش را دارد.
 * نام‌های فهرست که کاربری ندارند (کسی که گوشی ندارد) همان‌طور می‌مانند.
 */
final class CrewAccounts
{
    public static function sync(string $userId): void
    {
        $user = Db::one('SELECT id, full_name, role, crew_role, areas, active FROM users WHERE id = ?', [$userId]);
        if ($user === null) {
            return;
        }
        $linked = Db::one('SELECT id FROM crew_members WHERE user_id = ?', [$userId]);
        $crew = (int) $user['role'] === Auth::ROLE_FIELD && $user['crew_role'] !== null
            && isset(FieldRoster::ROLE_NAMES[(int) $user['crew_role']]);
        if (!$crew) {
            if ($linked !== null) {
                Db::run('UPDATE crew_members SET active = 0 WHERE id = ?', [$linked['id']]);
            }
            return;
        }
        $area = Areas::parse($user['areas'] ?? '')[0] ?? null;
        $county = $area === null ? null : (Areas::NAMES[$area] ?? null);
        if ($linked === null) {
            Db::run('INSERT INTO crew_members (id, full_name, role, county, active, created_at, user_id) VALUES (?, ?, ?, ?, ?, ?, ?)',
                [Db::uuid(), $user['full_name'], (int) $user['crew_role'], $county, (int) $user['active'], Db::now(), $userId]);
            return;
        }
        Db::run('UPDATE crew_members SET full_name = ?, role = ?, county = ?, active = ? WHERE id = ?',
            [$user['full_name'], (int) $user['crew_role'], $county, (int) $user['active'], $linked['id']]);
    }
}
