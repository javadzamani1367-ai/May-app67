<?php
declare(strict_types=1);

/**
 * گام ۸ پایگاه داده: ناحیه‌ها، مجریان، گردش پرونده، مراحل اقدام واحدها و بانک
 * دستگاه‌ها. همان در schema.sql هم هست؛ اینجا برای نصب‌هایی که از نسخه ۷ می‌آیند.
 */
final class FlowSchema
{
    public const TABLES = [
        // گردش هر پرونده: چه کسی ثبتش کرد، به کدام مجری و کارشناس رسید، و کی.
        // ستون‌های زمان، خط زمانی و امتیاز را می‌سازند؛ «مهلت گذشته» ذخیره نمی‌شود.
        "CREATE TABLE IF NOT EXISTS case_flow (
           report_id          CHAR(36)   NOT NULL PRIMARY KEY,
           origin             TINYINT    NOT NULL DEFAULT 0,
           intake_code        VARCHAR(32) NULL,
           intake_by          CHAR(36)   NULL,
           intake_at          BIGINT     NULL,
           intake_note        TEXT       NULL,
           refer_due_at       BIGINT     NULL,
           executor_id        CHAR(36)   NULL,
           executor_at        BIGINT     NULL,
           auto_referred      TINYINT    NOT NULL DEFAULT 0,
           expert_id          CHAR(36)   NULL,
           assigned_by        CHAR(36)   NULL,
           assigned_at        BIGINT     NULL,
           due_at             BIGINT     NULL,
           visited_at         BIGINT     NULL,
           approved_at        BIGINT     NULL,
           dispatched_at      BIGINT     NULL,
           executor_action_at BIGINT     NULL,
           expert_alert_at    BIGINT     NULL,
           manager_alert_at   BIGINT     NULL,
           updated_at         BIGINT     NOT NULL,
           KEY idx_case_flow_executor (executor_id),
           KEY idx_case_flow_expert (expert_id),
           KEY idx_case_flow_due (refer_due_at)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        "CREATE TABLE IF NOT EXISTS case_events (
           id        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
           report_id CHAR(36)    NOT NULL,
           at        BIGINT      NOT NULL,
           user_id   CHAR(36)    NULL,
           action    VARCHAR(32) NOT NULL,
           note      TEXT        NULL,
           KEY idx_case_events_report (report_id, at)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        // مراحل اقدام هر واحد؛ تا مدیر چیزی ننوشته، پیش‌فرض‌های UnitStages.
        "CREATE TABLE IF NOT EXISTS unit_stages (
           unit     TINYINT      NOT NULL,
           position TINYINT      NOT NULL,
           name     VARCHAR(191) NOT NULL,
           PRIMARY KEY (unit, position)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        // سریالی که در دو پرونده آمده؛ فقط مدیر می‌بیند.
        "CREATE TABLE IF NOT EXISTS device_alerts (
           id              CHAR(36)     NOT NULL PRIMARY KEY,
           serial_key      VARCHAR(191) NOT NULL,
           report_id       CHAR(36)     NOT NULL,
           other_report_id CHAR(36)     NOT NULL,
           created_at      BIGINT       NOT NULL,
           seen_at         BIGINT       NULL,
           UNIQUE KEY uq_device_alerts (serial_key, report_id, other_report_id),
           KEY idx_device_alerts_created (created_at)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    ];

    /** @return list<array{0:string,1:string,2:string}> جدول، ستون، تعریف */
    public const COLUMNS = [
        ['users', 'areas', 'VARCHAR(255) NULL'],
        ['reports', 'subscriber_name', 'VARCHAR(191) NULL'],
        ['devices', 'serial_key', 'VARCHAR(191) NULL'],
        ['dispatches', 'area_code', 'VARCHAR(8) NULL'],
        ['dispatches', 'stage', 'TINYINT NULL'],
        ['dispatches', 'stage_at', 'BIGINT NULL'],
        ['dispatch_responses', 'stage', 'TINYINT NULL'],
        ['notifications', 'area_code', 'VARCHAR(8) NULL'],
        ['approvals', 'seen_at', 'BIGINT NULL'],
        ['approvals', 'seen_by', 'CHAR(36) NULL'],
        ['field_items', 'area_code', 'VARCHAR(8) NULL'],
        ['field_items', 'handler_id', 'CHAR(36) NULL'],
        ['field_items', 'handled_at', 'BIGINT NULL'],
        ['field_items', 'handler_action_at', 'BIGINT NULL'],
        ['field_items', 'expert_alert_at', 'BIGINT NULL'],
        ['field_items', 'manager_alert_at', 'BIGINT NULL'],
    ];

    /** @return list<array{0:string,1:string,2:string}> جدول، نام نمایه، ستون‌ها */
    public const INDEXES = [
        ['devices', 'idx_devices_serial', 'serial_key'],
        ['dispatches', 'idx_dispatches_area', 'area_code'],
        ['field_items', 'idx_field_items_area', 'area_code'],
        ['field_items', 'idx_field_items_handler', 'handler_id'],
        ['reports', 'idx_reports_area', 'area_code'],
    ];

    /**
     * داده‌های پیش از این گام: ناحیه پرونده‌هایی که فقط نام شهرستان دارند، کلید سریال
     * دستگاه‌ها، و ردیف گردش هر پرونده از روی آنچه سرور از قبل دارد.
     */
    public static function backfill(): void
    {
        foreach (Db::all("SELECT DISTINCT county FROM reports WHERE (area_code IS NULL OR area_code = '') AND county IS NOT NULL") as $row) {
            $area = Areas::fromCounty((string) $row['county']);
            if ($area !== null) {
                Db::run("UPDATE reports SET area_code = ? WHERE (area_code IS NULL OR area_code = '') AND county = ?",
                    [$area, $row['county']]);
            }
        }
        foreach (Db::all('SELECT id, serial_number FROM devices WHERE serial_key IS NULL AND serial_number IS NOT NULL') as $row) {
            Db::run('UPDATE devices SET serial_key = ? WHERE id = ?', [DeviceRegistry::key($row['serial_number']), $row['id']]);
        }
        Db::run(
            'INSERT IGNORE INTO case_flow (report_id, origin, visited_at, approved_at, dispatched_at, updated_at)
             SELECT r.id, 0,
                    CASE WHEN r.status >= 1 THEN COALESCE(r.visit_date, r.updated_at) END,
                    (SELECT MAX(a.decided_at) FROM approvals a WHERE a.report_id = r.id AND a.decision = 1),
                    (SELECT MIN(d.dispatched_at) FROM dispatches d WHERE d.report_id = r.id),
                    ?
             FROM reports r',
            [Db::now()]
        );
        Db::run('UPDATE dispatches d JOIN reports r ON r.id = d.report_id SET d.area_code = r.area_code
                 WHERE d.area_code IS NULL AND r.area_code IS NOT NULL AND r.area_code <> ?', ['']);
    }
}
