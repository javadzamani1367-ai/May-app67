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
        private readonly Action _onImported;

        private string _address = "http://127.0.0.1:8765";
        private string _pairingCode = string.Empty;
        private string _packagePath = string.Empty;
        private string _packagePassword = string.Empty;
        private bool _busy;

        public ReceiveViewModel(ImportService import, PackageImporter packages, Action onImported)
        {
            _import = import;
            _packages = packages;
            _onImported = onImported;
            Log = new ObservableCollection<string>();
            PullCommand = new RelayCommand(Pull, () => !Busy);
            ImportPackageCommand = new RelayCommand(ImportPackage, () => !Busy);
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
