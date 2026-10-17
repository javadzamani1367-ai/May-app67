using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using Org.BouncyCastle.Crypto.Engines;
using Org.BouncyCastle.Crypto.Modes;
using Org.BouncyCastle.Crypto.Parameters;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>
    /// Opens the phone's `.cvz` package:
    ///
    ///   CVZ1 | salt(16) | iv(12) | AES-256-GCM ciphertext with the tag appended
    ///
    /// The key is PBKDF2-HMAC-SHA256, 120000 rounds — the same parameters the
    /// phone uses. .NET Framework 4.8 has no AesGcm of its own, so the GCM step
    /// runs through Bouncy Castle; PBKDF2 is native.
    /// </summary>
    public static class PackageCrypto
    {
        private const string Magic = "CVZ1";
        private const int SaltSize = 16;
        private const int IvSize = 12;
        private const int TagBits = 128;
        private const int Iterations = 120000;

        public static void Decrypt(string packagePath, string password, string targetZipPath)
        {
            byte[] encrypted = File.ReadAllBytes(packagePath);
            int headerSize = Magic.Length + SaltSize + IvSize;
            if (encrypted.Length <= headerSize)
            {
                throw new InvalidDataException("package too small");
            }

            string magic = Encoding.ASCII.GetString(encrypted, 0, Magic.Length);
            if (magic != Magic)
            {
                throw new InvalidDataException("not a cvz package");
            }

            byte[] salt = new byte[SaltSize];
            Buffer.BlockCopy(encrypted, Magic.Length, salt, 0, SaltSize);
            byte[] iv = new byte[IvSize];
            Buffer.BlockCopy(encrypted, Magic.Length + SaltSize, iv, 0, IvSize);

            byte[] cipherText = new byte[encrypted.Length - headerSize];
            Buffer.BlockCopy(encrypted, headerSize, cipherText, 0, cipherText.Length);

            byte[] key = DeriveKey(password, salt);
            byte[] plain = DecryptGcm(key, iv, cipherText);
            File.WriteAllBytes(targetZipPath, plain);
        }

        /// <summary>
        /// The same format the other way round, so one area's archive can hand cases to
        /// another's on a memory card: a fresh salt and nonce for every package.
        /// </summary>
        public static void Encrypt(string zipPath, string password, string packagePath)
        {
            byte[] salt = new byte[SaltSize];
            byte[] iv = new byte[IvSize];
            using (RandomNumberGenerator random = RandomNumberGenerator.Create())
            {
                random.GetBytes(salt);
                random.GetBytes(iv);
            }

            byte[] key = DeriveKey(password, salt);
            byte[] plain = File.ReadAllBytes(zipPath);
            GcmBlockCipher cipher = new GcmBlockCipher(new AesEngine());
            cipher.Init(true, new AeadParameters(new KeyParameter(key), TagBits, iv));
            byte[] output = new byte[cipher.GetOutputSize(plain.Length)];
            int written = cipher.ProcessBytes(plain, 0, plain.Length, output, 0);
            written += cipher.DoFinal(output, written);

            using (FileStream file = File.Create(packagePath))
            {
                byte[] magic = Encoding.ASCII.GetBytes(Magic);
                file.Write(magic, 0, magic.Length);
                file.Write(salt, 0, salt.Length);
                file.Write(iv, 0, iv.Length);
                file.Write(output, 0, written);
            }
        }

        private static byte[] DeriveKey(string password, byte[] salt)
        {
            using (Rfc2898DeriveBytes derive =
                new Rfc2898DeriveBytes(password ?? string.Empty, salt, Iterations, HashAlgorithmName.SHA256))
            {
                return derive.GetBytes(32);
            }
        }

        private static byte[] DecryptGcm(byte[] key, byte[] iv, byte[] cipherText)
        {
            GcmBlockCipher cipher = new GcmBlockCipher(new AesEngine());
            cipher.Init(false, new AeadParameters(new KeyParameter(key), TagBits, iv));

            byte[] output = new byte[cipher.GetOutputSize(cipherText.Length)];
            int written = cipher.ProcessBytes(cipherText, 0, cipherText.Length, output, 0);
            written += cipher.DoFinal(output, written);

            if (written == output.Length)
            {
                return output;
            }

            byte[] trimmed = new byte[written];
            Buffer.BlockCopy(output, 0, trimmed, 0, written);
            return trimmed;
        }
    }
}
