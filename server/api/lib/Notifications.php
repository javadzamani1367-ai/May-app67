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
    public static function toUser(string $userId, string $kind, string $title, string $body, ?string $reportId = null): void
    {
        self::insert($userId, null, null, $kind, $title, $body, $reportId);
    }

    public static function toRole(int $role, string $kind, string $title, string $body, ?string $reportId = null): void
    {
        self::insert(null, $role, null, $kind, $title, $body, $reportId);
    }

    public static function toUnit(int $unit, string $kind, string $title, string $body, ?string $reportId = null): void
    {
        self::insert(null, Auth::ROLE_UNIT, $unit, $kind, $title, $body, $reportId);
    }

    private static function insert(
        ?string $userId,
        ?int $role,
        ?int $unit,
        string $kind,
        string $title,
        string $body,
        ?string $reportId
    ): void {
        Db::run(
            'INSERT INTO notifications (id, user_id, role, unit, kind, title, body, report_id, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
            [Db::uuid(), $userId, $role, $unit, $kind, $title, $body, $reportId, Db::now()]
        );
    }
}
