# اسکیمای مشترک — مرجع سمت ویندوز

نسخه اسکیما: **۱** (`SCHEMA_VERSION = 1`)

این فایل آینه اسکیمای اندروید است. هر تغییری در `android/app/src/main/java/ir/ilam/inspection/data/db/`
باید همین‌جا هم اعمال شود و شماره نسخه بالا برود. دست‌دادن `GET /ping` نسخه را برمی‌گرداند؛
اگر نسخه ویندوز با گوشی یکی نبود، ادغام نباید انجام شود.

## اصول ثابت

- شناسه‌ها **UUID متنی** هستند، نه شماره ترتیبی.
- همه تاریخ‌ها **میلی‌ثانیه یونیکس** هستند. تبدیل به شمسی فقط در لایه نمایش.
- مسیر فایل‌ها **نسبی** است، نسبت به ریشه رسانه (`media/<report_id>/...` و `attachments/<report_id>/...`).
- اعداد در پایگاه داده **لاتین** هستند؛ ارقام فارسی فقط در نمایش و خروجی.
- کدهای عددی enum ها هرگز تغییر شماره نمی‌دهند.

## DDL معادل SQLite

```sql
CREATE TABLE reports (
  id TEXT NOT NULL PRIMARY KEY,
  tracking_code TEXT,
  temp_code TEXT,
  report_type INTEGER NOT NULL,
  status INTEGER NOT NULL,
  expert_code TEXT,
  report_date INTEGER NOT NULL,
  visit_date INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  synced_at INTEGER,
  county TEXT, district TEXT, address TEXT, postal_code TEXT,
  latitude REAL, longitude REAL, gps_accuracy REAL,
  file_number TEXT, bill_number TEXT, subscription_number TEXT, usage_type TEXT,
  owner_name TEXT, owner_national_id TEXT, owner_phone TEXT, owner_relation TEXT,
  meter_amperage REAL, measured_amperage REAL, connection_type TEXT, seal_status TEXT,
  description TEXT, actions_taken TEXT
);
CREATE UNIQUE INDEX index_reports_tracking_code ON reports (tracking_code);
CREATE INDEX index_reports_status ON reports (status);
CREATE INDEX index_reports_updated_at ON reports (updated_at);
CREATE INDEX index_reports_county ON reports (county);

CREATE TABLE devices (
  id TEXT NOT NULL PRIMARY KEY,
  report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
  row_number INTEGER NOT NULL,
  model TEXT, serial_number TEXT, power_watt REAL,
  entry_method INTEGER NOT NULL, note TEXT
);

CREATE TABLE attendees (
  id TEXT NOT NULL PRIMARY KEY,
  report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
  organization INTEGER NOT NULL,
  full_name TEXT, position TEXT, org_name TEXT
);

CREATE TABLE media (
  id TEXT NOT NULL PRIMARY KEY,
  report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
  type INTEGER NOT NULL,
  file_path TEXT NOT NULL,
  caption TEXT,
  captured_at INTEGER NOT NULL,
  latitude REAL, longitude REAL,
  size_bytes INTEGER NOT NULL
);

CREATE TABLE attachments (
  id TEXT NOT NULL PRIMARY KEY,
  report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
  category INTEGER NOT NULL,
  title TEXT,
  file_path TEXT NOT NULL,
  mime_type TEXT,
  added_at INTEGER NOT NULL,
  note TEXT
);

CREATE TABLE dispatches (
  id TEXT NOT NULL PRIMARY KEY,
  report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
  unit INTEGER NOT NULL,
  included_items TEXT NOT NULL,
  note TEXT,
  output_format INTEGER NOT NULL,
  dispatched_at INTEGER NOT NULL
);

CREATE TABLE settings (
  key TEXT NOT NULL PRIMARY KEY,
  value TEXT NOT NULL
);
```

## کدهای عددی

| میدان | کد | معنی |
|---|---|---|
| `reports.report_type` | ۱ / ۲ / ۳ / ۴ / ۵ / ۶ | سراغ / ۱۲۱ / مردمی / همکار / توانیر / میدانی |
| `reports.status` | ۰ / ۱ / ۲ | در دست اقدام / بازدید شده / بایگانی |
| `devices.entry_method` | ۰ / ۱ | بارکد / دستی |
| `attendees.organization` | ۰ / ۱ / ۲ | شرکت برق / پلیس امنیت / سایر |
| `media.type` | ۰ / ۱ | تصویر / ویدیو |
| `attachments.category` | ۰ تا ۸ | بخش ۹ فایل `CLAUDE.md` |
| `dispatches.unit` | ۰ / ۱ / ۲ / ۳ | فروش / حراست / حقوقی / برق شهرستان |
| `dispatches.output_format` | ۰ / ۱ | PDF / Word |

## کلیدهای جدول settings

`expert_code`، `expert_name`، `default_area_code`، `sync_target`، `media_quality`،
و `county_code_<index>` برای کد ناحیه هر شهرستان (index از صفر، به ترتیب فهرست بخش ۶).

## پیاده‌سازی

سمت اندروید: `android/app/src/main/java/ir/ilam/inspection/data/db/`
(`SCHEMA_VERSION` در `AppDatabase.kt`).
سمت ویندوز: `CryptoInspection.Archive/Data/Schema.cs` (`Schema.Version`) و
`Data/Models.cs`. هر دو باید همزمان تغییر کنند.

## دریافت از گوشی

پروتکل و ساختار JSON در `docs/SYNC.md` توضیح داده شده است. نام میدان‌های JSON دقیقاً
همان نام ستون‌های بالا است تا نگاشت در سمت ویندوز مستقیم باشد.
