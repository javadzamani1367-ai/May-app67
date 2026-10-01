<?php
declare(strict_types=1);

/**
 * جدول‌های اپ بازرسی میدانی.
 *
 * یک جای واحد برای متن ساخت جدول‌ها: گام ۲ به‌روزرسانی همین‌ها را اجرا می‌کند
 * و schema.sql همان‌ها را برای نصب تازه دارد؛ تست run.php یکی بودن نام
 * جدول‌ها را در هر دو می‌سنجد.
 *
 * یک جدول برای همه نوع‌ها (گزارش رمزارز، برق غیرمجاز، ترموویژن، آمپرگیری)،
 * چون آنچه مدیر رویش فیلتر می‌کند — نوع، کاربر، زمان، وضعیت، اولویت، مکان —
 * در همه یکی است. بقیه هر نوع در ستون payload به صورت JSON می‌نشیند.
 */
final class FieldSchema
{
    public const TABLES = [
        "CREATE TABLE IF NOT EXISTS field_items (
           id                CHAR(36)     NOT NULL PRIMARY KEY,
           kind              TINYINT      NOT NULL,
           tracking_code     VARCHAR(32)  NULL,
           user_id           CHAR(36)     NOT NULL,
           device_code       VARCHAR(64)  NULL,
           created_at        BIGINT       NOT NULL,
           client_updated_at BIGINT       NOT NULL,
           received_at       BIGINT       NOT NULL,
           updated_at        BIGINT       NOT NULL,
           status            TINYINT      NOT NULL DEFAULT 0,
           priority          TINYINT      NULL,
           latitude          DOUBLE       NULL,
           longitude         DOUBLE       NULL,
           accuracy          DOUBLE       NULL,
           address           VARCHAR(500) NULL,
           plate             VARCHAR(64)  NULL,
           description       TEXT         NULL,
           payload           LONGTEXT     NULL,
           merged_into       CHAR(36)     NULL,
           UNIQUE KEY uq_field_items_code (tracking_code),
           KEY idx_field_items_kind (kind),
           KEY idx_field_items_status (status),
           KEY idx_field_items_user (user_id),
           KEY idx_field_items_created (created_at),
           KEY idx_field_items_updated (updated_at),
           KEY idx_field_items_position (latitude, longitude),
           KEY idx_field_items_plate (plate)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS field_files (
           id                 CHAR(36)     NOT NULL PRIMARY KEY,
           item_id            CHAR(36)     NOT NULL,
           role               TINYINT      NOT NULL,
           mime               VARCHAR(100) NOT NULL,
           size               BIGINT       NOT NULL,
           sha256             CHAR(64)     NOT NULL,
           captured_at        BIGINT       NULL,
           latitude           DOUBLE       NULL,
           longitude          DOUBLE       NULL,
           accuracy           DOUBLE       NULL,
           location_uncertain TINYINT      NOT NULL DEFAULT 0,
           asset_type         TINYINT      NULL,
           plate              VARCHAR(64)  NULL,
           note               TEXT         NULL,
           path               VARCHAR(255) NULL,
           complete           TINYINT      NOT NULL DEFAULT 0,
           created_at         BIGINT       NOT NULL,
           KEY idx_field_files_item (item_id),
           KEY idx_field_files_plate (plate),
           CONSTRAINT fk_field_files_item FOREIGN KEY (item_id) REFERENCES field_items(id) ON DELETE CASCADE
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS field_events (
           id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
           item_id   CHAR(36)     NOT NULL,
           at        BIGINT       NOT NULL,
           user_id   CHAR(36)     NULL,
           action    VARCHAR(32)  NOT NULL,
           from_status TINYINT    NULL,
           to_status TINYINT      NULL,
           note      TEXT         NULL,
           KEY idx_field_events_item (item_id),
           CONSTRAINT fk_field_events_item FOREIGN KEY (item_id) REFERENCES field_items(id) ON DELETE CASCADE
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS code_counters (
           prefix CHAR(2)  NOT NULL,
           year   SMALLINT NOT NULL,
           last   INT      NOT NULL,
           PRIMARY KEY (prefix, year)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS field_settings (
           k VARCHAR(64) NOT NULL PRIMARY KEY,
           v TEXT        NOT NULL
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    ];
}
