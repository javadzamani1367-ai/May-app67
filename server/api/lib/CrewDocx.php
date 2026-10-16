<?php
declare(strict_types=1);

/**
 * گزارش Word عملکرد اکیپ برای مدیر و مدیرعامل: خلاصه به زبان آدم، جدول هر نفر،
 * نمودار ساعات روز و نوع بازرسی‌ها (میله‌هایی از نویسه که در Word راست‌به‌چپ
 * درست می‌مانند)، کار روزانه، نقشه بازدیدها با جدول شماره‌ها، ریز هر نفر و
 * تعریف‌ها، و جای امضا. فرم رسمی است: نام نرم‌افزار ندارد.
 */
final class CrewDocx
{
    private const BAR = 18;
    private const BAR_WIDTHS = [30, 46, 12, 12];

    /** @return list<array{0:string, 1:mixed}> بلوک‌های Docx؛ $mapJpeg مسیر نقشه، اگر ساخته شد */
    public static function blocks(array $report, array $approved, ?string $mapJpeg, CrewCalendar $cal): array
    {
        $fa = static fn ($v): string => Jalali::digits((string) $v);
        $members = $report['members'];
        $c = CrewMap::collect($members);
        $inspections = array_values(array_filter($c['events'], static fn (array $e): bool => $e['inspection']));
        $period = Jalali::format($report['from'], false) . ' تا ' . Jalali::format($report['to'], false);
        $blocks = [['h', 'گزارش عملکرد اکیپ‌های میدانی'], ['p', 'بازه گزارش: ' . $period . ' · تاریخ تهیه: ' . Jalali::format(Db::now())]];
        if ($members === []) {
            return [...$blocks, ['p', 'در این بازه کاری با اکیپ ثبت نشده است.']];
        }
        $blocks[] = ['h', 'خلاصه'];
        $blocks[] = ['p', self::narrative($report, $c, $inspections, $cal)];
        $blocks[] = ['grid', [['نام و نقش', 'روز کار', 'بازدید', 'بازرسی', 'شروع (میانگین)', 'پایان (میانگین)',
            'اضافه‌کار محاسبه‌شده', 'اضافه‌کار تأییدشده', 'بیرون از شهر'],
            array_map(static fn (array $m): array => [$m['name'] . ' (' . implode('، ', $m['roles']) . ')', $fa($m['total']['days']),
                $fa($m['total']['visits']), $fa($m['total']['inspections']), CrewDays::clock($m['total']['avg_start']),
                CrewDays::clock($m['total']['avg_end']), $fa($m['total']['overtime']) . ' ساعت',
                isset($approved[$m['key']]) ? $fa($approved[$m['key']]['hours']) . ' ساعت' : '—', $fa($m['total']['outside'])], $members),
            [22, 8, 8, 8, 10, 10, 12, 12, 10]]];

        $byBucket = array_fill_keys(array_keys(CrewCalendar::BUCKETS), 0);
        $byType = array_fill_keys(array_keys(CrewWork::TYPE_NAMES), 0);
        $byDay = [];
        foreach ($inspections as $e) {
            $byBucket[CrewCalendar::bucket($e['at'])]++;
            $byType[$e['type']]++;
            $byDay[CrewCalendar::day($e['at'])] = ($byDay[CrewCalendar::day($e['at'])] ?? 0) + 1;
        }
        $blocks[] = ['h', 'بازرسی‌ها در ساعات شبانه‌روز'];
        $blocks[] = ['grid', [['بازه', 'نمودار', 'تعداد', 'سهم'], self::bars(array_combine(
            array_map(static fn (array $b): string => $b[1], CrewCalendar::BUCKETS), array_values($byBucket))), self::BAR_WIDTHS]];
        $blocks[] = ['h', 'نوع بازرسی‌ها'];
        $blocks[] = ['grid', [['نوع', 'نمودار', 'تعداد', 'سهم'], self::bars(array_combine(array_values(CrewWork::TYPE_NAMES), array_values($byType))), self::BAR_WIDTHS]];
        ksort($byDay);
        $blocks[] = ['h', 'کار روزانه همه اکیپ‌ها'];
        $dayLabels = [];
        foreach ($byDay as $day => $n) {
            $millis = Jalali::startOfDay(...array_map('intval', explode('-', (string) $day))) + 43_200_000;
            $dayLabels[CrewSheets::weekday($millis) . ' ' . $fa(str_replace('-', '/', (string) $day))
                . ($cal->holiday($millis) !== null ? ' (تعطیل)' : '')] = $n;
        }
        $blocks[] = ['grid', [['روز', 'نمودار بازرسی‌ها', 'تعداد', 'سهم'], self::bars($dayLabels), self::BAR_WIDTHS]];

        $blocks[] = ['h', 'نقشه بازدیدها و بازرسی‌ها'];
        if ($mapJpeg !== null) {
            $blocks[] = ['image', [$mapJpeg, 'دایره‌های شماره‌دار: بازدیدها (جدول زیر)؛ نقطه‌های رنگی: بازرسی‌ها؛ چهارگوش‌های خاکستری: شهرها']];
        }
        $blocks[] = ['grid', [['رنگ نقطه', 'معنا'], [['آبی', 'آمپرگیری تابلو'], ['فیروزه‌ای', 'آمپرگیری تیر'],
            ['نارنجی', 'ترموویژن'], ['سرخ', 'بازرسی رمزارز'], ['بنفش', 'بازرسی برق غیرمجاز'], ['کم‌رنگ', 'تیر بدون آمپر (فقط موقعیت)']]]];
        $blocks[] = ['grid', [['شماره', 'تاریخ و ساعت', 'تابلو یا مورد', 'نزدیک‌ترین آبادی', 'بازرسی'],
            array_map(static fn (array $v): array => [$fa($v['n']), Jalali::format($v['first']) . ' تا ' . CrewDays::clock(CrewCalendar::minuteOfDay($v['last'])),
                $v['label'], $v['place'] ?? '—', $fa($v['inspections'])], $c['visits']), [8, 34, 20, 26, 12]]];

        foreach ($members as $m) {
            $t = $m['total'];
            $blocks[] = ['h', $m['name'] . ' — ' . implode('، ', $m['roles'])];
            $blocks[] = ['table', [
                ['روزهای کار', $fa($t['days']) . ($t['holidays'] > 0 ? ' (' . $fa($t['holidays']) . ' روز تعطیل)' : '')],
                ['بازدید و بازرسی', $fa($t['visits']) . ' بازدید، ' . $fa($t['inspections']) . ' بازرسی'],
                ['تفکیک بازرسی‌ها', implode('، ', array_filter(array_map(static fn ($k, $n) => $n > 0 ? CrewWork::TYPE_NAMES[$k] . ' ' . Jalali::digits((string) $n) : null,
                    array_keys($t['types']), $t['types'])))],
                ['مشترکان نامتعارف کشف‌شده', $fa($t['subscribers'])],
                ['شروع و پایان کار', 'میانگین ' . CrewDays::clock($t['avg_start']) . ' تا ' . CrewDays::clock($t['avg_end'])
                    . '؛ زودترین شروع ' . CrewDays::clock($t['earliest']) . '، دیرترین پایان ' . CrewDays::clock($t['latest'])],
                ['ساعات روز', implode('، ', array_map(static fn ($k, $n) => CrewCalendar::BUCKETS[$k][1] . ': ' . Jalali::digits((string) $n),
                    array_keys($t['buckets']), $t['buckets']))],
                ['اضافه‌کار', 'محاسبه‌شده ' . $fa($t['overtime']) . ' ساعت، ' . $fa($t['overtime_inspections']) . ' بازرسی بیرون از ساعت اداری'
                    . (isset($approved[$m['key']]) ? '؛ تأیید مدیر: ' . $fa($approved[$m['key']]['hours']) . ' ساعت '
                        . $approved[$m['key']]['note'] : '')],
                ['بیرون از محدوده شهر', $fa($t['outside']) . ' بازرسی'],
                ['مسافت تقریبی بین نقطه‌ها', $fa($t['km']) . ' کیلومتر'],
            ]];
            $blocks[] = ['grid', [['تاریخ', 'شروع', 'پایان', 'بازدید', 'بازرسی', 'اضافه‌کار', 'بیرون شهر', 'آبادی‌ها'],
                array_map(static fn (array $d): array => [CrewSheets::weekday($d['first']) . ' ' . $fa(CrewSheets::date($d['first']))
                    . ($d['holiday'] !== null ? ' (' . $d['holiday'] . ')' : ''),
                    $fa(CrewSheets::time($d['first'])), $fa(CrewSheets::time($d['last'])), $fa($d['visits']), $fa($d['inspections']),
                    $fa($d['overtime']), $fa($d['outside']), implode('، ', $d['places'])], $m['days']),
                [22, 9, 9, 9, 9, 10, 9, 23]]];
        }
        $blocks[] = ['h', 'تعریف‌ها'];
        foreach (self::DEFINITIONS as $text) {
            $blocks[] = ['p', $text];
        }
        $blocks[] = ['table', [['تهیه‌کننده', ''], ['تأیید مدیر', ''], ['تأیید مدیرعامل', '']]];
        return $blocks;
    }

    private const DEFINITIONS = [
        'بازدید: رفتن اکیپ سر یک تابلو و همه کاری که از همان تابلو و فیدرهای فشار ضعیف خروجی‌اش انجام شده (آمپرگیری تابلو، آمپرگیری تیربه‌تیر، ترموویژن). هر بازرسی رمزارز یا برق غیرمجاز یک بازدید جداست.',
        'بازرسی: هر آمپرگیری تابلو یا تیر، هر تابلو یا تیری که ترموویژن شده، و هر بازرسی رمزارز یا برق غیرمجاز.',
        'زمان‌ها لحظه ثبت موقعیت هر تابلو و تیر روی نقشه است؛ ردیابی پیوسته در کار نیست. شروع کار هر روز اولین ثبت، و پایان آن آخرین ثبت است.',
        'اضافه‌کار: کار بیرون از ساعت اداری روزهای شنبه تا چهارشنبه، و همه کار پنجشنبه، جمعه و تعطیلات رسمی. ساعت محاسبه‌شده فاصله اولین تا آخرین ثبت روز است که بیرون از ساعت اداری افتاده؛ ساعت تأییدشده را مدیر تعیین می‌کند.',
        'بیرون از محدوده شهر: نقطه‌ای که در شعاع ۴ کیلومتری مرکز هیچ شهر و ۲ کیلومتری هیچ شهرکی نیست (از نقشه آفلاین استان).',
        'هر عضوی که در اکیپ یک کار نام برده شده، همه آن کار را به نام خود دارد.',
    ];

    /** نمودار میله‌ای از نویسه؛ هر سطر [عنوان، میله، تعداد، سهم]. */
    private static function bars(array $values): array
    {
        $max = max([1, ...array_values($values)]);
        $total = max(1, array_sum($values));
        $rows = [];
        foreach ($values as $label => $n) {
            $rows[] = [(string) $label, str_repeat('█', (int) round($n / $max * self::BAR)) ?: '·', Jalali::digits((string) $n),
                Jalali::digits((string) round($n * 100 / $total)) . '٪'];
        }
        return $rows;
    }

    private static function narrative(array $report, array $c, array $inspections, CrewCalendar $cal): string
    {
        $fa = static fn ($v): string => Jalali::digits((string) $v);
        $n = max(1, count($inspections));
        $over = count(array_filter($inspections, static fn (array $e): bool => $cal->overtime($e['at'])));
        $members = $report['members'];
        $days = count(array_unique(array_map(static fn (array $e): string => CrewCalendar::day($e['at']), $c['events'])));
        $buckets = array_count_values(array_map(static fn (array $e): string => CrewCalendar::bucket($e['at']), $inspections));
        arsort($buckets);
        $top = $members[0];
        $text = 'در این بازه ' . $fa(count($members)) . ' نفر از اکیپ‌ها در ' . $fa($days) . ' روز، ' . $fa(count($c['visits']))
            . ' بازدید و ' . $fa(count($inspections)) . ' بازرسی ثبت کرده‌اند. ' . $fa(round(($n - $over) * 100 / $n))
            . '٪ بازرسی‌ها در ساعت اداری و ' . $fa(round($over * 100 / $n)) . '٪ در اضافه‌کار (بیرون از ساعت اداری، پنجشنبه و تعطیل) بوده است.';
        if ($report['geo']) {
            $outside = count(array_filter($inspections, static fn (array $e): bool => ($e['outside'] ?? null) === true));
            $text .= ' ' . $fa($outside) . ' بازرسی (' . $fa(round($outside * 100 / $n)) . '٪) بیرون از محدوده شهرها بوده است.';
        }
        if ($buckets !== []) {
            $text .= ' بیشترین کار در بازه «' . CrewCalendar::BUCKETS[array_key_first($buckets)][1] . '» انجام شده است.';
        }
        return $text . ' پرکارترین عضو: ' . $top['name'] . ' با ' . $fa($top['total']['inspections']) . ' بازرسی در '
            . $fa($top['total']['days']) . ' روز.';
    }
}
