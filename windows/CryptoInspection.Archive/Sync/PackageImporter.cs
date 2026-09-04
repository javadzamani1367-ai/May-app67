using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;
using Org.BouncyCastle.Crypto;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>
    /// The offline route: a `.cvz` carried on a memory card. Same payload as the
    /// HTTP pull, so cases merge through exactly the same code path.
    /// </summary>
    public class PackageImporter
    {
        private const string DataEntry = "data.json";

        private readonly ReportRepository _repository;
        private readonly MediaStore _media;

        public PackageImporter(ReportRepository repository, MediaStore media)
        {
            _repository = repository;
            _media = media;
        }

        public int Import(string packagePath, string password, Action<string> log)
        {
            string workingZip = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N") + ".zip");
            try
            {
                try
                {
                    PackageCrypto.Decrypt(packagePath, password, workingZip);
                }
                catch (InvalidCipherTextException)
                {
                    // A failed GCM tag check means the wrong password or a damaged
                    // file; the operator gets that, not a cryptography stack trace.
                    log(Strings.Get("msg_wrong_password"));
                    return 0;
                }
                catch (InvalidDataException)
                {
                    log(Strings.Get("msg_wrong_password"));
                    return 0;
                }

                return ImportZip(workingZip, log);
            }
            finally
            {
                if (File.Exists(workingZip))
                {
                    File.Delete(workingZip);
                }
            }
        }

        private int ImportZip(string zipPath, Action<string> log)
        {
            using (ZipArchive archive = ZipFile.OpenRead(zipPath))
            {
                ZipArchiveEntry dataEntry = archive.GetEntry(DataEntry);
                if (dataEntry == null)
                {
                    throw new InvalidDataException("package has no " + DataEntry);
                }

                JsonObject manifest;
                using (StreamReader reader = new StreamReader(dataEntry.Open(), Encoding.UTF8))
                {
                    manifest = JsonObject.Parse(reader.ReadToEnd());
                }

                int schemaVersion = manifest.Int("schema_version");
                if (schemaVersion != Schema.Version)
                {
                    log(Strings.Format("msg_schema_mismatch", schemaVersion, Schema.Version));
                    return 0;
                }

                List<ReportDetail> details = new List<ReportDetail>();
                long highestUpdatedAt = 0;
                foreach (JsonObject payload in manifest.Array("reports"))
                {
                    ReportDetail detail = ReportMapper.ToDetail(payload);
                    if (detail.Report == null || string.IsNullOrEmpty(detail.Report.Id))
                    {
                        continue;
                    }

                    detail.Report.SyncedAt = PersianDate.NowMillis();
                    details.Add(detail);
                    if (detail.Report.UpdatedAt > highestUpdatedAt)
                    {
                        highestUpdatedAt = detail.Report.UpdatedAt;
                    }
                }

                int files = ExtractFiles(archive);
                _repository.MergeAll(details);
                _repository.RecordSource(
                    manifest.String("device_id") ?? string.Empty,
                    manifest.String("expert_code"),
                    highestUpdatedAt,
                    details.Count);

                log(Strings.Format("msg_media_saved", PersianNumbers.ToPersian(files)));
                log(Strings.Format("msg_package_imported", PersianNumbers.ToPersian(details.Count)));
                return details.Count;
            }
        }

        /// <summary>
        /// Entries keep the phone's relative paths, so they land in the archive
        /// store unchanged. Paths that try to climb out of the store are refused.
        /// </summary>
        private int ExtractFiles(ZipArchive archive)
        {
            int saved = 0;
            foreach (ZipArchiveEntry entry in archive.Entries)
            {
                if (entry.FullName == DataEntry || entry.FullName.EndsWith("/", StringComparison.Ordinal))
                {
                    continue;
                }

                if (!IsSafeRelativePath(entry.FullName))
                {
                    continue;
                }

                if (_media.Exists(entry.FullName))
                {
                    continue;
                }

                using (Stream stream = entry.Open())
                {
                    _media.Save(entry.FullName, stream);
                }

                saved++;
            }

            return saved;
        }

        private static bool IsSafeRelativePath(string path)
        {
            if (string.IsNullOrEmpty(path) || Path.IsPathRooted(path))
            {
                return false;
            }

            return path.IndexOf("..", StringComparison.Ordinal) < 0 && path.IndexOf(':') < 0;
        }
    }
}
