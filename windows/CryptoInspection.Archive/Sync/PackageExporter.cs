using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>
    /// Writes a `.cvz` from this archive for another area's archive (or the
    /// central one): the cases of one area — or all of them — with their photos
    /// and documents, password-protected exactly as a phone's package is, and
    /// read by <see cref="PackageImporter"/> on the other side.
    /// </summary>
    public class PackageExporter
    {
        private const string DataEntry = "data.json";

        private readonly ReportQueries _queries;
        private readonly MediaStore _media;

        public PackageExporter(ReportQueries queries, MediaStore media)
        {
            _queries = queries;
            _media = media;
        }

        /// <summary>The number of cases written; an empty area code means every case in the archive.</summary>
        public int Export(string areaCode, string password, string packagePath, Action<string> log)
        {
            string area = PersianNumbers.ToLatin(areaCode ?? string.Empty).Trim();
            List<Dictionary<string, object>> reports = new List<Dictionary<string, object>>();
            List<string> files = new List<string>();
            foreach (Report report in _queries.Find(new ReportFilter()))
            {
                if (area.Length > 0 && (report.AreaCode ?? string.Empty).Trim() != area)
                {
                    continue;
                }

                ReportDetail detail = _queries.Load(report.Id);
                if (detail == null)
                {
                    continue;
                }

                reports.Add(ReportWriter.ToJson(detail));
                foreach (KeyValuePair<string, string> file in ReportMapper.FileReferences(detail))
                {
                    if (!string.IsNullOrEmpty(file.Value) && _media.Exists(file.Value))
                    {
                        files.Add(file.Value);
                    }
                }
            }

            string zipPath = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N") + ".zip");
            try
            {
                using (ZipArchive archive = ZipFile.Open(zipPath, ZipArchiveMode.Create))
                {
                    Dictionary<string, object> manifest = new Dictionary<string, object>
                    {
                        { "schema_version", Schema.Version },
                        { "device_id", "archive-" + (area.Length > 0 ? area : "all") },
                        { "expert_code", string.Empty },
                        { "exported_at", PersianDate.NowMillis() },
                        { "reports", reports }
                    };
                    ZipArchiveEntry data = archive.CreateEntry(DataEntry);
                    using (StreamWriter writer = new StreamWriter(data.Open(), new UTF8Encoding(false)))
                    {
                        writer.Write(JsonObject.Write(manifest));
                    }

                    foreach (string relative in files)
                    {
                        archive.CreateEntryFromFile(_media.Resolve(relative), relative.Replace('\\', '/'));
                    }
                }

                PackageCrypto.Encrypt(zipPath, password, packagePath);
            }
            finally
            {
                if (File.Exists(zipPath))
                {
                    File.Delete(zipPath);
                }
            }

            log(Strings.Format("msg_package_exported", PersianNumbers.ToPersian(reports.Count), Path.GetFileName(packagePath)));
            return reports.Count;
        }
    }
}
