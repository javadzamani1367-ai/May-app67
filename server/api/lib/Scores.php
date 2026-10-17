<?php
declare(strict_types=1);

/**
 * ریاضی امتیاز ۱۰۰: هر امتیاز میانگین وزنی چند جزء است و هر جزء خودش از ۰ تا ۱۰۰.
 * جزئی که داده کافی ندارد (مثلاً هیچ پرونده‌ای مهلتش نرسیده) کنار می‌رود و وزنش
 * میان بقیه پخش می‌شود؛ امتیازی که هیچ جزئی ندارد «—» است، نه صفر — صفر یعنی بد
 * کار کرده، و کسی که کاری به او نرسیده بد کار نکرده.
 *
 * وزن‌ها و آستانه‌ها اینجا هستند و صفحه امتیازها همین‌ها را برای خواننده توضیح
 * می‌دهد، تا امتیاز جعبه سیاه نباشد.
 */
final class Scores
{
    /** هر جزء: [کلید، وزن، توضیح]. */
    public const EXPERT = [
        ['timely', 40, 'بازدید در مهلت ارجاع'],
        ['quality', 30, 'تأیید بی برگشت برای اصلاح'],
        ['volume', 30, 'شمار بازدید، نسبت به پرکارترین کارشناس'],
    ];
    public const EXECUTOR = [
        ['refer', 30, 'سپردن پرونده به کارشناس در مهلت مجری'],
        ['followup', 25, 'پرونده‌هایی که به هشدار مدیر نرسید'],
        ['approval', 25, 'سرعت تأیید یا برگشت (۲۴ ساعت: ۱۰۰، یک هفته: ۰)'],
        ['closure', 20, 'پرونده‌های تأییدشده‌ای که به واحد فرستاده شد'],
    ];
    public const UNIT = [
        ['ontime', 50, 'پاسخ در مهلت'],
        ['progress', 30, 'پیشرفت مراحل اقدام تا مختومه'],
        ['seen', 20, 'دیدن ارسال تا یک روز کاری'],
    ];
    public const AREA = [
        ['executor', 35, 'امتیاز مجری ناحیه'],
        ['expert', 25, 'میانگین امتیاز کارشناسان ناحیه'],
        ['unit', 25, 'امتیاز واحدهای مقصد ناحیه'],
        ['cycle', 15, 'فاصله ثبت تا ارسال به واحد (۱۰ روز: ۱۰۰، ۴۰ روز: ۰)'],
    ];

    /**
     * @param list<array{0:string,1:int,2:string}> $spec
     * @param array<string, float|null> $parts
     */
    public static function combine(array $spec, array $parts): ?int
    {
        $sum = 0.0;
        $weights = 0;
        foreach ($spec as [$key, $weight]) {
            $value = $parts[$key] ?? null;
            if ($value !== null) {
                $sum += max(0.0, min(100.0, $value)) * $weight;
                $weights += $weight;
            }
        }
        return $weights === 0 ? null : (int) round($sum / $weights);
    }

    /** نسبت به درصد؛ تهی وقتی نمونه کمتر از $min است. */
    public static function ratio(int|float $good, int|float $total, int $min = 1): ?float
    {
        return $total < $min || $total <= 0 ? null : round($good / $total * 100, 1);
    }

    /** ۱۰۰ در $best یا بهتر، ۰ در $worst یا بدتر، خطی میان آن‌ها. */
    public static function linear(?float $value, float $best, float $worst): ?float
    {
        if ($value === null) {
            return null;
        }
        if ($value <= $best) {
            return 100.0;
        }
        return $value >= $worst ? 0.0 : round(100 * ($worst - $value) / ($worst - $best), 1);
    }

    /** @param list<float|int> $values */
    public static function median(array $values): ?float
    {
        if ($values === []) {
            return null;
        }
        sort($values);
        $n = count($values);
        return $n % 2 === 1 ? (float) $values[intdiv($n, 2)] : ($values[$n / 2 - 1] + $values[$n / 2]) / 2;
    }

    /** @param list<int|null> $scores */
    public static function average(array $scores): ?float
    {
        $values = array_values(array_filter($scores, static fn ($v) => $v !== null));
        return $values === [] ? null : round(array_sum($values) / count($values), 1);
    }

    /** کلاس رنگ: سبز ۸۰ به بالا، کهربایی ۶۰ به بالا، قرمز کمتر. */
    public static function grade(?int $score): string
    {
        return $score === null ? '' : ($score >= 80 ? 'score-a' : ($score >= 60 ? 'score-b' : 'score-c'));
    }

    /** ردیف‌ها به ترتیب امتیاز، بی‌امتیازها آخر، با رتبه. */
    public static function rank(array $rows): array
    {
        usort($rows, static fn ($a, $b) => [$b['score'] === null ? -1 : $b['score'], $a['name']]
            <=> [$a['score'] === null ? -1 : $a['score'], $b['name']]);
        $rank = 0;
        foreach ($rows as &$row) {
            $row['rank'] = $row['score'] === null ? null : ++$rank;
        }
        return $rows;
    }
}
