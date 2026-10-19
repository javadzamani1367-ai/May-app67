<?php
declare(strict_types=1);

/**
 * «درخواست کاربری» از هر اپ: نام، کد ملی، تلفن، توضیح، و کد همان نصب. هیچ کاربری‌ای
 * از این راه ساخته نمی‌شود؛ درخواست در صف مدیر می‌نشیند و مدیر یا شخص تازه‌ای
 * می‌سازد، یا کد را برای شخصی که هست ثبت می‌کند (نصب دوباره). کد ملی، درخواست را
 * خودکار به شخصی که از قبل ثبت شده وصل می‌کند.
 */
final class DeviceRequests
{
    public const PENDING = 0;
    public const DONE = 1;
    public const REJECTED = 2;

    /** @param array<string, mixed> $data */
    public static function submit(array $data): ?string
    {
        $device = Auth::normaliseDevice((string) ($data['device_code'] ?? ''));
        if ($device === '') {
            return 'کد دستگاه ارسال نشده است.';
        }
        $name = trim((string) ($data['full_name'] ?? ''));
        $national = Auth::latin(trim((string) ($data['national_id'] ?? '')));
        // اپ‌های پیش از این نسخه کد ملی نمی‌فرستند؛ درخواستشان پذیرفته است و مدیر کد ملی را می‌نویسد.
        if ($national !== '' && !Persons::validNationalId($national)) {
            return 'کد ملی درست نیست.';
        }
        if ($name === '' && $national === '') {
            return 'نام و کد ملی را بنویسید.';
        }
        $app = (string) ($data['app'] ?? '');
        $app = isset(PersonDevices::APPS[$app]) ? $app : 'expert';
        $person = $national === '' ? null : Persons::byNationalId($national);
        Db::run(
            'INSERT INTO device_requests (id, device_code, full_name, phone, county, status, created_at, national_id, app, note, person_id)
             VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone), county = VALUES(county),
                 created_at = VALUES(created_at), national_id = VALUES(national_id), app = VALUES(app), note = VALUES(note),
                 person_id = VALUES(person_id), status = 0, decided_at = NULL, decided_by = NULL',
            [Db::uuid(), $device, mb_substr($name, 0, 191), mb_substr(Auth::latin(trim((string) ($data['phone'] ?? ''))), 0, 32),
             mb_substr(trim((string) ($data['county'] ?? '')), 0, 100), Db::now(), $national === '' ? null : $national, $app,
             mb_substr(trim((string) ($data['note'] ?? '')), 0, 1000), $person['id'] ?? null]
        );
        Notifications::toRole(Auth::ROLE_MANAGER, 'device_request', 'درخواست کاربری از ' . PersonDevices::APPS[$app],
            ($name === '' ? '' : $name . ' — ') . ($person !== null ? 'شخص ثبت‌شده؛ نصب تازه' : 'شخص تازه'));
        return null;
    }

    /** درخواست‌های در انتظار، تازه‌ترین اول، با شخصی که کد ملی‌اش می‌خواند. */
    public static function pending(): array
    {
        $rows = Db::all(
            'SELECT d.*, p.full_name AS person_name, p.number AS person_number
             FROM device_requests d LEFT JOIN persons p ON p.id = d.person_id OR (d.person_id IS NULL AND p.national_id = d.national_id)
             WHERE d.status = 0 ORDER BY d.created_at DESC LIMIT 200'
        );
        foreach ($rows as &$row) {
            $row['app'] = (string) ($row['app'] ?? 'expert');
            $row['app_name'] = PersonDevices::APPS[$row['app']] ?? '';
            $row['created_at'] = (int) $row['created_at'];
        }
        return $rows;
    }

    /**
     * تصمیم مدیر: «ثبت» (کد برای شخص ثبت‌شده، یا شخص تازه از روی درخواست) یا «رد».
     * شناسه شخص، یا خطا.
     *
     * @return array{person_id?: string, error?: string}
     */
    public static function decide(string $requestId, string $action, ?string $personId, array $manager,
                                  array $fields = []): array
    {
        $request = Db::one('SELECT * FROM device_requests WHERE id = ? AND status = 0', [$requestId]);
        if ($request === null) {
            return ['error' => 'این درخواست دیگر در انتظار نیست.'];
        }
        if ($action === 'reject') {
            Db::run('UPDATE device_requests SET status = ?, decided_at = ?, decided_by = ? WHERE id = ?',
                [self::REJECTED, Db::now(), $manager['id'], $requestId]);
            return [];
        }
        $personId = ($personId ?: null) ?? ($request['person_id'] ?? null)
            ?? (Persons::byNationalId((string) ($request['national_id'] ?? ''))['id'] ?? null);
        if ($personId === null) {
            // مدیر می‌تواند نام و کد ملی درخواست را پیش از ساختن شخص اصلاح کند.
            $made = Persons::save(array_merge(['full_name' => $request['full_name'],
                'national_id' => $request['national_id'] ?? '', 'phone' => $request['phone'] ?? ''],
                array_filter($fields, static fn ($v) => trim((string) $v) !== '')), null);
            if (is_array($made)) {
                return $made;
            }
            $personId = $made;
        }
        $error = PersonDevices::set($personId, (string) ($request['app'] ?: 'expert'), (string) $request['device_code'],
            (string) $manager['id']);
        return $error === null ? ['person_id' => $personId] : ['error' => $error];
    }
}
