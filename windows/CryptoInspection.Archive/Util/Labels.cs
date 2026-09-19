using System.Linq;

namespace CryptoInspection.Archive.Util
{
    /// <summary>Names for the coded values shared with the phone's schema.</summary>
    public static class Labels
    {
        /// <summary>
        /// «ایلام — ناحیه ۴۰۱». Same rule as the phone's CountyLabel: a case
        /// filed before the area was recorded is written as the plain county
        /// name rather than with an empty area beside it.
        /// </summary>
        public static string CountyWithArea(string county, string areaCode)
        {
            string name = (county ?? string.Empty).Trim();
            if (name.Length == 0)
            {
                return string.Empty;
            }

            string area = new string((areaCode ?? string.Empty).Where(char.IsDigit).ToArray());
            if (area.Length == 0)
            {
                return name;
            }

            return Strings.Format("county_with_code", name, PersianNumbers.ToPersian(area));
        }

        public static string ReportType(int code)
        {
            return Strings.Coded("report_type", code);
        }

        public static string Status(int code)
        {
            return Strings.Coded("status", code);
        }

        public static string EntryMethod(int code)
        {
            return Strings.Coded("entry_method", code);
        }

        public static string Organization(int code)
        {
            return Strings.Coded("org", code);
        }

        public static string AttachmentCategory(int code)
        {
            return Strings.Coded("attachment", code);
        }

        public static string DispatchUnit(int code)
        {
            return Strings.Coded("unit", code);
        }

        public static string TapPoint(int? code)
        {
            return code.HasValue ? Strings.Coded("tap", code.Value) : null;
        }

        public static string PhaseType(int? code)
        {
            return code.HasValue ? Strings.Coded("phase", code.Value) : null;
        }

        public static string TariffType(int? code)
        {
            return code.HasValue ? Strings.Coded("tariff", code.Value) : null;
        }

        public static string MeterType(int? code)
        {
            return code.HasValue ? Strings.Coded("meter", code.Value) : null;
        }

        /// <summary>
        /// An unanswered question stays blank. Turning a missing answer into
        /// "no" would put a claim in an official report nobody made.
        /// </summary>
        public static string YesNo(int? code)
        {
            if (!code.HasValue)
            {
                return null;
            }

            return Strings.Get(code.Value == 1 ? "answer_yes" : "answer_no");
        }
    }
}
