using System;
using System.Collections.Generic;
using System.Globalization;
using System.Web.Script.Serialization;

namespace CryptoInspection.Archive.Sync
{
    /// <summary>
    /// A thin reader over `JavaScriptSerializer`, which ships with the .NET
    /// Framework. Parsing the phone's payload therefore costs the installer
    /// nothing, and there is no third party JSON library to keep current.
    /// </summary>
    public class JsonObject
    {
        private readonly Dictionary<string, object> _values;

        public JsonObject(Dictionary<string, object> values)
        {
            _values = values ?? new Dictionary<string, object>();
        }

        public static JsonObject Parse(string text)
        {
            JavaScriptSerializer serializer = new JavaScriptSerializer
            {
                MaxJsonLength = int.MaxValue,
                RecursionLimit = 200
            };
            Dictionary<string, object> values =
                serializer.Deserialize<Dictionary<string, object>>(text);
            return new JsonObject(values);
        }

        public bool Has(string key)
        {
            return _values.ContainsKey(key) && _values[key] != null;
        }

        public string String(string key)
        {
            object value;
            if (!_values.TryGetValue(key, out value) || value == null)
            {
                return null;
            }

            return Convert.ToString(value, CultureInfo.InvariantCulture);
        }

        public long Long(string key)
        {
            return NullableLong(key) ?? 0L;
        }

        public long? NullableLong(string key)
        {
            object value;
            if (!_values.TryGetValue(key, out value) || value == null)
            {
                return null;
            }

            try
            {
                return Convert.ToInt64(value, CultureInfo.InvariantCulture);
            }
            catch (Exception)
            {
                return null;
            }
        }

        public int Int(string key)
        {
            return (int)Long(key);
        }

        public double? NullableDouble(string key)
        {
            object value;
            if (!_values.TryGetValue(key, out value) || value == null)
            {
                return null;
            }

            try
            {
                return Convert.ToDouble(value, CultureInfo.InvariantCulture);
            }
            catch (Exception)
            {
                return null;
            }
        }

        public List<JsonObject> Array(string key)
        {
            List<JsonObject> items = new List<JsonObject>();
            object value;
            if (!_values.TryGetValue(key, out value) || value == null)
            {
                return items;
            }

            object[] array = value as object[];
            if (array == null)
            {
                List<object> list = value as List<object>;
                if (list == null)
                {
                    return items;
                }

                array = list.ToArray();
            }

            foreach (object element in array)
            {
                Dictionary<string, object> map = element as Dictionary<string, object>;
                if (map != null)
                {
                    items.Add(new JsonObject(map));
                }
            }

            return items;
        }

        /// <summary>Serialises a small request body such as the ack payload.</summary>
        public static string Write(object value)
        {
            JavaScriptSerializer serializer = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
            return serializer.Serialize(value);
        }
    }
}
