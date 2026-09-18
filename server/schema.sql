-- ---------------------------------------------------------------------------
-- سامانه مدیریت بازدید مراکز رمزارز — اسکیمای سرور
--
-- روی هاست اشتراکی با phpMyAdmin وارد شود. جدول‌های پرونده عیناً همان
-- ستون‌های گوشی و ویندوز هستند (windows/SCHEMA.md)؛ هر تغییر باید در هر سه
-- جا اعمال شود. جدول‌های کاربران، ارسال‌ها و پاسخ‌ها فقط روی سرور هستند.
--
-- utf8mb4 چون متن فارسی و شماره‌ها و نام‌ها ذخیره می‌شوند.
-- ---------------------------------------------------------------------------

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- کاربران: کارشناس، مدیر، و کاربر واحد مقصد -------------------------------
CREATE TABLE IF NOT EXISTS users (
  id            CHAR(36)     NOT NULL PRIMARY KEY,
  user_code     VARCHAR(64)  NOT NULL,
  full_name     VARCHAR(191) NOT NULL,
  role          TINYINT      NOT NULL DEFAULT 0,   -- ۰ کارشناس / ۱ مدیر / ۲ واحد
  unit          TINYINT      NULL,                 -- برای نقش واحد: ۰ فروش … ۳ برق شهرستان
  county        VARCHAR(100) NULL,
  phone         VARCHAR(32)  NULL,
  device_code   VARCHAR(64)  NULL,
  password_hash VARCHAR(255) NULL,
  active        TINYINT      NOT NULL DEFAULT 1,
  created_at    BIGINT       NOT NULL,
  updated_at    BIGINT       NOT NULL,
  note          TEXT         NULL,
  UNIQUE KEY uq_users_code (user_code),
  KEY idx_users_device (device_code),
  KEY idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- درخواست ثبت دستگاه: کارشناس می‌فرستد، مدیر تأیید می‌کند ------------------
CREATE TABLE IF NOT EXISTS device_requests (
  id          CHAR(36)     NOT NULL PRIMARY KEY,
  device_code VARCHAR(64)  NOT NULL,
  full_name   VARCHAR(191) NULL,
  phone       VARCHAR(32)  NULL,
  county      VARCHAR(100) NULL,
  status      TINYINT      NOT NULL DEFAULT 0,   -- ۰ در انتظار / ۱ تأیید / ۲ رد
  created_at  BIGINT       NOT NULL,
  decided_at  BIGINT       NULL,
  decided_by  CHAR(36)     NULL,
  UNIQUE KEY uq_device_requests_code (device_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- نشست‌ها: توکن هرگز خام ذخیره نمی‌شود -------------------------------------
CREATE TABLE IF NOT EXISTS tokens (
  token_hash  CHAR(64)  NOT NULL PRIMARY KEY,
  user_id     CHAR(36)  NOT NULL,
  device_code VARCHAR(64) NULL,
  created_at  BIGINT    NOT NULL,
  expires_at  BIGINT    NOT NULL,
  KEY idx_tokens_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- پرونده بازدید — همان اسکیمای گوشی ----------------------------------------
CREATE TABLE IF NOT EXISTS reports (
  id                  CHAR(36)     NOT NULL PRIMARY KEY,
  tracking_code       VARCHAR(64)  NULL,
  temp_code           VARCHAR(64)  NULL,
  report_type         INT          NOT NULL,
  status              INT          NOT NULL,
  expert_code         VARCHAR(64)  NULL,
  report_date         BIGINT       NOT NULL,
  visit_date          BIGINT       NULL,
  created_at          BIGINT       NOT NULL,
  updated_at          BIGINT       NOT NULL,
  synced_at           BIGINT       NULL,
  county              VARCHAR(100) NULL,
  district            VARCHAR(100) NULL,
  address             TEXT         NULL,
  postal_code         VARCHAR(32)  NULL,
  latitude            DOUBLE       NULL,
  longitude           DOUBLE       NULL,
  gps_accuracy        DOUBLE       NULL,
  file_number         VARCHAR(64)  NULL,
  bill_number         VARCHAR(64)  NULL,
  subscription_number VARCHAR(64)  NULL,
  usage_type          VARCHAR(100) NULL,
  owner_name          VARCHAR(191) NULL,
  owner_national_id   VARCHAR(20)  NULL,
  owner_phone         VARCHAR(32)  NULL,
  owner_relation      VARCHAR(100) NULL,
  meter_amperage      DOUBLE       NULL,
  connection_type     VARCHAR(100) NULL,
  seal_status         VARCHAR(100) NULL,
  measured_amperage   DOUBLE       NULL,
  tap_point           INT          NULL,
  phase_type          INT          NULL,
  amperage_r          DOUBLE       NULL,
  amperage_s          DOUBLE       NULL,
  amperage_t          DOUBLE       NULL,
  voltage_r           DOUBLE       NULL,
  voltage_s           DOUBLE       NULL,
  voltage_t           DOUBLE       NULL,
  total_watt          DOUBLE       NULL,
  tariff_type         INT          NULL,
  meter_type          INT          NULL,
  seal_external       INT          NULL,
  seal_external_serial VARCHAR(64) NULL,
  seal_internal       INT          NULL,
  meter_appearance_ok INT          NULL,
  meter_tampered      INT          NULL,
  description         TEXT         NULL,
  actions_taken       TEXT         NULL,
  UNIQUE KEY uq_reports_tracking (tracking_code),
  KEY idx_reports_status (status),
  KEY idx_reports_updated (updated_at),
  KEY idx_reports_county (county),
  KEY idx_reports_expert (expert_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS devices (
  id            CHAR(36)     NOT NULL PRIMARY KEY,
  report_id     CHAR(36)     NOT NULL,
  row_number    INT          NOT NULL,
  model         VARCHAR(191) NULL,
  serial_number VARCHAR(191) NULL,
  power_watt    DOUBLE       NULL,
  entry_method  INT          NOT NULL,
  note          TEXT         NULL,
  KEY idx_devices_report (report_id),
  CONSTRAINT fk_devices_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attendees (
  id           CHAR(36)     NOT NULL PRIMARY KEY,
  report_id    CHAR(36)     NOT NULL,
  organization INT          NOT NULL,
  full_name    VARCHAR(191) NULL,
  position     VARCHAR(191) NULL,
  org_name     VARCHAR(191) NULL,
  KEY idx_attendees_report (report_id),
  CONSTRAINT fk_attendees_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS media (
  id          CHAR(36)     NOT NULL PRIMARY KEY,
  report_id   CHAR(36)     NOT NULL,
  type        INT          NOT NULL,
  file_path   VARCHAR(255) NOT NULL,
  caption     VARCHAR(255) NULL,
  captured_at BIGINT       NOT NULL,
  latitude    DOUBLE       NULL,
  longitude   DOUBLE       NULL,
  size_bytes  BIGINT       NOT NULL DEFAULT 0,
  KEY idx_media_report (report_id),
  CONSTRAINT fk_media_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attachments (
  id        CHAR(36)     NOT NULL PRIMARY KEY,
  report_id CHAR(36)     NOT NULL,
  category  INT          NOT NULL,
  title     VARCHAR(255) NULL,
  file_path VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100) NULL,
  added_at  BIGINT       NOT NULL,
  note      TEXT         NULL,
  KEY idx_attachments_report (report_id),
  CONSTRAINT fk_attachments_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- گردش تأیید: کارشناس می‌فرستد، مدیر تأیید یا برای اصلاح برمی‌گرداند -------
CREATE TABLE IF NOT EXISTS approvals (
  id            CHAR(36) NOT NULL PRIMARY KEY,
  report_id     CHAR(36) NOT NULL,
  submitted_by  CHAR(36) NULL,
  submitted_at  BIGINT   NOT NULL,
  decided_by    CHAR(36) NULL,
  decided_at    BIGINT   NULL,
  decision      TINYINT  NOT NULL DEFAULT 0,  -- ۰ در انتظار / ۱ تأیید / ۲ برگشت برای اصلاح
  comment       TEXT     NULL,
  KEY idx_approvals_report (report_id),
  KEY idx_approvals_decision (decision),
  CONSTRAINT fk_approvals_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ارسال مدارک به واحدها ----------------------------------------------------
CREATE TABLE IF NOT EXISTS dispatches (
  id             CHAR(36) NOT NULL PRIMARY KEY,
  report_id      CHAR(36) NOT NULL,
  unit           INT      NOT NULL,          -- ۰ فروش / ۱ حراست / ۲ حقوقی / ۳ برق شهرستان
  included_items TEXT     NOT NULL,          -- JSON آرایه شناسه‌ها
  note           TEXT     NULL,
  output_format  INT      NOT NULL,
  dispatched_at  BIGINT   NOT NULL,
  sent_by        CHAR(36) NULL,
  channel        TINYINT  NOT NULL DEFAULT 0,-- ۰ سامانه / ۱ شبکه اجتماعی / ۲ بسته آفلاین
  deadline_at    BIGINT   NULL,              -- مهلت اعلام نتیجه اقدام
  status         TINYINT  NOT NULL DEFAULT 0,-- ۰ ارسال‌شده / ۱ دیده‌شده / ۲ پاسخ داده‌شده
  seen_at        BIGINT   NULL,
  answered_at    BIGINT   NULL,
  KEY idx_dispatches_report (report_id),
  KEY idx_dispatches_unit (unit),
  KEY idx_dispatches_status (status),
  KEY idx_dispatches_deadline (deadline_at),
  CONSTRAINT fk_dispatches_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- پاسخ واحد مقصد، با مستندات اقدام ----------------------------------------
CREATE TABLE IF NOT EXISTS dispatch_responses (
  id           CHAR(36) NOT NULL PRIMARY KEY,
  dispatch_id  CHAR(36) NOT NULL,
  responder_id CHAR(36) NULL,
  body         TEXT     NULL,
  created_at   BIGINT   NOT NULL,
  KEY idx_responses_dispatch (dispatch_id),
  CONSTRAINT fk_responses_dispatch FOREIGN KEY (dispatch_id) REFERENCES dispatches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS response_files (
  id          CHAR(36)     NOT NULL PRIMARY KEY,
  response_id CHAR(36)     NOT NULL,
  file_path   VARCHAR(255) NOT NULL,
  file_name   VARCHAR(255) NULL,
  mime_type   VARCHAR(100) NULL,
  size_bytes  BIGINT       NOT NULL DEFAULT 0,
  created_at  BIGINT       NOT NULL,
  KEY idx_response_files_response (response_id),
  CONSTRAINT fk_response_files_response FOREIGN KEY (response_id)
    REFERENCES dispatch_responses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- اعلان‌ها. هاست اشتراکی پروسه دائمی و وب‌سوکت ندارد، پس اپ و ویندوز این
-- جدول را با «از این زمان به بعد» می‌خوانند و خودشان اعلان نشان می‌دهند.
CREATE TABLE IF NOT EXISTS notifications (
  id         CHAR(36) NOT NULL PRIMARY KEY,
  user_id    CHAR(36) NULL,          -- تهی یعنی برای همه کاربران آن نقش
  role       TINYINT  NULL,
  unit       TINYINT  NULL,
  kind       VARCHAR(40) NOT NULL,   -- dispatch / response / approval / returned
  title      VARCHAR(255) NOT NULL,
  body       TEXT     NULL,
  report_id  CHAR(36) NULL,
  created_at BIGINT   NOT NULL,
  read_at    BIGINT   NULL,
  KEY idx_notifications_user (user_id),
  KEY idx_notifications_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- سابقه همگام‌سازی هر دستگاه ------------------------------------------------
CREATE TABLE IF NOT EXISTS sync_log (
  device_code     VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id         CHAR(36)    NULL,
  last_seen_at    BIGINT      NOT NULL,
  last_updated_at BIGINT      NOT NULL DEFAULT 0,
  report_count    INT         NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
