<?php
declare(strict_types=1);

/**
 * اعلان‌ها.
 *
 * هاست اشتراکی نه پروسه دائمی دارد نه وب‌سوکت، پس اعلان «فرستاده» نمی‌شود:
 * در این جدول می‌نشیند و اپ و ویندوز با هر بار همگام‌سازی آن را می‌خوانند و
 * خودشان نمایش می‌دهند. ساده است و روی هر هاستی کار می‌کند.
 */
final class Notifications
{
    /**
     * شرط SQL اعلان‌های یک کاربر: خودش، یا نقشش (و واحدش)، و اگر اعلان ناحیه دارد
     * فقط وقتی کاربر همان ناحیه را دارد یا به ناحیه‌ای بسته نیست.
     *
     * @return array{0:string,1:list<mixed>}
     */
    public static function visibleTo(array $user, string $alias = 'n'): array
    {
        $areas = (string) (Areas::store(Areas::parse($user['areas'] ?? '')) ?? '');
        return ["($alias.user_id = ? OR ($alias.user_id IS NULL AND $alias.role = ? AND ($alias.unit IS NULL OR $alias.unit = ?)
                  AND ($alias.area_code IS NULL OR ? = '' OR FIND_IN_SET($alias.area_code, ?) > 0)))",
            [$user['id'], (int) $user['role'], $user['unit'], $areas, $areas]];
    }

    public static function toUser(string $userId, string $kind, string $title, string $body, ?string $reportId = null): void
    {
        self::insert($userId, null, null, $kind, $title, $body, $reportId);
    }

    public static function toRole(int $role, string $kind, string $title, string $body, ?string $reportId = null): void
    {
        self::insert(null, $role, null, $kind, $title, $body, $reportId);
    }

    /**
     * برای یک واحد؛ با ناحیه، فقط کاربران واحد همان ناحیه (و واحد استانی که ناحیه
     * ندارد) می‌بینندش.
     */
    public static function toUnit(int $unit, string $kind, string $title, string $body, ?string $reportId = null,
                                  ?string $area = null): void
    {
        self::insert(null, Auth::ROLE_UNIT, $unit, $kind, $title, $body, $reportId, Areas::code($area));
    }

    /** برای همه کاربران یک نقش؛ با ناحیه، فقط آن‌هایی که آن ناحیه را دارند یا استانی‌اند. */
    public static function toRoleIn(int $role, ?string $area, string $kind, string $title, string $body, ?string $reportId = null): void
    {
        self::insert(null, $role, null, $kind, $title, $body, $reportId, Areas::code($area));
    }

    /** برای مجری (یا مجریان) رمزارز یک ناحیه. */
    public static function toExecutors(?string $area, string $kind, string $title, string $body, ?string $reportId = null): void
    {
        $area = Areas::code($area);
        if ($area !== null) {
            self::insert(null, Auth::ROLE_EXECUTOR, null, $kind, $title, $body, $reportId, $area);
        }
    }

    private static function insert(
        ?string $userId,
        ?int $role,
        ?int $unit,
        string $kind,
        string $title,
        string $body,
        ?string $reportId,
        ?string $area = null
    ): void {
        Db::run(
            'INSERT INTO notifications (id, user_id, role, unit, kind, title, body, report_id, created_at, area_code)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
            [Db::uuid(), $userId, $role, $unit, $kind, $title, $body, $reportId, Db::now(), $area]
        );
    }
}
