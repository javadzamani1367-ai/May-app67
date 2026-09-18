namespace CryptoInspection.Archive.Util
{
    /// <summary>Names for the coded values shared with the phone's schema.</summary>
    public static class Labels
    {
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
