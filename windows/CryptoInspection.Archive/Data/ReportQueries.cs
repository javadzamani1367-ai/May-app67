using System;
using System.Collections.Generic;
using System.Data;
using Microsoft.Data.Sqlite;

namespace CryptoInspection.Archive.Data
{
    /// <summary>Filters for the archive grid and the aggregate reports.</summary>
    public class ReportFilter
    {
        public string Search { get; set; }
        public int? Status { get; set; }
        public int? ReportType { get; set; }
        public string County { get; set; }
        public string Expert { get; set; }
        public long? FromDate { get; set; }
        public long? ToDate { get; set; }
    }

    /// <summary>Reading side of the archive: search, load, aggregate.</summary>
    public class ReportQueries
    {
        private const string ReportColumns =
            "id, tracking_code, temp_code, report_type, status, expert_code, report_date, visit_date, " +
            "created_at, updated_at, synced_at, county, district, address, postal_code, latitude, " +
            "longitude, gps_accuracy, file_number, bill_number, subscription_number, usage_type, " +
            "owner_name, owner_national_id, owner_phone, owner_relation, meter_amperage, " +
            "measured_amperage, connection_type, seal_status, description, actions_taken";

        private readonly Database _database;

        public ReportQueries(Database database)
        {
            _database = database;
        }

        public List<Report> Find(ReportFilter filter)
        {
            List<Report> reports = new List<Report>();
            string sql = "SELECT " + ReportColumns + " FROM reports WHERE 1 = 1" +
                " AND ($status IS NULL OR status = $status)" +
                " AND ($type IS NULL OR report_type = $type)" +
                " AND ($county IS NULL OR county = $county)" +
                " AND ($expert IS NULL OR expert_code = $expert)" +
                " AND ($from IS NULL OR report_date >= $from)" +
                " AND ($to IS NULL OR report_date <= $to)" +
                " AND ($search IS NULL OR tracking_code LIKE $like OR temp_code LIKE $like" +
                "      OR address LIKE $like OR subscription_number LIKE $like" +
                "      OR file_number LIKE $like OR owner_name LIKE $like)" +
                " ORDER BY report_date DESC";

            using (SqliteConnection connection = _database.Open())
            using (SqliteCommand command = connection.CreateCommand())
            {
                command.CommandText = sql;
                string search = string.IsNullOrWhiteSpace(filter.Search) ? null : filter.Search.Trim();
                Database.Bind(command, "$status", filter.Status);
                Database.Bind(command, "$type", filter.ReportType);
                Database.Bind(command, "$county", string.IsNullOrWhiteSpace(filter.County) ? null : filter.County);
                Database.Bind(command, "$expert", string.IsNullOrWhiteSpace(filter.Expert) ? null : filter.Expert);
                Database.Bind(command, "$from", filter.FromDate);
                Database.Bind(command, "$to", filter.ToDate);
                Database.Bind(command, "$search", search);
                Database.Bind(command, "$like", search == null ? null : "%" + search + "%");

                using (SqliteDataReader reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        reports.Add(ReadReport(reader));
                    }
                }
            }

            return reports;
        }

        public ReportDetail Load(string id)
        {
            ReportDetail detail = new ReportDetail();
            using (SqliteConnection connection = _database.Open())
            {
                using (SqliteCommand command = connection.CreateCommand())
                {
                    command.CommandText = "SELECT " + ReportColumns + " FROM reports WHERE id = $id";
                    Database.Bind(command, "$id", id);
                    using (SqliteDataReader reader = command.ExecuteReader())
                    {
                        if (!reader.Read())
                        {
                            return null;
                        }

                        detail.Report = ReadReport(reader);
                    }
                }

                Read(connection, "SELECT id, report_id, row_number, model, serial_number, power_watt, " +
                    "entry_method, note FROM devices WHERE report_id = $id ORDER BY row_number", id,
                    record => detail.Devices.Add(new Device
                    {
                        Id = Database.GetString(record, 0),
                        ReportId = Database.GetString(record, 1),
                        RowNumber = Database.GetInt(record, 2),
                        Model = Database.GetString(record, 3),
                        SerialNumber = Database.GetString(record, 4),
                        PowerWatt = Database.GetNullableDouble(record, 5),
                        EntryMethod = Database.GetInt(record, 6),
                        Note = Database.GetString(record, 7)
                    }));

                Read(connection, "SELECT id, report_id, organization, full_name, position, org_name " +
                    "FROM attendees WHERE report_id = $id", id,
                    record => detail.Attendees.Add(new Attendee
                    {
                        Id = Database.GetString(record, 0),
                        ReportId = Database.GetString(record, 1),
                        Organization = Database.GetInt(record, 2),
                        FullName = Database.GetString(record, 3),
                        Position = Database.GetString(record, 4),
                        OrgName = Database.GetString(record, 5)
                    }));

                Read(connection, "SELECT id, report_id, type, file_path, caption, captured_at, latitude, " +
                    "longitude, size_bytes FROM media WHERE report_id = $id ORDER BY captured_at", id,
                    record => detail.Media.Add(new MediaItem
                    {
                        Id = Database.GetString(record, 0),
                        ReportId = Database.GetString(record, 1),
                        Type = Database.GetInt(record, 2),
                        FilePath = Database.GetString(record, 3),
                        Caption = Database.GetString(record, 4),
                        CapturedAt = Database.GetLong(record, 5),
                        Latitude = Database.GetNullableDouble(record, 6),
                        Longitude = Database.GetNullableDouble(record, 7),
                        SizeBytes = Database.GetLong(record, 8)
                    }));

                Read(connection, "SELECT id, report_id, category, title, file_path, mime_type, added_at, " +
                    "note FROM attachments WHERE report_id = $id ORDER BY added_at", id,
                    record => detail.Attachments.Add(new Attachment
                    {
                        Id = Database.GetString(record, 0),
                        ReportId = Database.GetString(record, 1),
                        Category = Database.GetInt(record, 2),
                        Title = Database.GetString(record, 3),
                        FilePath = Database.GetString(record, 4),
                        MimeType = Database.GetString(record, 5),
                        AddedAt = Database.GetLong(record, 6),
                        Note = Database.GetString(record, 7)
                    }));

                Read(connection, "SELECT id, report_id, unit, included_items, note, output_format, " +
                    "dispatched_at FROM dispatches WHERE report_id = $id ORDER BY dispatched_at DESC", id,
                    record => detail.Dispatches.Add(new Dispatch
                    {
                        Id = Database.GetString(record, 0),
                        ReportId = Database.GetString(record, 1),
                        Unit = Database.GetInt(record, 2),
                        IncludedItems = Database.GetString(record, 3),
                        Note = Database.GetString(record, 4),
                        OutputFormat = Database.GetInt(record, 5),
                        DispatchedAt = Database.GetLong(record, 6)
                    }));
            }

            return detail;
        }

        public int CountByStatus(int? status)
        {
            using (SqliteConnection connection = _database.Open())
            {
                object value = Database.Scalar(
                    connection,
                    "SELECT COUNT(*) FROM reports WHERE $status IS NULL OR status = $status",
                    command => Database.Bind(command, "$status", status));
                return value == null || value == DBNull.Value ? 0 : Convert.ToInt32(value);
            }
        }

        public double TotalPower()
        {
            using (SqliteConnection connection = _database.Open())
            {
                object value = Database.Scalar(connection, "SELECT IFNULL(SUM(power_watt), 0) FROM devices");
                return value == null || value == DBNull.Value ? 0 : Convert.ToDouble(value);
            }
        }

        /// <summary>Grouped counts for the stats page. Only these two columns.</summary>
        public List<KeyValuePair<string, int>> CountGroupedBy(string column)
        {
            if (column != "report_type" && column != "county")
            {
                throw new ArgumentException("unsupported grouping column", "column");
            }

            List<KeyValuePair<string, int>> buckets = new List<KeyValuePair<string, int>>();
            using (SqliteConnection connection = _database.Open())
            using (SqliteCommand command = connection.CreateCommand())
            {
                command.CommandText =
                    "SELECT IFNULL(CAST(" + column + " AS TEXT), '') AS bucket, COUNT(*) FROM reports " +
                    "GROUP BY " + column + " ORDER BY COUNT(*) DESC";
                using (SqliteDataReader reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        buckets.Add(new KeyValuePair<string, int>(
                            Database.GetString(reader, 0) ?? string.Empty,
                            Database.GetInt(reader, 1)));
                    }
                }
            }

            return buckets;
        }

        public List<string> DistinctCounties()
        {
            List<string> counties = new List<string>();
            using (SqliteConnection connection = _database.Open())
            using (SqliteCommand command = connection.CreateCommand())
            {
                command.CommandText =
                    "SELECT DISTINCT county FROM reports WHERE county IS NOT NULL AND county <> '' ORDER BY county";
                using (SqliteDataReader reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        counties.Add(reader.GetString(0));
                    }
                }
            }

            return counties;
        }

        private static void Read(SqliteConnection connection, string sql, string id, Action<IDataRecord> handle)
        {
            using (SqliteCommand command = connection.CreateCommand())
            {
                command.CommandText = sql;
                Database.Bind(command, "$id", id);
                using (SqliteDataReader reader = command.ExecuteReader())
                {
                    while (reader.Read())
                    {
                        handle(reader);
                    }
                }
            }
        }

        private static Report ReadReport(IDataRecord record)
        {
            return new Report
            {
                Id = Database.GetString(record, 0),
                TrackingCode = Database.GetString(record, 1),
                TempCode = Database.GetString(record, 2),
                ReportType = Database.GetInt(record, 3),
                Status = Database.GetInt(record, 4),
                ExpertCode = Database.GetString(record, 5),
                ReportDate = Database.GetLong(record, 6),
                VisitDate = Database.GetNullableLong(record, 7),
                CreatedAt = Database.GetLong(record, 8),
                UpdatedAt = Database.GetLong(record, 9),
                SyncedAt = Database.GetNullableLong(record, 10),
                County = Database.GetString(record, 11),
                District = Database.GetString(record, 12),
                Address = Database.GetString(record, 13),
                PostalCode = Database.GetString(record, 14),
                Latitude = Database.GetNullableDouble(record, 15),
                Longitude = Database.GetNullableDouble(record, 16),
                GpsAccuracy = Database.GetNullableDouble(record, 17),
                FileNumber = Database.GetString(record, 18),
                BillNumber = Database.GetString(record, 19),
                SubscriptionNumber = Database.GetString(record, 20),
                UsageType = Database.GetString(record, 21),
                OwnerName = Database.GetString(record, 22),
                OwnerNationalId = Database.GetString(record, 23),
                OwnerPhone = Database.GetString(record, 24),
                OwnerRelation = Database.GetString(record, 25),
                MeterAmperage = Database.GetNullableDouble(record, 26),
                MeasuredAmperage = Database.GetNullableDouble(record, 27),
                ConnectionType = Database.GetString(record, 28),
                SealStatus = Database.GetString(record, 29),
                Description = Database.GetString(record, 30),
                ActionsTaken = Database.GetString(record, 31)
            };
        }
    }
}
