using System;
using System.Globalization;
using System.Windows;

namespace CryptoInspection.Archive.Util
{
    /// <summary>
    /// Reads visible text from `Resources/Strings.xaml`. No Persian literal ever
    /// appears in code, exactly as on the phone side.
    /// </summary>
    public static class Strings
    {
        public static string Get(string key)
        {
            if (Application.Current == null)
            {
                return key;
            }

            object value = Application.Current.TryFindResource(key);
            return value as string ?? key;
        }

        public static string Format(string key, params object[] arguments)
        {
            return string.Format(CultureInfo.CurrentCulture, Get(key), arguments);
        }

        /// <summary>Coded values follow the `prefix_code` convention in the dictionary.</summary>
        public static string Coded(string prefix, int code)
        {
            return Get(prefix + "_" + code.ToString(CultureInfo.InvariantCulture));
        }
    }
}
