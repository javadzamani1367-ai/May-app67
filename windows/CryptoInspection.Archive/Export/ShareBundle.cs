using System;
using System.Collections.Generic;
using System.IO;
using CryptoInspection.Archive.Data;

namespace CryptoInspection.Archive.Export
{
    /// <summary>
    /// Everything one case needs to travel over a messenger or a memory stick,
    /// in one folder: the official form as PDF and Word, and beside them every
    /// document and video the form cannot hold. Photos are already inside the
    /// form, so they are not copied again.
    /// </summary>
    public class ShareBundle
    {
        private readonly PdfReportBuilder _pdf;
        private readonly WordReportBuilder _word;
        private readonly MediaStore _media;

        public ShareBundle(PdfReportBuilder pdf, WordReportBuilder word, MediaStore media)
        {
            _pdf = pdf;
            _word = word;
            _media = media;
        }

        /// <summary>Builds the folder under <paramref name="root"/> and returns its path.</summary>
        public string Build(ReportDetail detail, string root, string name)
        {
            string folder = Path.Combine(root, name + "-" + DateTime.Now.ToString("yyyyMMdd-HHmmss"));
            Directory.CreateDirectory(folder);
            _pdf.Build(detail, Path.Combine(folder, name + ".pdf"));
            _word.Build(detail, Path.Combine(folder, name + ".docx"));

            HashSet<string> used = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            int index = 0;
            foreach (Attachment attachment in detail.Attachments)
            {
                index++;
                Copy(attachment.FilePath, folder, string.IsNullOrWhiteSpace(attachment.Title) ? "doc-" + index : attachment.Title, used);
            }

            index = 0;
            foreach (MediaItem item in detail.Media)
            {
                if (item.Type != 1)
                {
                    continue;
                }

                index++;
                Copy(item.FilePath, folder, "video-" + index, used);
            }

            return folder;
        }

        /// <summary>A missing file is skipped; the form still goes.</summary>
        private void Copy(string relativePath, string folder, string title, HashSet<string> used)
        {
            if (string.IsNullOrEmpty(relativePath) || !_media.Exists(relativePath))
            {
                return;
            }

            string safe = title;
            foreach (char invalid in Path.GetInvalidFileNameChars())
            {
                safe = safe.Replace(invalid, '_');
            }

            string extension = Path.GetExtension(relativePath);
            string fileName = safe + extension;
            for (int n = 2; !used.Add(fileName); n++)
            {
                fileName = safe + "-" + n + extension;
            }

            File.Copy(_media.Resolve(relativePath), Path.Combine(folder, fileName), true);
        }
    }
}
