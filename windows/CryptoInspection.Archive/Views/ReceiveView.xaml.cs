using System.Windows.Controls;
using CryptoInspection.Archive.ViewModels;
using Microsoft.Win32;

namespace CryptoInspection.Archive.Views
{
    public partial class ReceiveView : UserControl
    {
        public ReceiveView()
        {
            InitializeComponent();
        }

        /// <summary>File picking is view work; the view model only sees the path.</summary>
        private void OnBrowse(object sender, System.Windows.RoutedEventArgs e)
        {
            ReceiveViewModel model = DataContext as ReceiveViewModel;
            if (model == null)
            {
                return;
            }

            OpenFileDialog dialog = new OpenFileDialog
            {
                Filter = "Crypto inspection package (*.cvz)|*.cvz|All files (*.*)|*.*",
                CheckFileExists = true
            };
            if (dialog.ShowDialog() == true)
            {
                model.PackagePath = dialog.FileName;
            }
        }
    }
}
