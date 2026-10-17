<?php
declare(strict_types=1);

/**
 * زمان کار اکیپ: ساعت اداری، تعطیلات و بازه‌های روز. اضافه‌کار هر ثبتی است که
 * بیرون از ساعت اداری روزهای کاری (شنبه تا چهارشنبه) یا در پنجشنبه، جمعه و
 * تعطیلات رسمی افتاده. تعطیلات ثابت شمسی در کد است؛ تعطیلات قمری که هر سال
 * جابه‌جا می‌شوند را مدیر در جدول holidays می‌نویسد.
 */
final class CrewCalendar
{
    public const TABLES = [
        "CREATE TABLE IF NOT EXISTS holidays (
           day        CHAR(10)     NOT NULL PRIMARY KEY,
           title      VARCHAR(191) NOT NULL,
           created_at BIGINT       NOT NULL
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        "CREATE TABLE IF NOT EXISTS crew_overtime (
           member_key VARCHAR(200) NOT NULL,
           from_day   CHAR(10)     NOT NULL,
           to_day     CHAR(10)     NOT NULL,
           hours      DECIMAL(6,1) NOT NULL,
           note       VARCHAR(500) NULL,
           user_id    CHAR(36)     NULL,
           updated_at BIGINT       NOT NULL,
           PRIMARY KEY (member_key, from_day, to_day)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    ];

    public const ZONE = 'Asia/Tehran';

    /** تعطیلات ثابت شمسی، [ماه، روز] => عنوان. */
    public const FIXED = [
        '01-01' => 'نوروز', '01-02' => 'نوروز', '01-03' => 'نوروز', '01-04' => 'نوروز',
        '01-12' => 'روز جمهوری اسلامی', '01-13' => 'روز طبیعت',
        '03-14' => 'رحلت امام خمینی', '03-15' => 'قیام ۱۵ خرداد',
        '11-22' => 'پیروزی انقلاب اسلامی', '12-29' => 'ملی شدن صنعت نفت',
    ];

    /** بازه‌های روز با ساعت شروع هر کدام (به دقیقه)؛ هر بازه تا شروع بعدی. */
    public const BUCKETS = [
        'midnight' => [0, 'نیمه‌شب (۰ تا ۶)'],
        'morning' => [360, 'صبح (۶ تا ۱۱)'],
        'noon' => [660, 'ظهر (۱۱ تا ۱۴)'],
        'afternoon' => [840, 'عصر (۱۴ تا ۱۸)'],
        'night' => [1080, 'شب (۱۸ تا ۲۴)'],
    ];

    /** @param array<string, string> $extra تعطیلات نوشته‌شده مدیر، روز شمسی «۱۴۰۵-۰۷-۱۲» => عنوان */
    public function __construct(
        private readonly int $officeStartMin = 420,
        private readonly int $officeEndMin = 870,
        private readonly array $extra = []
    ) {
    }

    public static function fromSettings(): self
    {
        $s = FieldSettings::all();
        $extra = [];
        foreach (Db::all('SELECT day, title FROM holidays') as $row) {
            $extra[(string) $row['day']] = (string) $row['title'];
        }
        return new self((int) $s['office_start_min'], (int) $s['office_end_min'], $extra);
    }

    /** روز شمسی یک لحظه، به وقت ایران: «1405-07-12». */
    public static function day(int $millis): string
    {
        [$y, $m, $d] = Jalali::fromGregorian(...self::gregorian($millis));
        return sprintf('%04d-%02d-%02d', $y, $m, $d);
    }

    /** دقیقه از نیمه‌شب، به وقت ایران. */
    public static function minuteOfDay(int $millis): int
    {
        $t = self::local($millis);
        return (int) $t->format('G') * 60 + (int) $t->format('i');
    }

    public static function bucket(int $millis): string
    {
        $minute = self::minuteOfDay($millis);
        $found = 'midnight';
        foreach (self::BUCKETS as $key => [$start]) {
            if ($minute >= $start) {
                $found = $key;
            }
        }
        return $found;
    }

    /** عنوان تعطیلی روز، یا تهی برای روز کاری. پنجشنبه تعطیل رسمی نیست ولی اداری هم نیست. */
    public function holiday(int $millis): ?string
    {
        $day = self::day($millis);
        if ((int) self::local($millis)->format('w') === 5) {
            return 'جمعه';
        }
        return $this->extra[$day] ?? self::FIXED[substr($day, 5)] ?? null;
    }

    /** بیرون از ساعت اداری: تعطیل، پنجشنبه، یا پیش و پس از ساعت کار روز کاری. */
    public function overtime(int $millis): bool
    {
        if ($this->holiday($millis) !== null || (int) self::local($millis)->format('w') === 4) {
            return true;
        }
        $minute = self::minuteOfDay($millis);
        return $minute < $this->officeStartMin || $minute >= $this->officeEndMin;
    }

    /**
     * ساعت‌هایی از بازه [از، تا] که بیرون از ساعت اداری است؛ کار یک روز هرگز
     * از نیمه‌شب نمی‌گذرد، پس بازه در یک روز حساب می‌شود.
     */
    public function overtimeHours(int $from, int $to): float
    {
        if ($to <= $from) {
            return 0.0;
        }
        $span = ($to - $from) / 60000;
        if ($this->holiday($from) !== null || (int) self::local($from)->format('w') === 4) {
            return round($span / 60, 1);
        }
        $a = self::minuteOfDay($from);
        $b = $a + $span;
        $inside = max(0, min($b, $this->officeEndMin) - max($a, $this->officeStartMin));
        return round(max(0, $span - $inside) / 60, 1);
    }

    /** روز کاری: شنبه تا چهارشنبه، نه تعطیل. مهلت‌های گردش پرونده با همین شمرده می‌شوند. */
    public function workday(int $millis): bool
    {
        return (int) self::local($millis)->format('w') !== 4 && $this->holiday($millis) === null;
    }

    /** لحظه‌ای که $days روز کاری از $from گذشته است (همان ساعت روز). */
    public function addWorkDays(int $from, int $days): int
    {
        $t = $from;
        for ($left = max(0, $days); $left > 0;) {
            $t += 86_400_000;
            if ($this->workday($t)) {
                $left--;
            }
        }
        return $t;
    }

    private static function local(int $millis): DateTimeImmutable
    {
        return (new DateTimeImmutable('@' . intdiv($millis, 1000)))->setTimezone(new DateTimeZone(self::ZONE));
    }

    /** @return array{0:int,1:int,2:int} */
    private static function gregorian(int $millis): array
    {
        $t = self::local($millis);
        return [(int) $t->format('Y'), (int) $t->format('n'), (int) $t->format('j')];
    }
}
