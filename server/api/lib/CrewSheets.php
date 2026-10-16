<?php
declare(strict_types=1);

/**
 * اکسل عملکرد اکیپ: خلاصه هر نفر، کار روزانه، بازدیدها و بازرسی‌ها — عددها
 * عدد می‌مانند تا در اکسل جمع زده شوند.
 */
final class CrewSheets
{
    public const WEEKDAYS = ['یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'];

    /** @return list<array{0:string, 1:list<string>, 2:list<list<string|int|float|null>>}> */
    public static function book(array $report, array $approved): array
    {
        $buckets = array_map(static fn (array $b): string => $b[1], CrewCalendar::BUCKETS);
        $summary = [];
        $daily = [];
        foreach ($report['members'] as $m) {
            $t = $m['total'];
            $ok = $approved[$m['key']] ?? null;
            $summary[] = array_merge([$m['name'], implode('، ', $m['roles']), $t['days'], $t['holidays'], $t['visits'],
                $t['inspections']], array_values($t['types']), [$t['subscribers'],
                self::hm($t['avg_start']), self::hm($t['avg_end']), self::hm($t['earliest']), self::hm($t['latest']),
                $t['hours'], $t['overtime'], $ok['hours'] ?? null, $ok['note'] ?? '', $t['overtime_inspections'], $t['outside']],
                array_values($t['buckets']), [$t['km']]);
            foreach ($m['days'] as $d) {
                $daily[] = array_merge([$m['name'], self::date($d['first']), self::weekday($d['first']), $d['holiday'] ?? '',
                    self::time($d['first']), self::time($d['last']), $d['hours'], $d['visits'], $d['inspections']],
                    array_values($d['types']), [$d['subscribers'], $d['overtime'], $d['overtime_inspections'], $d['outside'], $d['km'],
                    implode('، ', $d['places'])]);
            }
        }
        $collected = CrewMap::collect($report['members']);
        $crewOf = self::crewOf($report['members']);
        $visits = array_map(static fn (array $v): array => [$v['n'], self::date($v['first']), $v['label'], $v['place'] ?? '',
            self::time($v['first']), self::time($v['last']), $v['inspections'],
            implode('، ', array_map(static fn ($k, $n) => CrewWork::TYPE_NAMES[$k] . ' ' . $n, array_keys($v['types']), $v['types'])),
            $v['lat'] === null ? '' : sprintf('%.6f, %.6f', $v['lat'], $v['lon']), implode('، ', $crewOf['visit'][$v['key']] ?? [])],
            $collected['visits']);
        $events = array_map(static fn (array $e): array => [self::date($e['at']), self::time($e['at']),
            CrewWork::TYPE_NAMES[$e['type']] . ($e['inspection'] ? '' : ' (بدون آمپر)'), $e['plate'], $e['code'],
            CrewCalendar::BUCKETS[CrewCalendar::bucket($e['at'])][1],
            $e['lat'] === null ? '' : sprintf('%.6f, %.6f', $e['lat'], $e['lon']),
            implode('، ', $crewOf['event'][$e['code'] . '|' . $e['at']] ?? [])], $collected['events']);
        $types = array_values(CrewWork::TYPE_NAMES);
        return [
            ['خلاصه هر نفر', array_merge(['نام', 'نقش', 'روزهای کار', 'روز تعطیل', 'بازدید', 'بازرسی'], $types,
                ['مشترک نامتعارف', 'میانگین شروع', 'میانگین پایان', 'زودترین شروع', 'دیرترین پایان', 'ساعت کار در میدان',
                    'اضافه‌کار محاسبه‌شده (ساعت)', 'اضافه‌کار تأییدشده (ساعت)', 'توضیح مدیر', 'بازرسی در اضافه‌کار',
                    'بازرسی بیرون از شهر'], array_values($buckets), ['مسافت تقریبی (کیلومتر)']), $summary],
            ['روزانه', array_merge(['نام', 'تاریخ', 'روز', 'تعطیلی', 'شروع', 'پایان', 'ساعت در میدان', 'بازدید', 'بازرسی'], $types,
                ['مشترک نامتعارف', 'اضافه‌کار (ساعت)', 'بازرسی در اضافه‌کار', 'بیرون از شهر', 'مسافت (کیلومتر)', 'آبادی‌ها']), $daily],
            ['بازدیدها', ['شماره روی نقشه', 'تاریخ', 'تابلو یا مورد', 'نزدیک‌ترین آبادی', 'از ساعت', 'تا ساعت', 'بازرسی',
                'تفکیک', 'مختصات', 'اکیپ'], $visits],
            ['بازرسی‌ها', ['تاریخ', 'ساعت', 'نوع', 'پلاک', 'کد مورد', 'بازه روز', 'مختصات', 'اکیپ'], $events],
        ];
    }

    /** چه کسانی در هر بازدید و هر رویداد بودند. */
    private static function crewOf(array $members): array
    {
        $out = ['visit' => [], 'event' => []];
        foreach ($members as $m) {
            foreach ($m['days'] as $d) {
                foreach ($d['visit_list'] as $v) {
                    $out['visit'][$v['key']][] = $m['name'];
                }
                foreach ($d['points'] as $p) {
                    $out['event'][$p['code'] . '|' . $p['at']][] = $m['name'];
                }
            }
        }
        $out['event'] = array_map('array_unique', $out['event']);
        return $out;
    }

    public static function date(int $millis): string
    {
        return str_replace('-', '/', CrewCalendar::day($millis));
    }

    public static function time(int $millis): string
    {
        return sprintf('%02d:%02d', intdiv($m = CrewCalendar::minuteOfDay($millis), 60), $m % 60);
    }

    public static function weekday(int $millis): string
    {
        return self::WEEKDAYS[(int) (new DateTimeImmutable('@' . intdiv($millis, 1000)))
            ->setTimezone(new DateTimeZone(CrewCalendar::ZONE))->format('w')];
    }

    private static function hm(?int $minute): string
    {
        return $minute === null ? '' : sprintf('%02d:%02d', intdiv($minute, 60), $minute % 60);
    }
}
