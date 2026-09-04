using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace CryptoInspection.Archive.Data
{
    /// <summary>
    /// Where the archive keeps its files and how it unlocks the database. The
    /// SQLCipher passphrase is stored with DPAPI under the current Windows user,
    /// so it never sits in plain text next to the database it opens.
    /// </summary>
    public class ArchiveSettings
    {
        private const string FileName = "archive.settings";
        private const string KeyDatabase = "database_path";
        private const string KeyMedia = "media_root";
        private const string KeyExport = "export_root";
        private const string KeyPassword = "database_password";

        public string DatabasePath { get; set; }

        public string MediaRoot { get; set; }

        public string ExportRoot { get; set; }

        public string DatabasePassword { get; set; }

        public static string ProfileDirectory
        {
            get
            {
                return Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "CryptoInspectionArchive");
            }
        }

        public static ArchiveSettings Load()
        {
            string directory = ProfileDirectory;
            if (!Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }

            ArchiveSettings settings = new ArchiveSettings
            {
                DatabasePath = Path.Combine(directory, "archive.db"),
                MediaRoot = Path.Combine(directory, "media"),
                ExportRoot = Path.Combine(directory, "exports"),
                DatabasePassword = string.Empty
            };

            string path = Path.Combine(directory, FileName);
            if (!File.Exists(path))
            {
                return settings;
            }

            foreach (string line in File.ReadAllLines(path, Encoding.UTF8))
            {
                int separator = line.IndexOf('=');
                if (separator <= 0)
                {
                    continue;
                }

                string key = line.Substring(0, separator).Trim();
                string value = line.Substring(separator + 1).Trim();
                switch (key)
                {
                    case KeyDatabase:
                        settings.DatabasePath = value;
                        break;
                    case KeyMedia:
                        settings.MediaRoot = value;
                        break;
                    case KeyExport:
                        settings.ExportRoot = value;
                        break;
                    case KeyPassword:
                        settings.DatabasePassword = Unprotect(value);
                        break;
                }
            }

            return settings;
        }

        public void Save()
        {
            string directory = ProfileDirectory;
            if (!Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }

            List<string> lines = new List<string>
            {
                KeyDatabase + "=" + DatabasePath,
                KeyMedia + "=" + MediaRoot,
                KeyExport + "=" + ExportRoot,
                KeyPassword + "=" + Protect(DatabasePassword)
            };
            File.WriteAllLines(Path.Combine(directory, FileName), lines, Encoding.UTF8);
        }

        private static string Protect(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return string.Empty;
            }

            byte[] encrypted = ProtectedData.Protect(
                Encoding.UTF8.GetBytes(value),
                null,
                DataProtectionScope.CurrentUser);
            return Convert.ToBase64String(encrypted);
        }

        private static string Unprotect(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return string.Empty;
            }

            try
            {
                byte[] plain = ProtectedData.Unprotect(
                    Convert.FromBase64String(value),
                    null,
                    DataProtectionScope.CurrentUser);
                return Encoding.UTF8.GetString(plain);
            }
            catch (Exception)
            {
                // A profile copied from another machine cannot be decrypted; the
                // operator is asked for the passphrase again rather than crashing.
                return string.Empty;
            }
        }
    }
}
