using System;

namespace CryptoInspection.Archive.Util
{
    /// <summary>
    /// Jalali conversion, the same Borkowski leap table the phone uses. The
    /// database holds unix milliseconds only; every Jalali value in this app is
    /// produced here, for display and for report headers.
    /// </summary>
    public static class PersianDate
    {
        private static readonly int[] Breaks =
        {
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
        };

        private static readonly DateTime Epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);

        /// <summary>
        /// Tehran, fixed at +03:30. Iran dropped daylight saving in 2022 and the
        /// archive only ever sees data recorded after that, so a fixed offset is
        /// exact here and avoids the invalid local midnights the old DST rules
        /// produced (the switch happened at 24:00).
        /// </summary>
        public static TimeSpan Offset
        {
            get { return TimeSpan.FromMinutes(210); }
        }

        public struct Jalali
        {
            public int Year;
            public int Month;
            public int Day;

            public Jalali(int year, int month, int day)
            {
                Year = year;
                Month = month;
                Day = day;
            }
        }

        private static void Calculate(int jy, out int leap, out int gy, out int march)
        {
            gy = jy + 621;
            int leapJ = -14;
            int jp = Breaks[0];
            if (jy < jp || jy >= Breaks[Breaks.Length - 1])
            {
                throw new ArgumentOutOfRangeException("jy");
            }

            int jump = 0;
            for (int i = 1; i < Breaks.Length; i++)
            {
                int jm = Breaks[i];
                jump = jm - jp;
                if (jy < jm)
                {
                    break;
                }

                leapJ += (jump / 33) * 8 + (jump % 33) / 4;
                jp = jm;
            }

            int n = jy - jp;
            leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4;
            if (jump % 33 == 4 && jump - n == 4)
            {
                leapJ += 1;
            }

            int leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150;
            march = 20 + leapJ - leapG;

            if (jump - n < 6)
            {
                n = n - jump + ((jump + 4) / 33) * 33;
            }

            leap = (((n + 1) % 33) - 1) % 4;
            if (leap == -1)
            {
                leap = 4;
            }
        }

        private static int GregorianToDayNumber(int gy, int gm, int gd)
        {
            int d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4
                + (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408;
            d -= ((((gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4) - 752;
            return d;
        }

        private static void DayNumberToGregorian(int dayNumber, out int gy, out int gm, out int gd)
        {
            int j = 4 * dayNumber + 139361631;
            j += (((4 * dayNumber + 183187720) / 146097) * 3) / 4 * 4 - 3908;
            int i = ((j % 1461) / 4) * 5 + 308;
            gd = ((i % 153) / 5) + 1;
            gm = ((i / 153) % 12) + 1;
            gy = j / 1461 - 100100 + (8 - gm) / 6;
        }

        private static int JalaliToDayNumber(int jy, int jm, int jd)
        {
            int leap, gy, march;
            Calculate(jy, out leap, out gy, out march);
            return GregorianToDayNumber(gy, 3, march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1;
        }

        private static Jalali DayNumberToJalali(int dayNumber)
        {
            int gy, gm, gd;
            DayNumberToGregorian(dayNumber, out gy, out gm, out gd);
            int jy = gy - 621;
            int leap, calGy, march;
            Calculate(jy, out leap, out calGy, out march);
            int firstDay = GregorianToDayNumber(calGy, 3, march);
            int k = dayNumber - firstDay;
            if (k >= 0)
            {
                if (k <= 185)
                {
                    return new Jalali(jy, 1 + k / 31, (k % 31) + 1);
                }

                k -= 186;
            }
            else
            {
                jy -= 1;
                k += 179;
                if (leap == 1)
                {
                    k += 1;
                }
            }

            return new Jalali(jy, 7 + k / 30, (k % 30) + 1);
        }

        public static Jalali FromGregorian(int gy, int gm, int gd)
        {
            return DayNumberToJalali(GregorianToDayNumber(gy, gm, gd));
        }

        public static bool IsLeapYear(int jy)
        {
            int leap, gy, march;
            Calculate(jy, out leap, out gy, out march);
            return leap == 0;
        }

        public static int MonthLength(int jy, int jm)
        {
            if (jm <= 6)
            {
                return 31;
            }

            if (jm <= 11)
            {
                return 30;
            }

            return IsLeapYear(jy) ? 30 : 29;
        }

        public static Jalali Of(long epochMillis)
        {
            DateTime local = Epoch.AddMilliseconds(epochMillis) + Offset;
            return FromGregorian(local.Year, local.Month, local.Day);
        }

        public static long ToEpochMillis(int jy, int jm, int jd)
        {
            int dayNumber = JalaliToDayNumber(jy, jm, jd);
            int gy, gm, gd;
            DayNumberToGregorian(dayNumber, out gy, out gm, out gd);
            DateTime utcMidnight = new DateTime(gy, gm, gd, 0, 0, 0, DateTimeKind.Utc) - Offset;
            return (long)(utcMidnight - Epoch).TotalMilliseconds;
        }

        /// <summary>`۱۴۰۴/۰۶/۱۴` — the display form used everywhere.</summary>
        public static string Format(long? epochMillis)
        {
            if (!epochMillis.HasValue)
            {
                return string.Empty;
            }

            Jalali j = Of(epochMillis.Value);
            return PersianNumbers.ToPersian(
                string.Format("{0:0000}/{1:00}/{2:00}", j.Year, j.Month, j.Day));
        }

        public static string FormatWithTime(long epochMillis)
        {
            DateTime local = Epoch.AddMilliseconds(epochMillis) + Offset;
            return Format(epochMillis) + " - " +
                PersianNumbers.ToPersian(string.Format("{0:00}:{1:00}", local.Hour, local.Minute));
        }

        /// <summary>Parses `۱۴۰۴/۰۶/۰۱` or `1404-06-01` into unix milliseconds.</summary>
        public static long? TryParse(string text)
        {
            if (string.IsNullOrWhiteSpace(text))
            {
                return null;
            }

            string[] parts = PersianNumbers.ToLatin(text).Split('/', '-', '.');
            if (parts.Length != 3)
            {
                return null;
            }

            int y, m, d;
            if (!int.TryParse(parts[0].Trim(), out y) ||
                !int.TryParse(parts[1].Trim(), out m) ||
                !int.TryParse(parts[2].Trim(), out d))
            {
                return null;
            }

            if (m < 1 || m > 12 || d < 1 || d > MonthLength(y, m))
            {
                return null;
            }

            return ToEpochMillis(y, m, d);
        }

        public static long EndOfDay(long epochMillis)
        {
            Jalali j = Of(epochMillis);
            return ToEpochMillis(j.Year, j.Month, j.Day) + 86400000L - 1;
        }

        public static long StartOfDay(long epochMillis)
        {
            Jalali j = Of(epochMillis);
            return ToEpochMillis(j.Year, j.Month, j.Day);
        }

        public static long NowMillis()
        {
            return (long)(DateTime.UtcNow - Epoch).TotalMilliseconds;
        }
    }
}
