<?php
declare(strict_types=1);

/**
 * شخص و کاربری‌هایش.
 *
 * هر شخص یک بار ثبت می‌شود و کد ملی معیار یکتای اوست. مدیر برای هر ناحیه تیک می‌زند
 * که این شخص در آنجا چه نقشی دارد؛ هر تیک یک کاربری جدا با کد و رمز خودش است و شخص
 * برای کار در هر شهرستان با کاربری همان شهرستان وارد می‌شود. پرونده‌ها، ارسال‌ها و
 * امتیازها مثل قبل به کاربری بسته‌اند، پس کار هر ناحیه جدا می‌ماند.
 */
final class Persons
{
    /**
     * نوع‌های کاربری که روی ناحیه تیک می‌خورند: نقش، واحد، نقش اکیپ و پاره کد کاربری.
     *
     * @var array<string, array{role:int, unit:?int, crew:?int, slug:string, name:string}>
     */
    public const KINDS = [
        'expert' => ['role' => Auth::ROLE_EXPERT, 'unit' => null, 'crew' => null, 'slug' => 'exp', 'name' => 'کارشناس'],
        'executor' => ['role' => Auth::ROLE_EXECUTOR, 'unit' => null, 'crew' => null, 'slug' => 'exec', 'name' => 'مجری رمزارز'],
        'illegal' => ['role' => Auth::ROLE_ILLEGAL_AREA, 'unit' => null, 'crew' => null, 'slug' => 'ill', 'name' => 'مجری برق غیرمجاز'],
        'sales' => ['role' => Auth::ROLE_UNIT, 'unit' => 0, 'crew' => null, 'slug' => 'sales', 'name' => 'واحد فروش'],
        'security' => ['role' => Auth::ROLE_UNIT, 'unit' => 1, 'crew' => null, 'slug' => 'security', 'name' => 'حراست'],
        'legal' => ['role' => Auth::ROLE_UNIT, 'unit' => 2, 'crew' => null, 'slug' => 'legal', 'name' => 'حقوقی'],
        'power' => ['role' => Auth::ROLE_UNIT, 'unit' => 3, 'crew' => null, 'slug' => 'power', 'name' => 'برق شهرستان'],
        'field' => ['role' => Auth::ROLE_FIELD, 'unit' => null, 'crew' => null, 'slug' => 'field', 'name' => 'کاربر میدانی'],
        'crew_lead' => ['role' => Auth::ROLE_FIELD, 'unit' => null, 'crew' => FieldRoster::ROLE_LEAD, 'slug' => 'lead', 'name' => 'مسئول اکیپ'],
        'crew_lineman' => ['role' => Auth::ROLE_FIELD, 'unit' => null, 'crew' => FieldRoster::ROLE_LINEMAN, 'slug' => 'lineman', 'name' => 'سیم‌بان'],
        'crew_driver' => ['role' => Auth::ROLE_FIELD, 'unit' => null, 'crew' => FieldRoster::ROLE_DRIVER, 'slug' => 'driver', 'name' => 'راننده'],
        'crew_crypto' => ['role' => Auth::ROLE_FIELD, 'unit' => null, 'crew' => FieldRoster::ROLE_CRYPTO, 'slug' => 'crewcr', 'name' => 'اکیپ: مجری رمزارز'],
        'crew_security' => ['role' => Auth::ROLE_FIELD, 'unit' => null, 'crew' => FieldRoster::ROLE_SECURITY, 'slug' => 'crewsec', 'name' => 'اکیپ: کارشناس حراست'],
        'crew_illegal' => ['role' => Auth::ROLE_FIELD, 'unit' => null, 'crew' => FieldRoster::ROLE_ILLEGAL, 'slug' => 'crewill', 'name' => 'اکیپ: مسئول غیرمجاز'],
    ];
    /** کاربری‌های استانی که ناحیه ندارند. */
    public const PROVINCE_KINDS = [
        'illegal_hq' => ['role' => Auth::ROLE_ILLEGAL, 'slug' => 'illhq', 'name' => 'مجری برق غیرمجاز ستاد'],
        'manager' => ['role' => Auth::ROLE_MANAGER, 'slug' => 'manager', 'name' => 'مدیر'],
    ];
    /** دسته‌های فهرست کاربری‌ها، به همین ترتیب. */
    public const GROUPS = [
        'مجریان' => ['executor', 'illegal'], 'کارشناسان' => ['expert'],
        'واحدهای مقصد' => ['sales', 'security', 'legal', 'power'],
        'اپ بازرسی میدانی و اکیپ' => ['field', 'crew_lead', 'crew_lineman', 'crew_driver', 'crew_crypto', 'crew_security', 'crew_illegal'],
    ];

    public static function nextNumber(): int
    {
        return max(1000, (int) (Db::one('SELECT MAX(number) AS n FROM persons')['n'] ?? 0)) + 1;
    }

    /** کد ملی ده‌رقمی با رقم کنترل درست. */
    public static function validNationalId(string $code): bool
    {
        $code = Auth::latin(trim($code));
        if (!preg_match('/^\d{10}$/', $code) || preg_match('/^(\d)\1{9}$/', $code)) {
            return false;
        }
        $sum = 0;
        for ($i = 0; $i < 9; $i++) {
            $sum += (int) $code[$i] * (10 - $i);
        }
        $rest = $sum % 11;
        $check = (int) $code[9];
        return $rest < 2 ? $check === $rest : $check === 11 - $rest;
    }

    public static function find(string $id): ?array
    {
        return Db::one('SELECT * FROM persons WHERE id = ?', [$id]);
    }

    public static function byNationalId(string $code): ?array
    {
        $code = Auth::latin(trim($code));
        return $code === '' ? null : Db::one('SELECT * FROM persons WHERE national_id = ?', [$code]);
    }

    /** نوع کاربری یک سطر users، یا تهی برای کاربری که در هیچ دسته‌ای نیست. */
    public static function kindOf(array $user): ?string
    {
        $role = (int) $user['role'];
        foreach (self::PROVINCE_KINDS as $key => $kind) {
            if ($kind['role'] === $role) {
                return $key;
            }
        }
        foreach (self::KINDS as $key => $kind) {
            if ($kind['role'] === $role
                && ($kind['unit'] === null || (int) ($user['unit'] ?? -1) === $kind['unit'])
                && ($kind['role'] !== Auth::ROLE_FIELD || $kind['crew'] === ($user['crew_role'] === null ? null : (int) $user['crew_role']))) {
                return $key;
            }
        }
        return null;
    }

    public static function kindName(?string $kind): string
    {
        return self::KINDS[$kind]['name'] ?? self::PROVINCE_KINDS[$kind]['name'] ?? '';
    }

    /**
     * ثبت یا ویرایش اطلاعات شخص. شناسه یا خطای فارسی.
     *
     * @param array<string, mixed> $data
     */
    public static function save(array $data, ?string $id): string|array
    {
        $name = trim(preg_replace('/\s+/u', ' ', (string) ($data['full_name'] ?? '')) ?? '');
        $national = Auth::latin(trim((string) ($data['national_id'] ?? '')));
        $phone = Auth::latin(trim((string) ($data['phone'] ?? '')));
        $home = Areas::code($data['home_area'] ?? null);
        if ($name === '' || mb_strlen($name) > 191) {
            return ['error' => 'نام و نام خانوادگی را بنویسید.'];
        }
        if ($national !== '' && !self::validNationalId($national)) {
            return ['error' => 'کد ملی درست نیست.'];
        }
        $existing = $id === null ? null : self::find($id);
        if ($id !== null && $existing === null) {
            return ['error' => 'شخص پیدا نشد.'];
        }
        if ($national === '' && ($existing === null || ($existing['national_id'] ?? null) !== null)) {
            return ['error' => 'کد ملی را بنویسید؛ معیار یکتای هر شخص است.'];
        }
        $same = $national === '' ? null : self::byNationalId($national);
        if ($same !== null && $same['id'] !== $id) {
            return ['error' => 'این کد ملی برای ' . $same['full_name'] . ' ثبت شده است.', 'person_id' => $same['id']];
        }
        $now = Db::now();
        $values = [$national === '' ? null : $national, $name, $phone === '' ? null : mb_substr($phone, 0, 32), $home,
            mb_substr(trim((string) ($data['note'] ?? '')), 0, 2000), isset($data['auto_bind']) && (int) $data['auto_bind'] === 1 ? 1 : 0,
            isset($data['active']) && (int) $data['active'] === 0 ? 0 : 1, $now];
        if ($existing === null) {
            $id = Db::uuid();
            Db::run('INSERT INTO persons (id, number, national_id, full_name, phone, home_area, note, auto_bind, active,
                                          updated_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
                array_merge([$id, self::nextNumber()], $values, [$now]));
            return $id;
        }
        Db::run('UPDATE persons SET national_id = ?, full_name = ?, phone = ?, home_area = ?, note = ?, auto_bind = ?,
                                    active = ?, updated_at = ? WHERE id = ?', array_merge($values, [$id]));
        // نام کاربری‌ها همان نام شخص است؛ غیرفعال شدن شخص، همه کاربری‌هایش را می‌بندد.
        Db::run('UPDATE users SET full_name = ?, phone = COALESCE(?, phone), updated_at = ? WHERE person_id = ?',
            [$name, $phone === '' ? null : $phone, $now, $id]);
        if ($values[6] === 0) {
            Db::run('UPDATE users SET active = 0 WHERE person_id = ?', [$id]);
            Db::run('DELETE t FROM tokens t JOIN users u ON u.id = t.user_id WHERE u.person_id = ?', [$id]);
        }
        foreach (Db::all('SELECT id FROM users WHERE person_id = ?', [$id]) as $row) {
            CrewAccounts::sync((string) $row['id']);
        }
        return $id;
    }

    /**
     * شخص یک کاربری که هنوز شخص ندارد، از روی نام و تلفن خودش؛ کد ملی را مدیر بعداً
     * می‌نویسد. شناسه شخص را برمی‌گرداند.
     */
    public static function ensureFor(string $userId): ?string
    {
        $user = Db::one('SELECT id, full_name, phone, areas, person_id, created_at FROM users WHERE id = ?', [$userId]);
        if ($user === null) {
            return null;
        }
        if ($user['person_id'] !== null) {
            return (string) $user['person_id'];
        }
        $id = Db::uuid();
        $now = Db::now();
        Db::run('INSERT INTO persons (id, number, national_id, full_name, phone, home_area, note, auto_bind, active, created_at, updated_at)
                 VALUES (?, ?, NULL, ?, ?, ?, NULL, 0, 1, ?, ?)',
            [$id, self::nextNumber(), $user['full_name'], $user['phone'], Areas::parse($user['areas'] ?? '')[0] ?? null, $now, $now]);
        Db::run('UPDATE users SET person_id = ? WHERE id = ?', [$id, $userId]);
        return $id;
    }

    /** کاربری‌های یک شخص، فعال‌ها اول، به ترتیب ناحیه و نوع. */
    public static function accounts(string $personId): array
    {
        $rows = Db::all('SELECT id, user_code, full_name, role, unit, crew_role, areas, permissions, active, updated_at
                         FROM users WHERE person_id = ? ORDER BY active DESC, areas, role, unit, crew_role', [$personId]);
        foreach ($rows as &$row) {
            $row['kind'] = self::kindOf($row);
            $row['kind_name'] = self::kindName($row['kind']) ?: (Users::ROLE_NAMES[(int) $row['role']] ?? '');
            $row['area'] = Areas::parse($row['areas'] ?? '')[0] ?? null;
            $row['area_label'] = $row['area'] === null ? 'استانی' : Areas::label($row['area']);
        }
        return $rows;
    }

    /** کد پیشنهادی: «ناحیه-نوع-شماره شخص»، یا «نوع-شماره» برای کاربری استانی. */
    public static function suggestCode(?string $area, string $kind, int $number): string
    {
        $slug = self::KINDS[$kind]['slug'] ?? self::PROVINCE_KINDS[$kind]['slug'] ?? $kind;
        $base = ($area === null ? '' : $area . '-') . $slug . '-' . $number;
        $code = $base;
        for ($n = 2; Db::one('SELECT id FROM users WHERE user_code = ?', [$code]) !== null; $n++) {
            $code = $base . '-' . $n;
        }
        return $code;
    }
}
