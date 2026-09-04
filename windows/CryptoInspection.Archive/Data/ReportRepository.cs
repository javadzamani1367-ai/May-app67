using System;
using System.Collections.Generic;
using Microsoft.Data.Sqlite;

namespace CryptoInspection.Archive.Data
{
    /// <summary>
    /// Writes incoming cases into the archive. Every merge is keyed on the
    /// phone's UUID and is idempotent: the same case arriving twice updates the
    /// row instead of duplicating it, which is the whole reason ids are UUIDs.
    /// </summary>
    public class ReportRepository
    {
        private readonly Database _database;

        public ReportRepository(Database database)
        {
            _database = database;
        }

        /// <summary>Replaces a case and all of its children in one transaction.</summary>
        public void Merge(ReportDetail detail)
        {
            if (detail == null || detail.Report == null || string.IsNullOrEmpty(detail.Report.Id))
            {
                throw new ArgumentException("report id is required");
            }

            using (SqliteConnection connection = _database.Open())
            using (SqliteTransaction transaction = connection.BeginTransaction())
            {
                UpsertReport(connection, transaction, detail.Report);
                ReplaceChildren(connection, transaction, detail);
                transaction.Commit();
            }
        }

        public void MergeAll(IEnumerable<ReportDetail> details)
        {
            using (SqliteConnection connection = _database.Open())
            using (SqliteTransaction transaction = connection.BeginTransaction())
            {
                foreach (ReportDetail detail in details)
                {
                    UpsertReport(connection, transaction, detail.Report);
                    ReplaceChildren(connection, transaction, detail);
                }

                transaction.Commit();
            }
        }

        public void RecordSource(string deviceId, string expertCode, long lastUpdatedAt, int reportCount)
        {
            using (SqliteConnection connection = _database.Open())
            {
                Database.Execute(
                    connection,
                    @"INSERT INTO sync_sources (device_id, expert_code, last_updated_at, last_received_at, report_count)
                      VALUES ($device, $expert, $updated, $received, $count)
                      ON CONFLICT(device_id) DO UPDATE SET
                        expert_code = $expert,
                        last_updated_at = MAX(last_updated_at, $updated),
                        last_received_at = $received,
                        report_count = report_count + $count",
                    null,
                    command =>
                    {
                        Database.Bind(command, "$device", deviceId);
                        Database.Bind(command, "$expert", expertCode);
                        Database.Bind(command, "$updated", lastUpdatedAt);
                        Database.Bind(command, "$received", Util.PersianDate.NowMillis());
                        Database.Bind(command, "$count", reportCount);
                    });
            }
        }

        /// <summary>Where the last pull stopped, so the next one asks for less.</summary>
        public long LastUpdatedFor(string deviceId)
        {
            using (SqliteConnection connection = _database.Open())
            {
                object value = Database.Scalar(
                    connection,
                    "SELECT last_updated_at FROM sync_sources WHERE device_id = $device",
                    command => Database.Bind(command, "$device", deviceId));
                return value == null || value == DBNull.Value ? 0L : Convert.ToInt64(value);
            }
        }

        private static void UpsertReport(SqliteConnection connection, SqliteTransaction transaction, Report report)
        {
            Database.Execute(
                connection,
                @"INSERT OR REPLACE INTO reports (
                    id, tracking_code, temp_code, report_type, status, expert_code,
                    report_date, visit_date, created_at, updated_at, synced_at,
                    county, district, address, postal_code, latitude, longitude, gps_accuracy,
                    file_number, bill_number, subscription_number, usage_type,
                    owner_name, owner_national_id, owner_phone, owner_relation,
                    meter_amperage, measured_amperage, connection_type, seal_status,
                    description, actions_taken)
                  VALUES (
                    $id, $tracking_code, $temp_code, $report_type, $status, $expert_code,
                    $report_date, $visit_date, $created_at, $updated_at, $synced_at,
                    $county, $district, $address, $postal_code, $latitude, $longitude, $gps_accuracy,
                    $file_number, $bill_number, $subscription_number, $usage_type,
                    $owner_name, $owner_national_id, $owner_phone, $owner_relation,
                    $meter_amperage, $measured_amperage, $connection_type, $seal_status,
                    $description, $actions_taken)",
                transaction,
                command =>
                {
                    Database.Bind(command, "$id", report.Id);
                    Database.Bind(command, "$tracking_code", report.TrackingCode);
                    Database.Bind(command, "$temp_code", report.TempCode);
                    Database.Bind(command, "$report_type", report.ReportType);
                    Database.Bind(command, "$status", report.Status);
                    Database.Bind(command, "$expert_code", report.ExpertCode);
                    Database.Bind(command, "$report_date", report.ReportDate);
                    Database.Bind(command, "$visit_date", report.VisitDate);
                    Database.Bind(command, "$created_at", report.CreatedAt);
                    Database.Bind(command, "$updated_at", report.UpdatedAt);
                    Database.Bind(command, "$synced_at", report.SyncedAt);
                    Database.Bind(command, "$county", report.County);
                    Database.Bind(command, "$district", report.District);
                    Database.Bind(command, "$address", report.Address);
                    Database.Bind(command, "$postal_code", report.PostalCode);
                    Database.Bind(command, "$latitude", report.Latitude);
                    Database.Bind(command, "$longitude", report.Longitude);
                    Database.Bind(command, "$gps_accuracy", report.GpsAccuracy);
                    Database.Bind(command, "$file_number", report.FileNumber);
                    Database.Bind(command, "$bill_number", report.BillNumber);
                    Database.Bind(command, "$subscription_number", report.SubscriptionNumber);
                    Database.Bind(command, "$usage_type", report.UsageType);
                    Database.Bind(command, "$owner_name", report.OwnerName);
                    Database.Bind(command, "$owner_national_id", report.OwnerNationalId);
                    Database.Bind(command, "$owner_phone", report.OwnerPhone);
                    Database.Bind(command, "$owner_relation", report.OwnerRelation);
                    Database.Bind(command, "$meter_amperage", report.MeterAmperage);
                    Database.Bind(command, "$measured_amperage", report.MeasuredAmperage);
                    Database.Bind(command, "$connection_type", report.ConnectionType);
                    Database.Bind(command, "$seal_status", report.SealStatus);
                    Database.Bind(command, "$description", report.Description);
                    Database.Bind(command, "$actions_taken", report.ActionsTaken);
                });
        }

        /// <summary>
        /// Children are replaced wholesale: the phone is the source of truth, and
        /// a device deleted there must not linger in the archive.
        /// </summary>
        private static void ReplaceChildren(
            SqliteConnection connection,
            SqliteTransaction transaction,
            ReportDetail detail)
        {
            string reportId = detail.Report.Id;
            foreach (string table in new[] { "devices", "attendees", "media", "attachments", "dispatches" })
            {
                Database.Execute(
                    connection,
                    "DELETE FROM " + table + " WHERE report_id = $report",
                    transaction,
                    command => Database.Bind(command, "$report", reportId));
            }

            foreach (Device device in detail.Devices)
            {
                Database.Execute(
                    connection,
                    @"INSERT OR REPLACE INTO devices (id, report_id, row_number, model, serial_number,
                        power_watt, entry_method, note)
                      VALUES ($id, $report, $row, $model, $serial, $power, $entry, $note)",
                    transaction,
                    command =>
                    {
                        Database.Bind(command, "$id", device.Id);
                        Database.Bind(command, "$report", reportId);
                        Database.Bind(command, "$row", device.RowNumber);
                        Database.Bind(command, "$model", device.Model);
                        Database.Bind(command, "$serial", device.SerialNumber);
                        Database.Bind(command, "$power", device.PowerWatt);
                        Database.Bind(command, "$entry", device.EntryMethod);
                        Database.Bind(command, "$note", device.Note);
                    });
            }

            foreach (Attendee attendee in detail.Attendees)
            {
                Database.Execute(
                    connection,
                    @"INSERT OR REPLACE INTO attendees (id, report_id, organization, full_name, position, org_name)
                      VALUES ($id, $report, $org, $name, $position, $org_name)",
                    transaction,
                    command =>
                    {
                        Database.Bind(command, "$id", attendee.Id);
                        Database.Bind(command, "$report", reportId);
                        Database.Bind(command, "$org", attendee.Organization);
                        Database.Bind(command, "$name", attendee.FullName);
                        Database.Bind(command, "$position", attendee.Position);
                        Database.Bind(command, "$org_name", attendee.OrgName);
                    });
            }

            foreach (MediaItem media in detail.Media)
            {
                Database.Execute(
                    connection,
                    @"INSERT OR REPLACE INTO media (id, report_id, type, file_path, caption,
                        captured_at, latitude, longitude, size_bytes)
                      VALUES ($id, $report, $type, $path, $caption, $captured, $lat, $lon, $size)",
                    transaction,
                    command =>
                    {
                        Database.Bind(command, "$id", media.Id);
                        Database.Bind(command, "$report", reportId);
                        Database.Bind(command, "$type", media.Type);
                        Database.Bind(command, "$path", media.FilePath);
                        Database.Bind(command, "$caption", media.Caption);
                        Database.Bind(command, "$captured", media.CapturedAt);
                        Database.Bind(command, "$lat", media.Latitude);
                        Database.Bind(command, "$lon", media.Longitude);
                        Database.Bind(command, "$size", media.SizeBytes);
                    });
            }

            foreach (Attachment attachment in detail.Attachments)
            {
                Database.Execute(
                    connection,
                    @"INSERT OR REPLACE INTO attachments (id, report_id, category, title, file_path,
                        mime_type, added_at, note)
                      VALUES ($id, $report, $category, $title, $path, $mime, $added, $note)",
                    transaction,
                    command =>
                    {
                        Database.Bind(command, "$id", attachment.Id);
                        Database.Bind(command, "$report", reportId);
                        Database.Bind(command, "$category", attachment.Category);
                        Database.Bind(command, "$title", attachment.Title);
                        Database.Bind(command, "$path", attachment.FilePath);
                        Database.Bind(command, "$mime", attachment.MimeType);
                        Database.Bind(command, "$added", attachment.AddedAt);
                        Database.Bind(command, "$note", attachment.Note);
                    });
            }

            foreach (Dispatch dispatch in detail.Dispatches)
            {
                Database.Execute(
                    connection,
                    @"INSERT OR REPLACE INTO dispatches (id, report_id, unit, included_items, note,
                        output_format, dispatched_at)
                      VALUES ($id, $report, $unit, $items, $note, $format, $at)",
                    transaction,
                    command =>
                    {
                        Database.Bind(command, "$id", dispatch.Id);
                        Database.Bind(command, "$report", reportId);
                        Database.Bind(command, "$unit", dispatch.Unit);
                        Database.Bind(command, "$items", dispatch.IncludedItems);
                        Database.Bind(command, "$note", dispatch.Note);
                        Database.Bind(command, "$format", dispatch.OutputFormat);
                        Database.Bind(command, "$at", dispatch.DispatchedAt);
                    });
            }
        }
    }
}
