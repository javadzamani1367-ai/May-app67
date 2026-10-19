<?php
declare(strict_types=1);

/**
 * گام ۹ پایگاه داده: «شخص» جدا از «کاربری»، کد دستگاه هر اپ هر شخص، و پرونده برق
 * غیرمجاز (reports.case_kind). همان در schema.sql هم هست؛ اینجا برای نصب‌هایی که از
 * نسخه ۸ می‌آیند.
 *
 * هر شخص یک بار ثبت می‌شود (کد ملی یکتاست) و هر نقش در هر ناحیه یک کاربری جدا با کد
 * و رمز خودش است: users همان «کاربری» است و person_id آن را به شخص می‌رساند.
 */
final class PersonSchema
{
    public const TABLES = [
        "CREATE TABLE IF NOT EXISTS persons (
           id          CHAR(36)     NOT NULL PRIMARY KEY,
           number      INT          NOT NULL,
           national_id VARCHAR(10)  NULL,
           full_name   VARCHAR(191) NOT NULL,
           phone       VARCHAR(32)  NULL,
           home_area   VARCHAR(8)   NULL,
           note        TEXT         NULL,
           auto_bind   TINYINT      NOT NULL DEFAULT 0,
           active      TINYINT      NOT NULL DEFAULT 1,
           created_at  BIGINT       NOT NULL,
           updated_at  BIGINT       NOT NULL,
           UNIQUE KEY uq_persons_number (number),
           UNIQUE KEY uq_persons_national (national_id),
           KEY idx_persons_home (home_area)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        // کد نصب هر اپ هر شخص: هر نصب کد تازه‌ای دارد و مدیر همان را اینجا می‌نویسد.
        "CREATE TABLE IF NOT EXISTS person_devices (
           person_id     CHAR(36)    NOT NULL,
           app           VARCHAR(16) NOT NULL,
           device_code   VARCHAR(64) NOT NULL,
           registered_at BIGINT      NOT NULL,
           PRIMARY KEY (person_id, app),
           KEY idx_person_devices_code (device_code)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    ];

    /** @return list<array{0:string,1:string,2:string}> جدول، ستون، تعریف */
    public const COLUMNS = [
        ['users', 'person_id', 'CHAR(36) NULL'],
        ['users', 'crew_role', 'TINYINT NULL'],
        ['device_requests', 'national_id', 'VARCHAR(10) NULL'],
        ['device_requests', 'app', 'VARCHAR(16) NULL'],
        ['device_requests', 'note', 'TEXT NULL'],
        ['device_requests', 'person_id', 'CHAR(36) NULL'],
        ['reports', 'case_kind', 'TINYINT NOT NULL DEFAULT 0'],
        ['case_flow', 'county_due_at', 'BIGINT NULL'],
        ['case_flow', 'hq_alert_at', 'BIGINT NULL'],
        ['case_flow', 'hq_due_at', 'BIGINT NULL'],
        ['case_flow', 'decide_alert_at', 'BIGINT NULL'],
        ['crew_members', 'user_id', 'CHAR(36) NULL'],
    ];

    /** @return list<array{0:string,1:string,2:string}> جدول، نام نمایه، ستون‌ها */
    public const INDEXES = [
        ['users', 'idx_users_person', 'person_id'],
        ['reports', 'idx_reports_kind', 'case_kind'],
        ['crew_members', 'idx_crew_user', 'user_id'],
    ];

    /**
     * داده‌های پیش از این گام: هر کاربر موجود یک شخص می‌شود با همان کد و رمز، تا هیچ
     * ورودی از کار نیفتد. کد ملی ندارد (مدیر بعداً می‌نویسد) و اولین ورود هر اپش، همان
     * نصب را ثبت می‌کند (auto_bind) — پیش از این گام فقط کارشناس قفل دستگاه داشت.
     * مجری غیرمجازی که ناحیه داشت مجری شهرستان می‌شود؛ بی‌ناحیه، ستاد.
     */
    public static function backfill(): void
    {
        Db::run("UPDATE users SET role = ? WHERE role = ? AND areas IS NOT NULL AND areas <> ''",
            [Auth::ROLE_ILLEGAL_AREA, Auth::ROLE_ILLEGAL]);
        $now = Db::now();
        foreach (Db::all('SELECT * FROM users WHERE person_id IS NULL ORDER BY created_at') as $user) {
            $personId = Db::uuid();
            Db::run(
                'INSERT INTO persons (id, number, national_id, full_name, phone, home_area, note, auto_bind, active,
                                      created_at, updated_at)
                 VALUES (?, ?, NULL, ?, ?, ?, NULL, 1, 1, ?, ?)',
                [$personId, Persons::nextNumber(), $user['full_name'], $user['phone'],
                 Areas::parse($user['areas'] ?? '')[0] ?? null, (int) $user['created_at'], $now]
            );
            Db::run('UPDATE users SET person_id = ? WHERE id = ?', [$personId, $user['id']]);
            $device = Auth::normaliseDevice($user['device_code'] ?? '');
            if ($device !== '' && (int) $user['role'] === Auth::ROLE_EXPERT) {
                Db::run('INSERT IGNORE INTO person_devices (person_id, app, device_code, registered_at) VALUES (?, ?, ?, ?)',
                    [$personId, 'expert', $device, $now]);
            }
        }
        // گزارش‌های برق غیرمجاز باز که هنوز پرونده ندارند، از این پس در گردش پرونده غیرمجازند.
        foreach (Db::all('SELECT * FROM field_items WHERE kind = ? AND report_id IS NULL AND merged_into IS NULL
                          AND status IN (?, ?, ?)',
            [Field::KIND_ILLEGAL, Field::STATUS_REGISTERED, Field::STATUS_REVIEWING, Field::STATUS_REVISIT]) as $item) {
            IllegalFlow::fromField($item);
        }
    }
}
