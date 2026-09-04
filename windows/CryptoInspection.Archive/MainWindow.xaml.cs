using System.Windows;
using CryptoInspection.Archive.ViewModels;

namespace CryptoInspection.Archive
{
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
            DataContext = new MainViewModel(App.Database, App.Media, App.Settings);
        }
    }
}
