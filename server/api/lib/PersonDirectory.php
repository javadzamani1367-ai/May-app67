<?php
declare(strict_types=1);

/**
 * فهرست اشخاص و کاربری‌ها، مرتب و بی‌شلوغی: اول ناحیه، داخل هر ناحیه دسته‌های نقش
 * (مجریان، کارشناسان، واحدها، اپ میدانی و اکیپ)، داخل هر دسته نام. کاربری‌های استانی
 * و اشخاصی که هنوز کاربری ندارند جدا می‌آیند. یک جستجو برای نام، کد ملی و کد کاربری.
 */
final class PersonDirectory
{
    /**
     * @return array{areas: list<array<string, mixed>>, province: list<array<string, mixed>>, idle: list<array<string, mixed>>}
     */
    public static function listing(string $query = '', ?string $area = null, ?string $kind = null, bool $inactive = false): array
    {
        $query = Auth::latin(trim($query));
        $like = '%' . $query . '%';
        $rows = Db::all(
            'SELECT u.id AS user_id, u.user_code, u.role, u.unit, u.crew_role, u.areas, u.active, u.permissions,
                    p.id AS person_id, p.full_name, p.national_id, p.number, p.home_area
             FROM users u LEFT JOIN persons p ON p.id = u.person_id
             WHERE (? = \'\' OR p.full_name LIKE ? OR p.national_id LIKE ? OR u.user_code LIKE ? OR u.full_name LIKE ?)
               AND (? = 1 OR u.active = 1)
             ORDER BY p.full_name, u.user_code',
            [$query, $like, $like, $like, $like, $inactive ? 1 : 0]
        );
        $byArea = [];
        $province = [];
        foreach ($rows as $row) {
            $row['kind'] = Persons::kindOf($row);
            $row['kind_name'] = Persons::kindName($row['kind']) ?: (Users::ROLE_NAMES[(int) $row['role']] ?? '');
            if ($kind !== null && $kind !== '' && $row['kind'] !== $kind) {
                continue;
            }
            $own = Areas::parse($row['areas'] ?? '');
            if ($own === []) {
                if ($area === null) {
                    $province[] = self::slim($row);
                }
                continue;
            }
            foreach ($own as $code) {
                if ($area !== null && $code !== $area) {
                    continue;
                }
                $byArea[$code][self::group($row['kind'])][] = self::slim($row);
            }
        }
        $areas = [];
        foreach (array_keys(Areas::NAMES) as $code) {
            $code = (string) $code;
            if (!isset($byArea[$code])) {
                continue;
            }
            $groups = [];
            foreach (array_keys(Persons::GROUPS) + [99 => 'سایر'] as $title) {
                if (isset($byArea[$code][$title])) {
                    $groups[] = ['title' => $title, 'rows' => $byArea[$code][$title]];
                }
            }
            $areas[] = ['code' => $code, 'name' => Areas::label($code), 'count' => array_sum(array_map('count', $byArea[$code])),
                'groups' => $groups];
        }
        $idle = $area !== null || ($kind ?? '') !== '' ? [] : Db::all(
            'SELECT p.id AS person_id, p.full_name, p.national_id, p.number FROM persons p
             WHERE p.active = 1 AND NOT EXISTS (SELECT 1 FROM users u WHERE u.person_id = p.id AND u.active = 1)
               AND (? = \'\' OR p.full_name LIKE ? OR p.national_id LIKE ?) ORDER BY p.full_name LIMIT 300',
            [$query, $like, $like]);
        return ['areas' => $areas, 'province' => $province, 'idle' => $idle];
    }

    private static function group(?string $kind): string
    {
        foreach (Persons::GROUPS as $title => $kinds) {
            if (in_array($kind, $kinds, true)) {
                return $title;
            }
        }
        return 'سایر';
    }

    private static function slim(array $row): array
    {
        return [
            'person_id' => $row['person_id'], 'user_id' => $row['user_id'], 'user_code' => $row['user_code'],
            'name' => $row['full_name'] ?? $row['user_code'], 'national_id' => $row['national_id'],
            'number' => $row['number'] === null ? null : (int) $row['number'], 'kind' => $row['kind'],
            'kind_name' => $row['kind_name'], 'active' => (int) $row['active'] === 1,
            'permissions' => (int) $row['permissions'],
        ];
    }

    /** آنچه فرم‌ها لازم دارند: ناحیه‌ها، نوع‌های کاربری، دسته‌ها و اپ‌ها. */
    public static function catalog(): array
    {
        $kinds = [];
        foreach (Persons::KINDS as $key => $spec) {
            $kinds[] = ['key' => $key, 'name' => $spec['name'], 'field' => $spec['role'] === Auth::ROLE_FIELD];
        }
        return [
            'areas' => array_map(static fn ($c) => ['code' => (string) $c, 'name' => Areas::label((string) $c)], array_keys(Areas::NAMES)),
            'kinds' => $kinds,
            'province_kinds' => [['key' => 'illegal_hq', 'name' => Persons::PROVINCE_KINDS['illegal_hq']['name']]],
            'groups' => array_map(static fn ($t, $k) => ['title' => $t, 'kinds' => $k], array_keys(Persons::GROUPS), Persons::GROUPS),
            'apps' => array_map(static fn ($k, $v) => ['key' => $k, 'name' => $v], array_keys(PersonDevices::APPS), PersonDevices::APPS),
        ];
    }
}
