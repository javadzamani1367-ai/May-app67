using System;
using System.Collections.Generic;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>
    /// Drives one pull from a phone: handshake, manifest, cases, files, ack.
    /// Nothing is acknowledged until the case and every file it points at are
    /// safely written, so a cable pulled mid-transfer costs a repeat, not data.
    /// </summary>
    public class ImportService
    {
        private readonly ReportRepository _repository;
        private readonly MediaStore _media;

        public ImportService(ReportRepository repository, MediaStore media)
        {
            _repository = repository;
            _media = media;
        }

        /// <summary>Reports progress to the UI as plain, already localised lines.</summary>
        public int Pull(PhoneClient client, Action<string> log)
        {
            log(Strings.Get("msg_connecting"));
            PhoneIdentity identity = client.Ping();
            log(Strings.Format("msg_connected", identity.DeviceId, identity.ExpertCode));

            if (identity.SchemaVersion != Schema.Version)
            {
                log(Strings.Format("msg_schema_mismatch", identity.SchemaVersion, Schema.Version));
                return 0;
            }

            long since = _repository.LastUpdatedFor(identity.DeviceId);
            List<ManifestEntry> manifest = client.Manifest(since);
            if (manifest.Count == 0)
            {
                log(Strings.Get("msg_nothing_new"));
                return 0;
            }

            log(Strings.Format("msg_manifest", PersianNumbers.ToPersian(manifest.Count)));

            List<string> received = new List<string>();
            long highestUpdatedAt = since;
            int fileCount = 0;

            foreach (ManifestEntry entry in manifest)
            {
                JsonObject payload = client.Report(entry.Id);
                ReportDetail detail = ReportMapper.ToDetail(payload);
                if (detail.Report == null || string.IsNullOrEmpty(detail.Report.Id))
                {
                    continue;
                }

                log(Strings.Format("msg_importing_report",
                    PersianNumbers.ToPersian(detail.Report.DisplayCode ?? detail.Report.Id)));

                fileCount += DownloadFiles(client, detail);

                detail.Report.SyncedAt = PersianDate.NowMillis();
                _repository.Merge(detail);
                received.Add(detail.Report.Id);
                if (entry.UpdatedAt > highestUpdatedAt)
                {
                    highestUpdatedAt = entry.UpdatedAt;
                }
            }

            log(Strings.Format("msg_media_saved", PersianNumbers.ToPersian(fileCount)));

            int acknowledged = client.Acknowledge(received);
            _repository.RecordSource(identity.DeviceId, identity.ExpertCode, highestUpdatedAt, received.Count);
            log(Strings.Format("msg_acked", PersianNumbers.ToPersian(acknowledged)));
            return received.Count;
        }

        /// <summary>
        /// Files already present are not fetched again: a case that comes back
        /// because its description changed does not re-download its photos.
        /// </summary>
        private int DownloadFiles(PhoneClient client, ReportDetail detail)
        {
            int saved = 0;
            foreach (KeyValuePair<string, string> file in ReportMapper.FileReferences(detail))
            {
                if (string.IsNullOrEmpty(file.Value) || _media.Exists(file.Value))
                {
                    continue;
                }

                client.DownloadFile(file.Key, stream => _media.Save(file.Value, stream));
                saved++;
            }

            return saved;
        }
    }
}
