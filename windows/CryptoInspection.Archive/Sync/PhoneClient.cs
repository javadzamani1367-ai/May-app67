using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>Answer of `GET /ping`: who is on the other end of the cable.</summary>
    public class PhoneIdentity
    {
        public string DeviceId { get; set; }
        public string ExpertCode { get; set; }
        public int SchemaVersion { get; set; }
    }

    /// <summary>One changed case, as listed by `GET /manifest`.</summary>
    public class ManifestEntry
    {
        public string Id { get; set; }
        public long UpdatedAt { get; set; }
    }

    /// <summary>
    /// The pull side of the protocol. `HttpWebRequest` rather than HttpClient:
    /// it behaves identically on Windows 7 with no extra assemblies, and the
    /// calls here are a handful of simple GETs and one POST.
    /// </summary>
    public class PhoneClient
    {
        private const string PairingHeader = "x-pair-code";
        private const int TimeoutMillis = 30000;

        private readonly string _baseAddress;
        private readonly string _pairingCode;

        public PhoneClient(string baseAddress, string pairingCode)
        {
            _baseAddress = (baseAddress ?? string.Empty).TrimEnd('/');
            _pairingCode = pairingCode ?? string.Empty;
        }

        public PhoneIdentity Ping()
        {
            JsonObject json = JsonObject.Parse(GetString("/ping"));
            return new PhoneIdentity
            {
                DeviceId = json.String("device_id"),
                ExpertCode = json.String("expert_code"),
                SchemaVersion = json.Int("schema_version")
            };
        }

        public List<ManifestEntry> Manifest(long since)
        {
            JsonObject json = JsonObject.Parse(GetString("/manifest?since=" + since));
            List<ManifestEntry> entries = new List<ManifestEntry>();
            foreach (JsonObject item in json.Array("reports"))
            {
                entries.Add(new ManifestEntry
                {
                    Id = item.String("id"),
                    UpdatedAt = item.Long("updated_at")
                });
            }

            return entries;
        }

        public JsonObject Report(string id)
        {
            return JsonObject.Parse(GetString("/report/" + Uri.EscapeDataString(id)));
        }

        /// <summary>Streams one media or attachment file into the archive store.</summary>
        public void DownloadFile(string id, Action<Stream> consume)
        {
            HttpWebRequest request = CreateRequest("/media/" + Uri.EscapeDataString(id), "GET");
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (Stream stream = response.GetResponseStream())
            {
                if (stream == null)
                {
                    throw new IOException("empty media response");
                }

                consume(stream);
            }
        }

        /// <summary>Confirms receipt so the phone can move its `synced_at` forward.</summary>
        public int Acknowledge(IEnumerable<string> ids)
        {
            List<string> list = new List<string>(ids);
            if (list.Count == 0)
            {
                return 0;
            }

            string body = JsonObject.Write(new Dictionary<string, object> { { "ids", list } });
            byte[] payload = Encoding.UTF8.GetBytes(body);

            HttpWebRequest request = CreateRequest("/ack", "POST");
            request.ContentType = "application/json; charset=utf-8";
            request.ContentLength = payload.Length;
            using (Stream stream = request.GetRequestStream())
            {
                stream.Write(payload, 0, payload.Length);
            }

            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (StreamReader reader = new StreamReader(response.GetResponseStream() ?? Stream.Null, Encoding.UTF8))
            {
                JsonObject json = JsonObject.Parse(reader.ReadToEnd());
                return json.Int("acknowledged");
            }
        }

        private string GetString(string path)
        {
            HttpWebRequest request = CreateRequest(path, "GET");
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (StreamReader reader = new StreamReader(response.GetResponseStream() ?? Stream.Null, Encoding.UTF8))
            {
                return reader.ReadToEnd();
            }
        }

        private HttpWebRequest CreateRequest(string path, string method)
        {
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(_baseAddress + path);
            request.Method = method;
            request.Timeout = TimeoutMillis;
            request.ReadWriteTimeout = TimeoutMillis;
            request.KeepAlive = false;
            request.Headers[PairingHeader] = _pairingCode;
            return request;
        }
    }
}
