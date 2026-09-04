namespace CryptoInspection.Archive.Data
{
    /// <summary>
    /// The shared schema, byte for byte the tables Room creates on the phone.
    /// Any change here has to be made on both sides and bump the version — the
    /// sync handshake refuses to merge across different versions.
    /// </summary>
    public static class Schema
    {
        public const int Version = 1;

        public static readonly string[] Statements =
        {
            @"CREATE TABLE IF NOT EXISTS reports (
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
                description TEXT, actions_taken TEXT)",

            @"CREATE UNIQUE INDEX IF NOT EXISTS index_reports_tracking_code
                ON reports (tracking_code)",
            @"CREATE INDEX IF NOT EXISTS index_reports_status ON reports (status)",
            @"CREATE INDEX IF NOT EXISTS index_reports_updated_at ON reports (updated_at)",
            @"CREATE INDEX IF NOT EXISTS index_reports_county ON reports (county)",

            @"CREATE TABLE IF NOT EXISTS devices (
                id TEXT NOT NULL PRIMARY KEY,
                report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
                row_number INTEGER NOT NULL,
                model TEXT, serial_number TEXT, power_watt REAL,
                entry_method INTEGER NOT NULL, note TEXT)",
            @"CREATE INDEX IF NOT EXISTS index_devices_report_id ON devices (report_id)",

            @"CREATE TABLE IF NOT EXISTS attendees (
                id TEXT NOT NULL PRIMARY KEY,
                report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
                organization INTEGER NOT NULL,
                full_name TEXT, position TEXT, org_name TEXT)",
            @"CREATE INDEX IF NOT EXISTS index_attendees_report_id ON attendees (report_id)",

            @"CREATE TABLE IF NOT EXISTS media (
                id TEXT NOT NULL PRIMARY KEY,
                report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
                type INTEGER NOT NULL,
                file_path TEXT NOT NULL,
                caption TEXT,
                captured_at INTEGER NOT NULL,
                latitude REAL, longitude REAL,
                size_bytes INTEGER NOT NULL)",
            @"CREATE INDEX IF NOT EXISTS index_media_report_id ON media (report_id)",

            @"CREATE TABLE IF NOT EXISTS attachments (
                id TEXT NOT NULL PRIMARY KEY,
                report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
                category INTEGER NOT NULL,
                title TEXT,
                file_path TEXT NOT NULL,
                mime_type TEXT,
                added_at INTEGER NOT NULL,
                note TEXT)",
            @"CREATE INDEX IF NOT EXISTS index_attachments_report_id ON attachments (report_id)",

            @"CREATE TABLE IF NOT EXISTS dispatches (
                id TEXT NOT NULL PRIMARY KEY,
                report_id TEXT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
                unit INTEGER NOT NULL,
                included_items TEXT NOT NULL,
                note TEXT,
                output_format INTEGER NOT NULL,
                dispatched_at INTEGER NOT NULL)",
            @"CREATE INDEX IF NOT EXISTS index_dispatches_report_id ON dispatches (report_id)",

            @"CREATE TABLE IF NOT EXISTS settings (
                key TEXT NOT NULL PRIMARY KEY,
                value TEXT NOT NULL)",

            // Archive-only bookkeeping: which phone sent what, and how far we got.
            @"CREATE TABLE IF NOT EXISTS sync_sources (
                device_id TEXT NOT NULL PRIMARY KEY,
                expert_code TEXT,
                last_updated_at INTEGER NOT NULL,
                last_received_at INTEGER NOT NULL,
                report_count INTEGER NOT NULL)"
        };
    }
}
