using System.Collections.Generic;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive.Export
{
    /// <summary>One label/value line of the printed form.</summary>
    public struct Field
    {
        public string Label;
        public string Value;

        public Field(string label, string value)
        {
            Label = label;
            Value = string.IsNullOrEmpty(value) ? Strings.Get("value_empty") : value;
        }
    }

    /// <summary>
    /// The seven sections of the visit form, assembled once and reused by the
    /// PDF and Word writers so the two outputs can never drift apart.
    /// </summary>
    public static class ReportFields
    {
        public static List<Field> CaseFields(ReportDetail detail)
        {
            Report r = detail.Report;
            return new List<Field>
            {
                new Field(Strings.Get("column_tracking_code"), PersianNumbers.ToPersian(r.DisplayCode)),
                new Field(Strings.Get("column_report_type"), Labels.ReportType(r.ReportType)),
                new Field(Strings.Get("column_report_date"), PersianDate.Format(r.ReportDate)),
                new Field(Strings.Get("column_visit_date"), PersianDate.Format(r.VisitDate)),
                new Field(Strings.Get("column_expert"), PersianNumbers.ToPersian(r.ExpertCode)),
                new Field(Strings.Get("column_status"), Labels.Status(r.Status))
            };
        }

        public static List<Field> LocationFields(ReportDetail detail)
        {
            Report r = detail.Report;
            return new List<Field>
            {
                new Field(Strings.Get("column_county"), r.County),
                new Field(Strings.Get("column_district"), r.District),
                new Field(Strings.Get("column_address"), r.Address),
                new Field(Strings.Get("form_postal_code"), PersianNumbers.ToPersian(r.PostalCode)),
                new Field(Strings.Get("column_file_number"), PersianNumbers.ToPersian(r.FileNumber)),
                new Field(Strings.Get("column_bill_number"), PersianNumbers.ToPersian(r.BillNumber)),
                new Field(Strings.Get("column_subscription"), PersianNumbers.ToPersian(r.SubscriptionNumber)),
                new Field(Strings.Get("form_usage_type"), r.UsageType),
                new Field(Strings.Get("column_coordinates"), Coordinates(r))
            };
        }

        public static List<Field> OwnerFields(ReportDetail detail)
        {
            Report r = detail.Report;
            return new List<Field>
            {
                new Field(Strings.Get("column_owner"), r.OwnerName),
                new Field(Strings.Get("form_owner_national_id"), PersianNumbers.ToPersian(r.OwnerNationalId)),
                new Field(Strings.Get("form_owner_phone"), PersianNumbers.ToPersian(r.OwnerPhone)),
                new Field(Strings.Get("form_owner_relation"), r.OwnerRelation)
            };
        }

        public static List<Field> TechnicalFields(ReportDetail detail)
        {
            Report r = detail.Report;
            return new List<Field>
            {
                new Field(Strings.Get("column_meter_amperage"), PersianNumbers.ToPersian(r.MeterAmperage)),
                new Field(Strings.Get("column_measured_amperage"), PersianNumbers.ToPersian(r.MeasuredAmperage)),
                new Field(Strings.Get("form_connection_type"), r.ConnectionType),
                new Field(Strings.Get("form_seal_status"), r.SealStatus)
            };
        }

        public static List<string> DeviceHeader()
        {
            return new List<string>
            {
                Strings.Get("column_row"),
                Strings.Get("column_device_model"),
                Strings.Get("column_device_serial"),
                Strings.Get("column_device_power"),
                Strings.Get("column_entry_method")
            };
        }

        public static List<List<string>> DeviceRows(ReportDetail detail)
        {
            List<List<string>> rows = new List<List<string>>();
            foreach (Device device in detail.Devices)
            {
                rows.Add(new List<string>
                {
                    PersianNumbers.ToPersian(device.RowNumber),
                    device.Model ?? string.Empty,
                    PersianNumbers.ToPersian(device.SerialNumber),
                    PersianNumbers.ToPersian(device.PowerWatt),
                    Labels.EntryMethod(device.EntryMethod)
                });
            }

            return rows;
        }

        public static List<string> AttendeeHeader()
        {
            return new List<string>
            {
                Strings.Get("column_row"),
                Strings.Get("column_attendee_name"),
                Strings.Get("column_attendee_position"),
                Strings.Get("column_attendee_org")
            };
        }

        public static List<List<string>> AttendeeRows(ReportDetail detail)
        {
            List<List<string>> rows = new List<List<string>>();
            int index = 1;
            foreach (Attendee attendee in detail.Attendees)
            {
                rows.Add(new List<string>
                {
                    PersianNumbers.ToPersian(index++),
                    attendee.FullName ?? string.Empty,
                    attendee.Position ?? string.Empty,
                    string.IsNullOrEmpty(attendee.OrgName)
                        ? Labels.Organization(attendee.Organization)
                        : attendee.OrgName
                });
            }

            return rows;
        }

        public static string PhotoCaption(ReportDetail detail, MediaItem photo)
        {
            List<string> parts = new List<string>();
            if (!string.IsNullOrEmpty(photo.Caption))
            {
                parts.Add(photo.Caption);
            }

            parts.Add(PersianDate.FormatWithTime(photo.CapturedAt));
            if (photo.Latitude.HasValue && photo.Longitude.HasValue)
            {
                parts.Add(PersianNumbers.ToPersian(
                    string.Format("{0:F5} , {1:F5}", photo.Latitude.Value, photo.Longitude.Value)));
            }

            parts.Add(PersianNumbers.ToPersian(detail.Report.DisplayCode));
            return string.Join(" | ", parts.ToArray());
        }

        public static string Coordinates(Report report)
        {
            if (!report.Latitude.HasValue || !report.Longitude.HasValue)
            {
                return null;
            }

            string text = string.Format("{0:F6} , {1:F6}", report.Latitude.Value, report.Longitude.Value);
            if (report.GpsAccuracy.HasValue)
            {
                text += string.Format(" (± {0:F0})", report.GpsAccuracy.Value);
            }

            return PersianNumbers.ToPersian(text);
        }
    }
}
