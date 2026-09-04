using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Export;
using CryptoInspection.Archive.Sync;

namespace CryptoInspection.Archive.ViewModels
{
    /// <summary>
    /// Wires the four tabs together. Everything is built once here so the views
    /// stay declarative and the data layer is created in one place.
    /// </summary>
    public class MainViewModel
    {
        public MainViewModel(Database database, MediaStore media, ArchiveSettings settings)
        {
            ReportQueries queries = new ReportQueries(database);
            ReportRepository repository = new ReportRepository(database);

            Archive = new ArchiveViewModel(queries);
            Receive = new ReceiveViewModel(
                new ImportService(repository, media),
                new PackageImporter(repository, media),
                OnImported);
            Reports = new ReportsViewModel(
                queries,
                new ExcelExporter(queries),
                new PdfReportBuilder(media),
                new WordReportBuilder(media),
                settings,
                () => Archive.Selected == null ? null : Archive.Selected.Id);
            Settings = new SettingsViewModel(settings);
        }

        public ArchiveViewModel Archive { get; private set; }

        public ReceiveViewModel Receive { get; private set; }

        public ReportsViewModel Reports { get; private set; }

        public SettingsViewModel Settings { get; private set; }

        /// <summary>After a pull or a package import, both views show the new rows.</summary>
        private void OnImported()
        {
            Archive.Refresh();
            Reports.Refresh();
        }
    }
}
