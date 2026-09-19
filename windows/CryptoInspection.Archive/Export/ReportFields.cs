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
                new Field(Strings.Get("column_county"), Labels.CountyWithArea(r.County, r.AreaCode)),
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

        /// <summary>
        /// Section four, built the same way the phone builds it: each phase
        /// against neutral, its own power, then the totals. Empty rows are
        /// dropped so the form never shows a question as answered when it is
        /// not. Mirrors TechnicalRows.kt — change both together.
        /// </summary>
        public static List<Field> TechnicalFields(ReportDetail detail)
        {
            Report r = detail.Report;
            List<Field> fields = new List<Field>();

            fields.Add(new Field(Strings.Get("form_tap_point"), Labels.TapPoint(r.TapPoint)));
            fields.Add(new Field(Strings.Get("form_phase_type"), Labels.PhaseType(r.PhaseType)));

            bool single = r.PhaseType == 0;
            double?[] amps = { r.AmperageR, r.AmperageS, r.AmperageT };
            double?[] volts = { r.VoltageR, r.VoltageS, r.VoltageT };
            string[] names = { "R", "S", "T" };
            int phases = single ? 1 : (r.PhaseType == 1 ? 3 : 0);

            for (int index = 0; index < phases; index++)
            {
                string ampLabel = single
                    ? Strings.Get("form_amperage")
                    : Strings.Format("form_amperage_phase", names[index]);
                string voltLabel = single
                    ? Strings.Get("form_voltage")
                    : Strings.Format("form_voltage_phase", names[index]);
                fields.Add(new Field(ampLabel, PersianNumbers.ToPersian(amps[index])));
                fields.Add(new Field(voltLabel, PersianNumbers.ToPersian(volts[index])));
                if (!single && amps[index].HasValue && volts[index].HasValue)
                {
                    fields.Add(new Field(
                        Strings.Format("form_power_phase", names[index]),
                        Strings.Format("unit_watt",
                            PersianNumbers.ToPersian(amps[index].Value * volts[index].Value))));
                }
            }

            if (r.MeasuredAmperage.HasValue)
            {
                fields.Add(new Field(Strings.Get("form_total_amperage"),
                    Strings.Format("unit_ampere", PersianNumbers.ToPersian(r.MeasuredAmperage))));
            }

            if (r.TotalWatt.HasValue)
            {
                fields.Add(new Field(Strings.Get("form_power_total"),
                    Strings.Format("unit_watt", PersianNumbers.ToPersian(r.TotalWatt))));
                fields.Add(new Field(Strings.Get("form_power_kilowatt"),
                    Strings.Format("unit_kilowatt",
                        PersianNumbers.ToPersian(r.TotalWatt.Value / 1000.0))));
            }

            fields.Add(new Field(Strings.Get("form_tariff_type"), Labels.TariffType(r.TariffType)));
            fields.Add(new Field(Strings.Get("form_meter_type"), Labels.MeterType(r.MeterType)));
            fields.Add(new Field(Strings.Get("question_seal_external"), Labels.YesNo(r.SealExternal)));
            fields.Add(new Field(Strings.Get("form_seal_external_serial"),
                PersianNumbers.ToPersian(r.SealExternalSerial)));
            fields.Add(new Field(Strings.Get("question_seal_internal"), Labels.YesNo(r.SealInternal)));
            fields.Add(new Field(Strings.Get("question_appearance_ok"),
                Labels.YesNo(r.MeterAppearanceOk)));
            fields.Add(new Field(Strings.Get("question_tampered"), Labels.YesNo(r.MeterTampered)));

            return fields.FindAll(field => !string.IsNullOrWhiteSpace(field.Value));
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
