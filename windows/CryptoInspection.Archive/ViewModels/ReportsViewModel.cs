using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Export;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive.ViewModels
{
    /// <summary>A name and its count, for the aggregate lists.</summary>
    public class StatRow
    {
        public string Name { get; set; }
        public string Count { get; set; }
    }

    /// <summary>
    /// Aggregate reporting: the counters, the filtered Excel export, and the
    /// per-case PDF and Word forms rebuilt from the archived data.
    /// </summary>
    public class ReportsViewModel : ObservableObject
    {
        private readonly ReportQueries _queries;
        private readonly ExcelExporter _excel;
        private readonly PdfReportBuilder _pdf;
        private readonly WordReportBuilder _word;
        private readonly ArchiveSettings _settings;
        private readonly Func<string> _selectedReportId;

        private string _fromDate = string.Empty;
        private string _toDate = string.Empty;
        private string _county;
        private string _status;
        private string _message = string.Empty;

        public ReportsViewModel(
            ReportQueries queries,
            ExcelExporter excel,
            PdfReportBuilder pdf,
            WordReportBuilder word,
            ArchiveSettings settings,
            Func<string> selectedReportId)
        {
            _queries = queries;
            _excel = excel;
            _pdf = pdf;
            _word = word;
            _settings = settings;
            _selectedReportId = selectedReportId;

            Counties = new ObservableCollection<string>();
            Statuses = new ObservableCollection<string>
            {
                Strings.Get("filter_all"),
                Labels.Status(0),
                Labels.Status(1),
                Labels.Status(2)
            };
            TypeStats = new ObservableCollection<StatRow>();
            CountyStats = new ObservableCollection<StatRow>();
            ExportExcelCommand = new RelayCommand(ExportExcel);
            ExportPdfCommand = new RelayCommand(ExportPdf);
            ExportWordCommand = new RelayCommand(ExportWord);
            ClearFilterCommand = new RelayCommand(ClearFilter);
            OpenFolderCommand = new RelayCommand(OpenExportFolder);
            Refresh();
        }

        public ObservableCollection<string> Counties { get; private set; }

        public ObservableCollection<string> Statuses { get; private set; }

        public ObservableCollection<StatRow> TypeStats { get; private set; }

        public ObservableCollection<StatRow> CountyStats { get; private set; }

        public RelayCommand ExportExcelCommand { get; private set; }

        public RelayCommand ExportPdfCommand { get; private set; }

        public RelayCommand ExportWordCommand { get; private set; }

        public RelayCommand ClearFilterCommand { get; private set; }

        public RelayCommand OpenFolderCommand { get; private set; }

        public string FromDate
        {
            get { return _fromDate; }
            set { Set(ref _fromDate, value); }
        }

        public string ToDate
        {
            get { return _toDate; }
            set { Set(ref _toDate, value); }
        }

        public string County
        {
            get { return _county; }
            set { Set(ref _county, value); }
        }

        public string Status
        {
            get { return _status; }
            set { Set(ref _status, value); }
        }

        public string Total { get; private set; }

        public string Pending { get; private set; }

        public string Visited { get; private set; }

        public string Archived { get; private set; }

        public string TotalPower { get; private set; }

        public string Message
        {
            get { return _message; }
            private set { Set(ref _message, value); }
        }

        public void Refresh()
        {
            Total = PersianNumbers.ToPersian(_queries.CountByStatus(null));
            Pending = PersianNumbers.ToPersian(_queries.CountByStatus(0));
            Visited = PersianNumbers.ToPersian(_queries.CountByStatus(1));
            Archived = PersianNumbers.ToPersian(_queries.CountByStatus(2));
            TotalPower = PersianNumbers.Grouped(_queries.TotalPower());
            Raise("Total");
            Raise("Pending");
            Raise("Visited");
            Raise("Archived");
            Raise("TotalPower");

            Counties.Clear();
            Counties.Add(Strings.Get("filter_all"));
            foreach (string county in _queries.DistinctCounties())
            {
                Counties.Add(county);
            }

            TypeStats.Clear();
            foreach (KeyValuePair<string, int> bucket in _queries.CountGroupedBy("report_type"))
            {
                int code;
                TypeStats.Add(new StatRow
                {
                    Name = int.TryParse(bucket.Key, out code) ? Labels.ReportType(code) : bucket.Key,
                    Count = PersianNumbers.ToPersian(bucket.Value)
                });
            }

            CountyStats.Clear();
            foreach (KeyValuePair<string, int> bucket in _queries.CountGroupedBy("county"))
            {
                CountyStats.Add(new StatRow
                {
                    Name = bucket.Key,
                    Count = PersianNumbers.ToPersian(bucket.Value)
                });
            }
        }

        private ReportFilter CurrentFilter()
        {
            return new ReportFilter
            {
                FromDate = PersianDate.TryParse(FromDate),
                ToDate = PersianDate.TryParse(ToDate).HasValue
                    ? PersianDate.EndOfDay(PersianDate.TryParse(ToDate).Value)
                    : (long?)null,
                County = County == Strings.Get("filter_all") ? null : County,
                Status = ParseStatus()
            };
        }

        private int? ParseStatus()
        {
            for (int code = 0; code <= 2; code++)
            {
                if (Labels.Status(code) == Status)
                {
                    return code;
                }
            }

            return null;
        }

        private void ClearFilter()
        {
            FromDate = string.Empty;
            ToDate = string.Empty;
            County = null;
            Status = null;
        }

        private void ExportExcel()
        {
            List<Report> reports = _queries.Find(CurrentFilter());
            string path = NewExportPath("reports", ".xlsx");
            _excel.Export(reports, path);
            Message = Strings.Format("msg_export_done", Path.GetFileName(path));
        }

        private void ExportPdf()
        {
            ReportDetail detail = SelectedDetail();
            if (detail == null)
            {
                return;
            }

            string path = NewExportPath(SafeName(detail), ".pdf");
            _pdf.Build(detail, path);
            Message = Strings.Format("msg_export_done", Path.GetFileName(path));
        }

        private void ExportWord()
        {
            ReportDetail detail = SelectedDetail();
            if (detail == null)
            {
                return;
            }

            string path = NewExportPath(SafeName(detail), ".docx");
            _word.Build(detail, path);
            Message = Strings.Format("msg_export_done", Path.GetFileName(path));
        }

        private ReportDetail SelectedDetail()
        {
            string id = _selectedReportId();
            return string.IsNullOrEmpty(id) ? null : _queries.Load(id);
        }

        private static string SafeName(ReportDetail detail)
        {
            string code = detail.Report.DisplayCode ?? detail.Report.Id;
            foreach (char invalid in Path.GetInvalidFileNameChars())
            {
                code = code.Replace(invalid, '_');
            }

            return code;
        }

        private string NewExportPath(string name, string extension)
        {
            if (!Directory.Exists(_settings.ExportRoot))
            {
                Directory.CreateDirectory(_settings.ExportRoot);
            }

            return Path.Combine(
                _settings.ExportRoot,
                name + "-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + extension);
        }

        private void OpenExportFolder()
        {
            if (!Directory.Exists(_settings.ExportRoot))
            {
                Directory.CreateDirectory(_settings.ExportRoot);
            }

            Process.Start("explorer.exe", _settings.ExportRoot);
        }
    }
}
