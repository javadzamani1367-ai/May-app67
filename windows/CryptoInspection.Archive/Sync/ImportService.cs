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
        private readonly ArchiveSettings _settings;

        public ImportService(ReportRepository repository, MediaStore media, ArchiveSettings settings)
        {
            _repository = repository;
            _media = media;
            _settings = settings;
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
            int otherArea = 0;

            foreach (ManifestEntry entry in manifest)
            {
                JsonObject payload = client.Report(entry.Id);
                ReportDetail detail = ReportMapper.ToDetail(payload);
                if (detail.Report == null || string.IsNullOrEmpty(detail.Report.Id))
                {
                    continue;
                }

                // A case of another area is not this archive's, and is not acknowledged:
                // the phone must not take it as received and let it be deleted.
                if (!_settings.Keeps(detail.Report.AreaCode))
                {
                    otherArea++;
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
            if (otherArea > 0)
            {
                log(Strings.Format("msg_other_area", PersianNumbers.ToPersian(otherArea)));
            }

            int acknowledged = client.Acknowledge(received);
            _repository.RecordSource(identity.DeviceId, identity.ExpertCode, highestUpdatedAt, received.Count);
            log(Strings.Format("msg_acked", PersianNumbers.ToPersian(acknowledged)));
            return received.Count;
        }

        /// <summary>
        /// The server route: what changed since the last pull, for the account the
        /// archive signed in with, kept only if it is this archive's area. The
        /// server's own copy is never acknowledged back — nothing there waits on it.
        /// </summary>
        public int PullFromServer(ServerClient client, Action<string> log)
        {
            log(Strings.Get("msg_server_connecting"));
            List<ManifestEntry> manifest = client.Manifest(_settings.ServerPulledAt);
            if (manifest.Count == 0)
            {
                log(Strings.Get("msg_nothing_new"));
                return 0;
            }

            log(Strings.Format("msg_manifest", PersianNumbers.ToPersian(manifest.Count)));
            int received = 0;
            int otherArea = 0;
            int files = 0;
            long highest = _settings.ServerPulledAt;
            foreach (ManifestEntry entry in manifest)
            {
                ReportDetail detail = ReportMapper.ToDetail(client.Report(entry.Id));
                if (entry.UpdatedAt > highest)
                {
                    highest = entry.UpdatedAt;
                }

                if (detail.Report == null || string.IsNullOrEmpty(detail.Report.Id))
                {
                    continue;
                }

                if (!_settings.Keeps(detail.Report.AreaCode))
                {
                    otherArea++;
                    continue;
                }

                log(Strings.Format("msg_importing_report", PersianNumbers.ToPersian(detail.Report.DisplayCode ?? detail.Report.Id)));
                files += DownloadServerFiles(client, "media", detail.Media.ConvertAll(m => new KeyValuePair<string, string>(m.Id, m.FilePath)));
                files += DownloadServerFiles(client, "attachment", detail.Attachments.ConvertAll(a => new KeyValuePair<string, string>(a.Id, a.FilePath)));
                detail.Report.SyncedAt = PersianDate.NowMillis();
                _repository.Merge(detail);
                received++;
            }

            _settings.ServerPulledAt = highest;
            _settings.Save();
            _repository.RecordSource("server", _settings.ServerUser, highest, received);
            log(Strings.Format("msg_media_saved", PersianNumbers.ToPersian(files)));
            if (otherArea > 0)
            {
                log(Strings.Format("msg_other_area", PersianNumbers.ToPersian(otherArea)));
            }

            return received;
        }

        /// <summary>A file the server does not have yet is skipped, not fatal: it comes with a later pull.</summary>
        private int DownloadServerFiles(ServerClient client, string kind, List<KeyValuePair<string, string>> references)
        {
            int saved = 0;
            foreach (KeyValuePair<string, string> file in references)
            {
                if (string.IsNullOrEmpty(file.Value) || _media.Exists(file.Value))
                {
                    continue;
                }

                try
                {
                    client.DownloadFile(kind, file.Key, stream => _media.Save(file.Value, stream));
                    saved++;
                }
                catch (System.Net.WebException)
                {
                }
            }

            return saved;
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
