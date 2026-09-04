using System.Collections.Generic;
using ClosedXML.Excel;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive.Export
{
    /// <summary>
    /// The aggregate report: one row per case with the eighteen columns the
    /// office asks for, plus a second sheet with the totals. Both sheets are
    /// right to left, so column A sits where a Persian reader expects it.
    /// </summary>
    public class ExcelExporter
    {
        private readonly ReportQueries _queries;

        public ExcelExporter(ReportQueries queries)
        {
            _queries = queries;
        }

        public string Export(List<Report> reports, string targetPath)
        {
            using (XLWorkbook workbook = new XLWorkbook())
            {
                IXLWorksheet sheet = workbook.Worksheets.Add(Strings.Get("excel_sheet_name"));
                sheet.RightToLeft = true;
                WriteHeader(sheet);

                int row = 2;
                foreach (Report report in reports)
                {
                    ReportDetail detail = _queries.Load(report.Id);
                    WriteRow(sheet, row++, detail ?? new ReportDetail { Report = report });
                }

                sheet.Columns().AdjustToContents();
                WriteStatsSheet(workbook);
                workbook.SaveAs(targetPath);
            }

            return targetPath;
        }

        private static void WriteHeader(IXLWorksheet sheet)
        {
            string[] headers =
            {
                "column_tracking_code", "column_report_type", "column_report_date", "column_visit_date",
                "column_county", "column_district", "column_address", "column_subscription",
                "column_file_number", "column_bill_number", "column_owner", "column_meter_amperage",
                "column_measured_amperage", "column_device_count", "column_total_power", "column_status",
                "column_expert", "column_coordinates"
            };

            for (int i = 0; i < headers.Length; i++)
            {
                IXLCell cell = sheet.Cell(1, i + 1);
                cell.Value = Strings.Get(headers[i]);
                cell.Style.Font.Bold = true;
                cell.Style.Fill.BackgroundColor = XLColor.FromHtml("#F2F5F7");
            }

            sheet.SheetView.FreezeRows(1);
        }

        private static void WriteRow(IXLWorksheet sheet, int row, ReportDetail detail)
        {
            Report r = detail.Report;
            int column = 1;
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(r.DisplayCode);
            sheet.Cell(row, column++).Value = Labels.ReportType(r.ReportType);
            sheet.Cell(row, column++).Value = PersianDate.Format(r.ReportDate);
            sheet.Cell(row, column++).Value = PersianDate.Format(r.VisitDate);
            sheet.Cell(row, column++).Value = r.County ?? string.Empty;
            sheet.Cell(row, column++).Value = r.District ?? string.Empty;
            sheet.Cell(row, column++).Value = r.Address ?? string.Empty;
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(r.SubscriptionNumber);
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(r.FileNumber);
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(r.BillNumber);
            sheet.Cell(row, column++).Value = r.OwnerName ?? string.Empty;
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(r.MeterAmperage);
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(r.MeasuredAmperage);
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(detail.DeviceCount);
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(detail.TotalPower);
            sheet.Cell(row, column++).Value = Labels.Status(r.Status);
            sheet.Cell(row, column++).Value = PersianNumbers.ToPersian(r.ExpertCode);
            sheet.Cell(row, column).Value = ReportFields.Coordinates(r) ?? string.Empty;
        }

        /// <summary>The aggregate view: totals, then the two breakdowns.</summary>
        private void WriteStatsSheet(XLWorkbook workbook)
        {
            IXLWorksheet sheet = workbook.Worksheets.Add(Strings.Get("stats_sheet_name"));
            sheet.RightToLeft = true;

            int row = 1;
            row = WriteStat(sheet, row, "stats_total", _queries.CountByStatus(null));
            row = WriteStat(sheet, row, "stats_pending", _queries.CountByStatus(0));
            row = WriteStat(sheet, row, "stats_visited", _queries.CountByStatus(1));
            row = WriteStat(sheet, row, "stats_archived", _queries.CountByStatus(2));
            row = WriteStat(sheet, row, "stats_total_power", _queries.TotalPower());

            row++;
            sheet.Cell(row, 1).Value = Strings.Get("stats_by_type");
            sheet.Cell(row++, 1).Style.Font.Bold = true;
            foreach (KeyValuePair<string, int> bucket in _queries.CountGroupedBy("report_type"))
            {
                int code;
                sheet.Cell(row, 1).Value = int.TryParse(bucket.Key, out code)
                    ? Labels.ReportType(code)
                    : bucket.Key;
                sheet.Cell(row++, 2).Value = PersianNumbers.ToPersian(bucket.Value);
            }

            row++;
            sheet.Cell(row, 1).Value = Strings.Get("stats_by_county");
            sheet.Cell(row++, 1).Style.Font.Bold = true;
            foreach (KeyValuePair<string, int> bucket in _queries.CountGroupedBy("county"))
            {
                sheet.Cell(row, 1).Value = bucket.Key;
                sheet.Cell(row++, 2).Value = PersianNumbers.ToPersian(bucket.Value);
            }

            sheet.Columns().AdjustToContents();
        }

        private static int WriteStat(IXLWorksheet sheet, int row, string key, double value)
        {
            sheet.Cell(row, 1).Value = Strings.Get(key);
            sheet.Cell(row, 1).Style.Font.Bold = true;
            sheet.Cell(row, 2).Value = PersianNumbers.Grouped(value);
            return row + 1;
        }
    }
}
