using System.Collections.ObjectModel;
using System.Collections.Generic;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive.ViewModels
{
    /// <summary>One grid line of the archive.</summary>
    public class ReportRow
    {
        public string Id { get; set; }
        public string TrackingCode { get; set; }
        public string ReportType { get; set; }
        public string ReportDate { get; set; }
        public string VisitDate { get; set; }
        public string County { get; set; }
        public string Address { get; set; }
        public string Subscription { get; set; }
        public string Owner { get; set; }
        public string Status { get; set; }
        public string Expert { get; set; }
        public string LastSync { get; set; }

        public static ReportRow From(Report report)
        {
            return new ReportRow
            {
                Id = report.Id,
                TrackingCode = PersianNumbers.ToPersian(report.DisplayCode),
                ReportType = Labels.ReportType(report.ReportType),
                ReportDate = PersianDate.Format(report.ReportDate),
                VisitDate = PersianDate.Format(report.VisitDate),
                County = report.County,
                Address = report.Address,
                Subscription = PersianNumbers.ToPersian(report.SubscriptionNumber),
                Owner = report.OwnerName,
                Status = Labels.Status(report.Status),
                Expert = PersianNumbers.ToPersian(report.ExpertCode),
                LastSync = PersianDate.Format(report.SyncedAt)
            };
        }
    }

    /// <summary>The archive tab: search across every case a phone has sent.</summary>
    public class ArchiveViewModel : ObservableObject
    {
        private readonly ReportQueries _queries;
        private string _search = string.Empty;
        private ReportRow _selected;
        private string _summary = string.Empty;

        public ArchiveViewModel(ReportQueries queries)
        {
            _queries = queries;
            Rows = new ObservableCollection<ReportRow>();
            RefreshCommand = new RelayCommand(Refresh);
            Refresh();
        }

        public ObservableCollection<ReportRow> Rows { get; private set; }

        public RelayCommand RefreshCommand { get; private set; }

        public string Search
        {
            get { return _search; }
            set
            {
                if (Set(ref _search, value))
                {
                    Refresh();
                }
            }
        }

        public ReportRow Selected
        {
            get { return _selected; }
            set { Set(ref _selected, value); }
        }

        public string Summary
        {
            get { return _summary; }
            private set { Set(ref _summary, value); }
        }

        public void Refresh()
        {
            List<Report> reports = _queries.Find(new ReportFilter { Search = _search });
            Rows.Clear();
            foreach (Report report in reports)
            {
                Rows.Add(ReportRow.From(report));
            }

            Summary = Strings.Format("archive_count", PersianNumbers.ToPersian(Rows.Count));
        }
    }
}
