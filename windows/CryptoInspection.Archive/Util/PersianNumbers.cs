using System;
using System.Globalization;
using System.Text;

namespace CryptoInspection.Archive.Util
{
    /// <summary>
    /// Digit shaping. Persian digits are for display and printed output only;
    /// the database, file names and the sync protocol keep latin digits.
    /// </summary>
    public static class PersianNumbers
    {
        private const string Persian = "۰۱۲۳۴۵۶۷۸۹";
        private const string Arabic = "٠١٢٣٤٥٦٧٨٩";

        public static string ToPersian(string input)
        {
            if (string.IsNullOrEmpty(input))
            {
                return string.Empty;
            }

            StringBuilder builder = new StringBuilder(input.Length);
            foreach (char c in input)
            {
                builder.Append(c >= '0' && c <= '9' ? Persian[c - '0'] : c);
            }

            return builder.ToString();
        }

        public static string ToPersian(long value)
        {
            return ToPersian(value.ToString(CultureInfo.InvariantCulture));
        }

        public static string ToPersian(int value)
        {
            return ToPersian(value.ToString(CultureInfo.InvariantCulture));
        }

        /// <summary>Trims a trailing `.0` so amperage reads `۶۳`, not `۶۳٫۰`.</summary>
        public static string ToPersian(double? value)
        {
            if (!value.HasValue)
            {
                return string.Empty;
            }

            return ToPersian(Plain(value)).Replace('.', '٫');
        }

        public static string Plain(double? value)
        {
            if (!value.HasValue)
            {
                return string.Empty;
            }

            double v = value.Value;
            if (Math.Abs(v - Math.Truncate(v)) < 0.0000001)
            {
                return ((long)v).ToString(CultureInfo.InvariantCulture);
            }

            return v.ToString(CultureInfo.InvariantCulture);
        }

        /// <summary>Accepts persian or arabic-indic digits typed by the operator.</summary>
        public static string ToLatin(string input)
        {
            if (string.IsNullOrEmpty(input))
            {
                return string.Empty;
            }

            StringBuilder builder = new StringBuilder(input.Length);
            foreach (char c in input)
            {
                int p = Persian.IndexOf(c);
                int a = Arabic.IndexOf(c);
                if (p >= 0)
                {
                    builder.Append((char)('0' + p));
                }
                else if (a >= 0)
                {
                    builder.Append((char)('0' + a));
                }
                else if (c == '٫')
                {
                    builder.Append('.');
                }
                else
                {
                    builder.Append(c);
                }
            }

            return builder.ToString();
        }

        /// <summary>Grouped thousands for readable watt totals: `۱۲٬۵۰۰`.</summary>
        public static string Grouped(double? value)
        {
            if (!value.HasValue)
            {
                return string.Empty;
            }

            string text = ((long)value.Value).ToString("#,0", CultureInfo.InvariantCulture);
            return ToPersian(text.Replace(",", "٬"));
        }
    }
}
