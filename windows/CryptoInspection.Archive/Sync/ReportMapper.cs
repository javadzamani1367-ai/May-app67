using System.Collections.Generic;
using CryptoInspection.Archive.Data;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>
    /// Maps the phone's JSON onto the archive's rows. The keys are the database
    /// column names on both sides, so this file is the one place that has to
    /// change when the schema does.
    /// </summary>
    public static class ReportMapper
    {
        public static ReportDetail ToDetail(JsonObject json)
        {
            ReportDetail detail = new ReportDetail
            {
                Report = new Report
                {
                    Id = json.String("id"),
                    TrackingCode = json.String("tracking_code"),
                    TempCode = json.String("temp_code"),
                    ReportType = json.Int("report_type"),
                    Status = json.Int("status"),
                    ExpertCode = json.String("expert_code"),
                    ReportDate = json.Long("report_date"),
                    VisitDate = json.NullableLong("visit_date"),
                    CreatedAt = json.Long("created_at"),
                    UpdatedAt = json.Long("updated_at"),

                    // synced_at is phone-local bookkeeping and is not on the wire;
                    // the archive stamps it with the moment the case arrived here.
                    SyncedAt = null,
                    County = json.String("county"),
                    AreaCode = json.String("area_code"),
                    District = json.String("district"),
                    Address = json.String("address"),
                    PostalCode = json.String("postal_code"),
                    Latitude = json.NullableDouble("latitude"),
                    Longitude = json.NullableDouble("longitude"),
                    GpsAccuracy = json.NullableDouble("gps_accuracy"),
                    FileNumber = json.String("file_number"),
                    BillNumber = json.String("bill_number"),
                    SubscriptionNumber = json.String("subscription_number"),
                    UsageType = json.String("usage_type"),
                    OwnerName = json.String("owner_name"),
                    OwnerNationalId = json.String("owner_national_id"),
                    OwnerPhone = json.String("owner_phone"),
                    OwnerRelation = json.String("owner_relation"),
                    MeterAmperage = json.NullableDouble("meter_amperage"),
                    MeasuredAmperage = json.NullableDouble("measured_amperage"),
                    ConnectionType = json.String("connection_type"),
                    SealStatus = json.String("seal_status"),
                    TapPoint = json.NullableInt("tap_point"),
                    PhaseType = json.NullableInt("phase_type"),
                    AmperageR = json.NullableDouble("amperage_r"),
                    AmperageS = json.NullableDouble("amperage_s"),
                    AmperageT = json.NullableDouble("amperage_t"),
                    VoltageR = json.NullableDouble("voltage_r"),
                    VoltageS = json.NullableDouble("voltage_s"),
                    VoltageT = json.NullableDouble("voltage_t"),
                    TotalWatt = json.NullableDouble("total_watt"),
                    TariffType = json.NullableInt("tariff_type"),
                    MeterType = json.NullableInt("meter_type"),
                    SealExternal = json.NullableInt("seal_external"),
                    SealExternalSerial = json.String("seal_external_serial"),
                    SealInternal = json.NullableInt("seal_internal"),
                    MeterAppearanceOk = json.NullableInt("meter_appearance_ok"),
                    MeterTampered = json.NullableInt("meter_tampered"),
                    ApprovalState = json.Int("approval_state"),
                    ApprovalComment = json.String("approval_comment"),
                    ApprovalAt = json.NullableLong("approval_at"),
                    Description = json.String("description"),
                    ActionsTaken = json.String("actions_taken")
                }
            };

            foreach (JsonObject item in json.Array("devices"))
            {
                detail.Devices.Add(new Device
                {
                    Id = item.String("id"),
                    ReportId = item.String("report_id"),
                    RowNumber = item.Int("row_number"),
                    Model = item.String("model"),
                    SerialNumber = item.String("serial_number"),
                    PowerWatt = item.NullableDouble("power_watt"),
                    EntryMethod = item.Int("entry_method"),
                    Note = item.String("note")
                });
            }

            foreach (JsonObject item in json.Array("attendees"))
            {
                detail.Attendees.Add(new Attendee
                {
                    Id = item.String("id"),
                    ReportId = item.String("report_id"),
                    Organization = item.Int("organization"),
                    FullName = item.String("full_name"),
                    Position = item.String("position"),
                    OrgName = item.String("org_name")
                });
            }

            foreach (JsonObject item in json.Array("media"))
            {
                detail.Media.Add(new MediaItem
                {
                    Id = item.String("id"),
                    ReportId = item.String("report_id"),
                    Type = item.Int("type"),
                    FilePath = item.String("file_path"),
                    Caption = item.String("caption"),
                    CapturedAt = item.Long("captured_at"),
                    Latitude = item.NullableDouble("latitude"),
                    Longitude = item.NullableDouble("longitude"),
                    SizeBytes = item.Long("size_bytes")
                });
            }

            foreach (JsonObject item in json.Array("attachments"))
            {
                detail.Attachments.Add(new Attachment
                {
                    Id = item.String("id"),
                    ReportId = item.String("report_id"),
                    Category = item.Int("category"),
                    Title = item.String("title"),
                    FilePath = item.String("file_path"),
                    MimeType = item.String("mime_type"),
                    AddedAt = item.Long("added_at"),
                    Note = item.String("note")
                });
            }

            foreach (JsonObject item in json.Array("dispatches"))
            {
                detail.Dispatches.Add(new Dispatch
                {
                    Id = item.String("id"),
                    ReportId = item.String("report_id"),
                    Unit = item.Int("unit"),
                    IncludedItems = item.String("included_items"),
                    Note = item.String("note"),
                    OutputFormat = item.Int("output_format"),
                    DispatchedAt = item.Long("dispatched_at"),
                    Channel = item.Int("channel"),
                    DeadlineAt = item.NullableLong("deadline_at"),
                    Status = item.Int("status"),
                    AnsweredAt = item.NullableLong("answered_at"),
                    Answer = item.String("answer")
                });
            }

            return detail;
        }

        /// <summary>Every file a case refers to, media and attachments alike.</summary>
        public static List<KeyValuePair<string, string>> FileReferences(ReportDetail detail)
        {
            List<KeyValuePair<string, string>> files = new List<KeyValuePair<string, string>>();
            foreach (MediaItem item in detail.Media)
            {
                files.Add(new KeyValuePair<string, string>(item.Id, item.FilePath));
            }

            foreach (Attachment attachment in detail.Attachments)
            {
                files.Add(new KeyValuePair<string, string>(attachment.Id, attachment.FilePath));
            }

            return files;
        }
    }
}
