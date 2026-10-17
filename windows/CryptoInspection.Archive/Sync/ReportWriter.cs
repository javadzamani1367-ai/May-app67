using System.Collections.Generic;
using CryptoInspection.Archive.Data;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>
    /// The archive's rows back into the phone's JSON — the reverse of
    /// <see cref="ReportMapper"/>, with the same keys, so a package written here
    /// is read by another archive exactly as a phone's package is.
    /// </summary>
    public static class ReportWriter
    {
        public static Dictionary<string, object> ToJson(ReportDetail detail)
        {
            Report r = detail.Report;
            Dictionary<string, object> json = new Dictionary<string, object>
            {
                { "id", r.Id }, { "tracking_code", r.TrackingCode }, { "temp_code", r.TempCode },
                { "report_type", r.ReportType }, { "status", r.Status }, { "expert_code", r.ExpertCode },
                { "report_date", r.ReportDate }, { "visit_date", r.VisitDate }, { "created_at", r.CreatedAt },
                { "updated_at", r.UpdatedAt }, { "county", r.County }, { "area_code", r.AreaCode },
                { "district", r.District }, { "address", r.Address }, { "postal_code", r.PostalCode },
                { "latitude", r.Latitude }, { "longitude", r.Longitude }, { "gps_accuracy", r.GpsAccuracy },
                { "position_at", r.PositionAt }, { "observer_latitude", r.ObserverLatitude },
                { "observer_longitude", r.ObserverLongitude }, { "observer_accuracy", r.ObserverAccuracy },
                { "file_number", r.FileNumber }, { "bill_number", r.BillNumber },
                { "subscription_number", r.SubscriptionNumber }, { "usage_type", r.UsageType },
                { "owner_name", r.OwnerName }, { "owner_national_id", r.OwnerNationalId }, { "owner_phone", r.OwnerPhone },
                { "owner_relation", r.OwnerRelation }, { "subscriber_name", r.SubscriberName },
                { "meter_amperage", r.MeterAmperage }, { "measured_amperage", r.MeasuredAmperage },
                { "connection_type", r.ConnectionType }, { "seal_status", r.SealStatus },
                { "tap_point", r.TapPoint }, { "phase_type", r.PhaseType },
                { "amperage_r", r.AmperageR }, { "amperage_s", r.AmperageS }, { "amperage_t", r.AmperageT },
                { "voltage_r", r.VoltageR }, { "voltage_s", r.VoltageS }, { "voltage_t", r.VoltageT },
                { "total_watt", r.TotalWatt }, { "tariff_type", r.TariffType }, { "meter_type", r.MeterType },
                { "seal_external", r.SealExternal }, { "seal_external_serial", r.SealExternalSerial },
                { "seal_internal", r.SealInternal }, { "meter_appearance_ok", r.MeterAppearanceOk },
                { "meter_tampered", r.MeterTampered }, { "approval_state", r.ApprovalState },
                { "approval_comment", r.ApprovalComment }, { "approval_at", r.ApprovalAt },
                { "description", r.Description }, { "actions_taken", r.ActionsTaken }
            };

            List<object> devices = new List<object>();
            foreach (Device d in detail.Devices)
            {
                devices.Add(new Dictionary<string, object>
                {
                    { "id", d.Id }, { "report_id", d.ReportId }, { "row_number", d.RowNumber }, { "model", d.Model },
                    { "serial_number", d.SerialNumber }, { "power_watt", d.PowerWatt }, { "entry_method", d.EntryMethod },
                    { "note", d.Note }
                });
            }

            List<object> attendees = new List<object>();
            foreach (Attendee a in detail.Attendees)
            {
                attendees.Add(new Dictionary<string, object>
                {
                    { "id", a.Id }, { "report_id", a.ReportId }, { "organization", a.Organization },
                    { "full_name", a.FullName }, { "position", a.Position }, { "org_name", a.OrgName }
                });
            }

            List<object> media = new List<object>();
            foreach (MediaItem m in detail.Media)
            {
                media.Add(new Dictionary<string, object>
                {
                    { "id", m.Id }, { "report_id", m.ReportId }, { "type", m.Type }, { "file_path", m.FilePath },
                    { "caption", m.Caption }, { "captured_at", m.CapturedAt }, { "latitude", m.Latitude },
                    { "longitude", m.Longitude }, { "size_bytes", m.SizeBytes }
                });
            }

            List<object> attachments = new List<object>();
            foreach (Attachment a in detail.Attachments)
            {
                attachments.Add(new Dictionary<string, object>
                {
                    { "id", a.Id }, { "report_id", a.ReportId }, { "category", a.Category }, { "title", a.Title },
                    { "file_path", a.FilePath }, { "mime_type", a.MimeType }, { "added_at", a.AddedAt }, { "note", a.Note }
                });
            }

            List<object> dispatches = new List<object>();
            foreach (Dispatch d in detail.Dispatches)
            {
                dispatches.Add(new Dictionary<string, object>
                {
                    { "id", d.Id }, { "report_id", d.ReportId }, { "unit", d.Unit }, { "included_items", d.IncludedItems },
                    { "note", d.Note }, { "output_format", d.OutputFormat }, { "dispatched_at", d.DispatchedAt },
                    { "channel", d.Channel }, { "deadline_at", d.DeadlineAt }, { "status", d.Status },
                    { "answered_at", d.AnsweredAt }, { "answer", d.Answer }
                });
            }

            json["devices"] = devices;
            json["attendees"] = attendees;
            json["media"] = media;
            json["attachments"] = attachments;
            json["dispatches"] = dispatches;
            return json;
        }
    }
}
