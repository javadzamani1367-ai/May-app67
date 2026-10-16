<?php
declare(strict_types=1);

/**
 * عملکرد هر عضو اکیپ، روز به روز، از رویدادهای CrewWork: ساعت شروع و پایان،
 * بازدیدها و بازرسی‌ها، بازه‌های روز، اضافه‌کار، کار تعطیل، بیرون از شهر و
 * مسافت تقریبی. هر کس در اکیپ نام برده شده همه کار آن مورد را به نام خودش دارد.
 */
final class CrewDays
{
    /** ترموویژنی که تابلویش معلوم نیست، به نزدیک‌ترین بازدید همان روز تا این فاصله زمانی می‌پیوندد. */
    private const ATTACH_MILLIS = 3 * 3_600_000;

    /**
     * @param list<array> $items موردها با payload آرایه (و frames برای ترموویژن)
     * @return list<array> اعضا، پرکارترین اول
     */
    public static function build(array $items, CrewCalendar $cal, ?GeoPlaces $geo, int $from, int $to): array
    {
        $members = [];
        foreach ($items as $item) {
            $events = array_values(array_filter(CrewWork::events($item),
                static fn (array $e): bool => $e['at'] >= $from && $e['at'] <= $to));
            if ($events === []) {
                continue;
            }
            foreach ($events as &$e) {
                $where = $geo !== null && $e['lat'] !== null ? $geo->locate($e['lat'], $e['lon']) : null;
                $e['outside'] = $where['outside'] ?? null;
                $e['place'] = $where['place'] ?? null;
            }
            unset($e);
            foreach (CrewWork::crew((array) $item['payload']) as $person) {
                $members[$person['key']] ??= ['key' => $person['key'], 'name' => $person['name'], 'roles' => [], 'events' => []];
                $members[$person['key']]['roles'][$person['role']] = FieldRoster::ROLE_NAMES[$person['role']];
                array_push($members[$person['key']]['events'], ...$events);
            }
        }
        $out = [];
        foreach ($members as $m) {
            $byDay = [];
            foreach ($m['events'] as $e) {
                $byDay[CrewCalendar::day($e['at'])][] = $e;
            }
            ksort($byDay);
            $days = [];
            foreach ($byDay as $day => $events) {
                $days[] = self::day((string) $day, $events, $cal);
            }
            ksort($m['roles']);
            $out[] = ['key' => $m['key'], 'name' => $m['name'], 'roles' => array_values($m['roles']),
                'days' => $days, 'total' => self::total($days)];
        }
        usort($out, static fn (array $a, array $b): int => [$b['total']['inspections'], $a['name']] <=> [$a['total']['inspections'], $b['name']]);
        return $out;
    }

    /** یک روز کار یک نفر. */
    public static function day(string $day, array $events, CrewCalendar $cal): array
    {
        usort($events, static fn (array $a, array $b): int => $a['at'] <=> $b['at']);
        $keys = [];
        foreach ($events as $i => $e) {
            $keys[$i] = $e['panel'] !== null ? 'p:' . $e['panel'] : null;
        }
        foreach ($events as $i => $e) {
            if ($keys[$i] !== null) {
                continue;
            }
            if ($e['type'] === 'thermal') {
                $best = null;
                foreach ($events as $j => $o) {
                    $gap = abs($o['at'] - $e['at']);
                    if ($keys[$j] !== null && str_starts_with($keys[$j], 'p:') && $gap <= self::ATTACH_MILLIS && ($best === null || $gap < $best[1])) {
                        $best = [$keys[$j], $gap];
                    }
                }
                $keys[$i] = $best[0] ?? null;
            }
            $keys[$i] ??= 'i:' . $e['item'];
        }
        $visits = [];
        foreach ($events as $i => $e) {
            $v = &$visits[$keys[$i]];
            $v ??= ['label' => $e['plate'] !== '' ? $e['plate'] : $e['code'], 'named' => false,
                'first' => $e['at'], 'last' => $e['at'], 'inspections' => 0, 'types' => [], 'place' => $e['place']];
            // A visit is named by its panel's plate, as first written that day.
            if ($e['type'] === 'panel' && $e['plate'] !== '' && !$v['named']) {
                $v['label'] = $e['plate'];
                $v['named'] = true;
            }
            $v['last'] = max($v['last'], $e['at']);
            if ($e['inspection']) {
                $v['inspections']++;
                $v['types'][$e['type']] = ($v['types'][$e['type']] ?? 0) + 1;
            }
            $v['place'] ??= $e['place'];
            unset($v);
        }
        $inspections = array_values(array_filter($events, static fn (array $e): bool => $e['inspection']));
        $buckets = array_fill_keys(array_keys(CrewCalendar::BUCKETS), 0);
        $types = array_fill_keys(array_keys(CrewWork::TYPE_NAMES), 0);
        $outside = 0;
        $overtimeInspections = 0;
        foreach ($inspections as $e) {
            $buckets[CrewCalendar::bucket($e['at'])]++;
            $types[$e['type']]++;
            $outside += $e['outside'] === true ? 1 : 0;
            $overtimeInspections += $cal->overtime($e['at']) ? 1 : 0;
        }
        $metres = 0.0;
        $previous = null;
        foreach ($events as $e) {
            if ($e['lat'] === null) {
                continue;
            }
            if ($previous !== null) {
                $metres += PositionAudit::distance($previous[0], $previous[1], $e['lat'], $e['lon']);
            }
            $previous = [$e['lat'], $e['lon']];
        }
        $first = $events[0]['at'];
        $last = $events[count($events) - 1]['at'];
        $places = array_count_values(array_filter(array_column($events, 'place'), 'is_string'));
        arsort($places);
        return [
            'day' => $day, 'first' => $first, 'last' => $last, 'hours' => round(($last - $first) / 3_600_000, 1),
            'visits' => count($visits), 'visit_list' => array_values($visits), 'inspections' => count($inspections),
            'types' => $types, 'subscribers' => array_sum(array_column($events, 'subs')), 'buckets' => $buckets,
            'overtime' => $cal->overtimeHours($first, $last), 'overtime_inspections' => $overtimeInspections,
            'holiday' => $cal->holiday($first), 'outside' => $outside, 'km' => round($metres / 1000, 1),
            'places' => array_slice(array_keys($places), 0, 3),
            'points' => array_map(static fn (array $e, int $i): array => ['at' => $e['at'], 'type' => $e['type'],
                'lat' => $e['lat'], 'lon' => $e['lon'], 'plate' => $e['plate'], 'code' => $e['code'],
                'inspection' => $e['inspection'], 'visit' => $keys[$i]], $events, array_keys($events)),
        ];
    }

    /** جمع روزهای یک نفر. */
    public static function total(array $days): array
    {
        $sum = static fn (string $k): float|int => array_sum(array_column($days, $k));
        $types = array_fill_keys(array_keys(CrewWork::TYPE_NAMES), 0);
        $buckets = array_fill_keys(array_keys(CrewCalendar::BUCKETS), 0);
        foreach ($days as $d) {
            foreach ($d['types'] as $k => $n) {
                $types[$k] += $n;
            }
            foreach ($d['buckets'] as $k => $n) {
                $buckets[$k] += $n;
            }
        }
        $starts = array_map(static fn (array $d): int => CrewCalendar::minuteOfDay($d['first']), $days);
        $ends = array_map(static fn (array $d): int => CrewCalendar::minuteOfDay($d['last']), $days);
        return [
            'days' => count($days),
            'holidays' => count(array_filter($days, static fn (array $d): bool => $d['holiday'] !== null)),
            'visits' => $sum('visits'), 'inspections' => $sum('inspections'), 'types' => $types,
            'subscribers' => $sum('subscribers'), 'buckets' => $buckets,
            'hours' => round($sum('hours'), 1), 'overtime' => round($sum('overtime'), 1),
            'overtime_inspections' => $sum('overtime_inspections'), 'outside' => $sum('outside'), 'km' => round($sum('km'), 1),
            'avg_start' => $starts === [] ? null : (int) round(array_sum($starts) / count($starts)),
            'avg_end' => $ends === [] ? null : (int) round(array_sum($ends) / count($ends)),
            'earliest' => $starts === [] ? null : min($starts), 'latest' => $ends === [] ? null : max($ends),
        ];
    }

    /** «۰۷:۴۵» از دقیقه روز. */
    public static function clock(?int $minute): string
    {
        return $minute === null ? '—' : Jalali::digits(sprintf('%02d:%02d', intdiv($minute, 60), $minute % 60));
    }
}
