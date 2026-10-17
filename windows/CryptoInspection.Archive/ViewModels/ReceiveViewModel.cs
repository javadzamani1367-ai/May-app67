using System;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using System.Windows;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Sync;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive.ViewModels
{
    /// <summary>
    /// The receiving tab. Both routes — pulling over Wi-Fi or a USB forward, and
    /// importing an offline package — end in the same merge, so a case looks the
    /// same in the archive however it travelled.
    /// </summary>
    public class ReceiveViewModel : ObservableObject
    {
        private readonly ImportService _import;
        private readonly PackageImporter _packages;
        private readonly PackageExporter _exporter;
        private readonly ArchiveSettings _settings;
        private readonly Action _onImported;

        private string _address = "http://127.0.0.1:8765";
        private string _pairingCode = string.Empty;
        private string _packagePath = string.Empty;
        private string _packagePassword = string.Empty;
        private bool _busy;
        private string _serverAddress;
        private string _serverUser;
        private string _exportArea = string.Empty;
        private string _exportPassword = string.Empty;

        public ReceiveViewModel(ImportService import, PackageImporter packages, PackageExporter exporter,
                                ArchiveSettings settings, Action onImported)
        {
            _import = import;
            _packages = packages;
            _exporter = exporter;
            _settings = settings;
            _onImported = onImported;
            _serverAddress = settings.ServerAddress ?? string.Empty;
            _serverUser = settings.ServerUser ?? string.Empty;
            Log = new ObservableCollection<string>();
            PullCommand = new RelayCommand(Pull, () => !Busy);
            ImportPackageCommand = new RelayCommand(ImportPackage, () => !Busy);
            ServerPullCommand = new RelayCommand(ServerPull, () => !Busy);
            ExportPackageCommand = new RelayCommand(ExportPackage, () => !Busy);
        }

        public RelayCommand ServerPullCommand { get; private set; }

        public RelayCommand ExportPackageCommand { get; private set; }

        public string ServerAddress
        {
            get { return _serverAddress; }
            set { Set(ref _serverAddress, value); }
        }

        public string ServerUser
        {
            get { return _serverUser; }
            set { Set(ref _serverUser, value); }
        }

        /// <summary>Set from the view's password box; never stored — only the session token is.</summary>
        public string ServerPassword { get; set; }

        /// <summary>The area whose cases go into the package; empty for every case here.</summary>
        public string ExportArea
        {
            get { return _exportArea; }
            set { Set(ref _exportArea, value); }
        }

        public string ExportPassword
        {
            get { return _exportPassword; }
            set { Set(ref _exportPassword, value); }
        }

        /// <summary>
        /// Signs in when a password was typed (the token is kept for next time), then
        /// pulls what changed on the server for this account and this archive's areas.
        /// </summary>
        private void ServerPull()
        {
            string address = ServerAddress.Trim();
            string user = PersianNumbers.ToLatin(ServerUser).Trim();
            string password = ServerPassword ?? string.Empty;
            RunInBackground(() =>
            {
                if (password.Length > 0 || string.IsNullOrEmpty(_settings.ServerToken) || _settings.ServerUser != user
                    || _settings.ServerAddress != address)
                {
                    Append(Strings.Get("msg_server_login"));
                    _settings.ServerToken = new ServerClient(address, null).Login(user, password);
                    _settings.ServerAddress = address;
                    _settings.ServerUser = user;
                    _settings.ServerPulledAt = 0;
                    _settings.Save();
                }

                try
                {
                    _import.PullFromServer(new ServerClient(address, _settings.ServerToken), Append);
                }
                catch (ServerRefusal)
                {
                    // An expired session: the next pull asks for the password again.
                    _settings.ServerToken = string.Empty;
                    _settings.Save();
                    throw;
                }
            });
        }

        private void ExportPackage()
        {
            string area = ExportArea;
            string password = ExportPassword;
            if (password.Trim().Length < 6)
            {
                Append(Strings.Get("msg_package_password_short"));
                return;
            }

            if (!System.IO.Directory.Exists(_settings.ExportRoot))
            {
                System.IO.Directory.CreateDirectory(_settings.ExportRoot);
            }

            string name = "package-" + (string.IsNullOrWhiteSpace(area) ? "all" : PersianNumbers.ToLatin(area).Trim())
                + "-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".cvz";
            string path = System.IO.Path.Combine(_settings.ExportRoot, name);
            RunInBackground(() => _exporter.Export(area, password, path, Append));
        }

        public ObservableCollection<string> Log { get; private set; }

        public RelayCommand PullCommand { get; private set; }

        public RelayCommand ImportPackageCommand { get; private set; }

        public string Address
        {
            get { return _address; }
            set { Set(ref _address, value); }
        }

        public string PairingCode
        {
            get { return _pairingCode; }
            set { Set(ref _pairingCode, value); }
        }

        public string PackagePath
        {
            get { return _packagePath; }
            set { Set(ref _packagePath, value); }
        }

        public string PackagePassword
        {
            get { return _packagePassword; }
            set { Set(ref _packagePassword, value); }
        }

        public bool Busy
        {
            get { return _busy; }
            private set { Set(ref _busy, value); }
        }

        private void Pull()
        {
            string address = Address;
            string code = PersianNumbers.ToLatin(PairingCode).Trim();
            RunInBackground(() =>
            {
                PhoneClient client = new PhoneClient(address, code);
                _import.Pull(client, Append);
            });
        }

        private void ImportPackage()
        {
            string path = PackagePath;
            string password = PackagePassword;
            RunInBackground(() => _packages.Import(path, password, Append));
        }

        /// <summary>
        /// Network and disk work stay off the UI thread; log lines are marshalled
        /// back so the operator watches progress instead of a frozen window.
        /// </summary>
        private void RunInBackground(Action work)
        {
            Busy = true;
            Task.Factory.StartNew(work).ContinueWith(task =>
            {
                if (task.Exception != null)
                {
                    Exception error = task.Exception.GetBaseException();
                    Append(Strings.Format("msg_failed", error.Message));
                }

                Application.Current.Dispatcher.Invoke(new Action(() =>
                {
                    Busy = false;
                    if (_onImported != null)
                    {
                        _onImported();
                    }
                }));
            });
        }

        private void Append(string line)
        {
            Application.Current.Dispatcher.Invoke(new Action(() => Log.Add(line)));
        }
    }
}
