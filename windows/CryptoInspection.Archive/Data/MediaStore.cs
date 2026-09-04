using System;
using System.IO;

namespace CryptoInspection.Archive.Data
{
    /// <summary>
    /// Photos, videos and documents received from phones. Paths in the database
    /// stay relative, exactly as on the phone, so the archive folder can move to
    /// another drive without touching a single row.
    /// </summary>
    public class MediaStore
    {
        public MediaStore(string root)
        {
            Root = root;
            if (!Directory.Exists(root))
            {
                Directory.CreateDirectory(root);
            }
        }

        public string Root { get; private set; }

        public string Resolve(string relativePath)
        {
            if (string.IsNullOrEmpty(relativePath))
            {
                return null;
            }

            return Path.Combine(Root, relativePath.Replace('/', Path.DirectorySeparatorChar));
        }

        public bool Exists(string relativePath)
        {
            string full = Resolve(relativePath);
            return !string.IsNullOrEmpty(full) && File.Exists(full);
        }

        /// <summary>Writes an incoming file, creating the case folder as needed.</summary>
        public void Save(string relativePath, Stream content)
        {
            string full = Resolve(relativePath);
            if (string.IsNullOrEmpty(full))
            {
                throw new ArgumentException("relative path is required", "relativePath");
            }

            string directory = Path.GetDirectoryName(full);
            if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }

            using (FileStream file = File.Create(full))
            {
                content.CopyTo(file);
            }
        }
    }
}
