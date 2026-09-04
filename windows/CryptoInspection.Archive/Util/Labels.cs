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
    }
}
