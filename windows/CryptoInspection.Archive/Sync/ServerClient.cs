using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>
    /// The central server, for the archive of an area: the manager or that
    /// area's crypto executor signs in once, and the archive then pulls the
    /// cases the server holds for that account — every area for the manager,
    /// the executor's own areas otherwise. The server decides the scope.
    ///
    /// Routes go through `index.php?route=`, which works on a host with or
    /// without URL rewriting. Windows 7 does not offer TLS 1.2 to .NET by
    /// default; it is switched on here, since a cPanel host refuses anything older.
    /// </summary>
    public class ServerClient
    {
        private const int TimeoutMillis = 60000;

        private readonly string _baseAddress;
        private readonly string _token;

        public ServerClient(string baseAddress, string token)
        {
            _baseAddress = (baseAddress ?? string.Empty).Trim().TrimEnd('/');
            if (_baseAddress.EndsWith("/index.php", StringComparison.OrdinalIgnoreCase))
            {
                _baseAddress = _baseAddress.Substring(0, _baseAddress.Length - "/index.php".Length);
            }

            _token = token;
            ServicePointManager.SecurityProtocol |= SecurityProtocolType.Tls12;
        }

        public bool Configured
        {
            get { return _baseAddress.Length > 0; }
        }

        /// <summary>Signs in and returns the session token; the server's own message on refusal.</summary>
        public string Login(string userCode, string password)
        {
            string body = JsonObject.Write(new Dictionary<string, object>
            {
                { "user_code", userCode ?? string.Empty },
                { "password", password ?? string.Empty },
                { "app", "windows" }
            });
            JsonObject data = Call("auth/login", "POST", body, null);
            return data.String("token");
        }

        public List<ManifestEntry> Manifest(long since)
        {
            JsonObject data = Call("sync/manifest&since=" + since, "GET", null, _token);
            List<ManifestEntry> entries = new List<ManifestEntry>();
            foreach (JsonObject item in data.Array("reports"))
            {
                entries.Add(new ManifestEntry { Id = item.String("id"), UpdatedAt = item.Long("updated_at") });
            }

            return entries;
        }

        public JsonObject Report(string id)
        {
            JsonObject data = Call("sync/report&id=" + Uri.EscapeDataString(id), "GET", null, _token);
            Dictionary<string, object> report = data.Raw("report");
            return report == null ? new JsonObject(new Dictionary<string, object>()) : new JsonObject(report);
        }

        /// <summary>Streams one photo or document of a case; kind is "media" or "attachment".</summary>
        public void DownloadFile(string kind, string id, Action<Stream> consume)
        {
            HttpWebRequest request = CreateRequest("sync/file&kind=" + kind + "&id=" + Uri.EscapeDataString(id), "GET", _token);
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (Stream stream = response.GetResponseStream())
            {
                if (stream == null)
                {
                    throw new IOException("empty file response");
                }

                consume(stream);
            }
        }

        /// <summary>The "data" part of the server's answer; a refusal becomes an exception carrying its Persian message.</summary>
        private JsonObject Call(string route, string method, string body, string token)
        {
            HttpWebRequest request = CreateRequest(route, method, token);
            if (body != null)
            {
                byte[] payload = Encoding.UTF8.GetBytes(body);
                request.ContentType = "application/json; charset=utf-8";
                request.ContentLength = payload.Length;
                using (Stream stream = request.GetRequestStream())
                {
                    stream.Write(payload, 0, payload.Length);
                }
            }

            string text;
            try
            {
                using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
                using (StreamReader reader = new StreamReader(response.GetResponseStream() ?? Stream.Null, Encoding.UTF8))
                {
                    text = reader.ReadToEnd();
                }
            }
            catch (WebException e)
            {
                HttpWebResponse failed = e.Response as HttpWebResponse;
                if (failed == null)
                {
                    throw;
                }

                using (StreamReader reader = new StreamReader(failed.GetResponseStream() ?? Stream.Null, Encoding.UTF8))
                {
                    text = reader.ReadToEnd();
                }
            }

            JsonObject json = JsonObject.Parse(text);
            Dictionary<string, object> error = json.Raw("error");
            if (error != null)
            {
                throw new ServerRefusal(new JsonObject(error).String("message") ?? string.Empty);
            }

            Dictionary<string, object> data = json.Raw("data");
            return new JsonObject(data ?? new Dictionary<string, object>());
        }

        private HttpWebRequest CreateRequest(string route, string method, string token)
        {
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(_baseAddress + "/index.php?route=" + route);
            request.Method = method;
            request.Timeout = TimeoutMillis;
            request.ReadWriteTimeout = TimeoutMillis;
            request.Accept = "application/json";
            if (!string.IsNullOrEmpty(token))
            {
                request.Headers[HttpRequestHeader.Authorization] = "Bearer " + token;
            }

            return request;
        }
    }

    /// <summary>The server said no; the message is its own, already in Persian.</summary>
    public class ServerRefusal : Exception
    {
        public ServerRefusal(string message) : base(message)
        {
        }
    }
}
