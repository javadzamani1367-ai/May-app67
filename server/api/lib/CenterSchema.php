<?php
declare(strict_types=1);

/**
 * جدول‌های «آمار جامع مراکز استخراج و اقدامات».
 *
 * هر سطر آمار یک مرکز کشف‌شده است: یا از پرونده تأییدشده رمزارز ساخته شده
 * (report_id)، یا از فایل اکسل سال‌های پیش آمده، یا مدیر دستی افزوده. یک پرونده
 * می‌تواند چند سطر داشته باشد (فایل نمونه هم سطر تکراری داشت).
 *
 * مقدار هر ستون سه منبع دارد و هیچ‌کدام دیگری را پاک نمی‌کند: سامانه (هر بار از
 * پرونده خوانده می‌شود)، فایل (import_json) و آنچه کاربر نوشته (values_json).
 * کدام یک برنده است در choice_json می‌ماند (CenterValues).
 */
final class CenterSchema
{
    public const TABLES = [
        "CREATE TABLE IF NOT EXISTS center_rows (
           id          CHAR(36)    NOT NULL PRIMARY KEY,
           report_id   CHAR(36)    NULL,
           year        SMALLINT    NOT NULL,
           area_code   VARCHAR(8)  NULL,
           row_date    BIGINT      NULL,
           source      TINYINT     NOT NULL DEFAULT 0,
           values_json LONGTEXT    NULL,
           import_json LONGTEXT    NULL,
           choice_json TEXT        NULL,
           match_key   VARCHAR(64) NULL,
           created_by  CHAR(36)    NULL,
           created_at  BIGINT      NOT NULL,
           updated_at  BIGINT      NOT NULL,
           deleted_at  BIGINT      NULL,
           KEY idx_center_rows_report (report_id),
           KEY idx_center_rows_date (row_date),
           KEY idx_center_rows_year (year),
           KEY idx_center_rows_area (area_code),
           KEY idx_center_rows_match (match_key)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS center_changes (
           id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
           row_id    CHAR(36)     NOT NULL,
           field     VARCHAR(40)  NOT NULL,
           old_value TEXT         NULL,
           new_value TEXT         NULL,
           how       VARCHAR(16)  NOT NULL,
           user_id   CHAR(36)     NULL,
           at        BIGINT       NOT NULL,
           KEY idx_center_changes_row (row_id, at)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS center_locks (
           year      SMALLINT NOT NULL PRIMARY KEY,
           locked_by CHAR(36) NULL,
           locked_at BIGINT   NOT NULL
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS center_subscribers (
           id           CHAR(36)    NOT NULL PRIMARY KEY,
           year         SMALLINT    NOT NULL,
           subscription VARCHAR(32) NULL,
           bill_id      VARCHAR(32) NULL,
           file_number  VARCHAR(32) NULL,
           data_json    LONGTEXT    NOT NULL,
           created_at   BIGINT      NOT NULL,
           updated_at   BIGINT      NOT NULL,
           KEY idx_center_subs_subscription (subscription),
           KEY idx_center_subs_bill (bill_id),
           KEY idx_center_subs_year (year)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS reward_rules (
           id             CHAR(36)     NOT NULL PRIMARY KEY,
           kind           TINYINT      NOT NULL,
           letter_no      VARCHAR(64)  NULL,
           letter_id      VARCHAR(64)  NULL,
           letter_date    BIGINT       NULL,
           effective_from BIGINT       NOT NULL,
           spec_json      TEXT         NOT NULL,
           note           VARCHAR(500) NULL,
           created_by     CHAR(36)     NULL,
           created_at     BIGINT       NOT NULL,
           deleted_at     BIGINT       NULL,
           KEY idx_reward_rules_kind (kind, effective_from)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    ];

    public const SOURCE_SYSTEM = 0;
    public const SOURCE_IMPORT = 1;
    public const SOURCE_MANUAL = 2;
}
