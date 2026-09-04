using System;
using System.IO;
using System.Reflection;

namespace CryptoInspection.Archive.Util
{
    /// <summary>
    /// Vazirmatn for the printed output. QuestPDF needs the font bytes, and the
    /// file is optional so the project builds before it has been dropped in.
    /// </summary>
    public static class AppFonts
    {
        public const string FamilyName = "Vazirmatn";

        private static bool _registered;

        public static string RegularPath
        {
            get { return Path.Combine(AssetDirectory, "Vazirmatn-Regular.ttf"); }
        }

        public static string BoldPath
        {
            get { return Path.Combine(AssetDirectory, "Vazirmatn-Bold.ttf"); }
        }

        public static bool IsBundled
        {
            get { return File.Exists(RegularPath); }
        }

        /// <summary>Registers the font with QuestPDF once; a no-op when absent.</summary>
        public static void Register(Action<Stream> register)
        {
            if (_registered || !IsBundled || register == null)
            {
                return;
            }

            using (FileStream stream = File.OpenRead(RegularPath))
            {
                register(stream);
            }

            if (File.Exists(BoldPath))
            {
                using (FileStream stream = File.OpenRead(BoldPath))
                {
                    register(stream);
                }
            }

            _registered = true;
        }

        /// <summary>The family the report should ask for, with a safe fallback.</summary>
        public static string ReportFamily
        {
            get { return IsBundled ? FamilyName : "Tahoma"; }
        }

        private static string AssetDirectory
        {
            get
            {
                string baseDirectory = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location)
                    ?? Environment.CurrentDirectory;
                return Path.Combine(baseDirectory, "Assets", "fonts");
            }
        }
    }
}
