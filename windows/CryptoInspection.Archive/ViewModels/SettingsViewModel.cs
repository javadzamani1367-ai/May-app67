using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive.ViewModels
{
    /// <summary>
    /// Where the archive lives and how it unlocks. Paths and passphrase take
    /// effect on the next start, because the database is opened once at startup.
    /// </summary>
    public class SettingsViewModel : ObservableObject
    {
        private readonly ArchiveSettings _settings;
        private string _message = string.Empty;

        public SettingsViewModel(ArchiveSettings settings)
        {
            _settings = settings;
            DatabasePath = settings.DatabasePath;
            MediaRoot = settings.MediaRoot;
            ExportRoot = settings.ExportRoot;
            DatabasePassword = settings.DatabasePassword;
            SaveCommand = new RelayCommand(Save);
        }

        public RelayCommand SaveCommand { get; private set; }

        public string DatabasePath { get; set; }

        public string MediaRoot { get; set; }

        public string ExportRoot { get; set; }

        public string DatabasePassword { get; set; }

        public string Message
        {
            get { return _message; }
            private set { Set(ref _message, value); }
        }

        private void Save()
        {
            _settings.DatabasePath = DatabasePath;
            _settings.MediaRoot = MediaRoot;
            _settings.ExportRoot = ExportRoot;
            _settings.DatabasePassword = DatabasePassword;
            _settings.Save();
            Message = Strings.Get("msg_saved");
        }
    }
}
