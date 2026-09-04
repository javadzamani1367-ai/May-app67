using System;
using System.Windows;
using System.Windows.Threading;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive
{
    /// <summary>
    /// Application entry point. The database is opened once here so a bad path
    /// or a wrong passphrase is reported before any window tries to query it.
    /// </summary>
    public partial class App : Application
    {
        public static ArchiveSettings Settings { get; private set; }

        public static Database Database { get; private set; }

        public static MediaStore Media { get; private set; }

        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);
            DispatcherUnhandledException += OnUnhandledException;

            Settings = ArchiveSettings.Load();
            Media = new MediaStore(Settings.MediaRoot);
            Database = new Database(Settings.DatabasePath, Settings.DatabasePassword);
            Database.EnsureCreated();
        }

        private void OnUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
        {
            MessageBox.Show(
                Strings.Format("msg_failed", e.Exception.Message),
                Strings.Get("app_title"),
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            e.Handled = true;
        }
    }
}
