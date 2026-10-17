<?php
declare(strict_types=1);

// بخش‌های مشترک صفحه عملکرد اکیپ و خروجی‌هایش: بازه، گزارش، و نمایش.

/** بازه از ?from و ?to شمسی؛ پیش‌فرض از اول ماه جاری تا امروز. سقف یک سال. */
function crew_period(): array
{
    [$y, $m, $d] = array_map('intval', explode('-', CrewCalendar::day(Db::now())));
    $fromP = Jalali::parse((string) ($_GET['from'] ?? '')) ?? [$y, $m, 1];
    $toP = Jalali::parse((string) ($_GET['to'] ?? '')) ?? [$y, $m, $d];
    $from = Jalali::startOfDay(...$fromP);
    $to = Jalali::startOfDay(...$toP) + 86_400_000 - 1;
    if ($to < $from) {
        [$from, $to, $fromP, $toP] = [Jalali::startOfDay(...$toP), $from + 86_400_000 - 1, $toP, $fromP];
    }
    $from = max($from, $to - 366 * 86_400_000);
    $day = static fn (array $p): string => sprintf('%04d-%02d-%02d', ...$p);
    return ['from' => $from, 'to' => $to, 'from_day' => CrewCalendar::day($from), 'to_day' => $day($toP),
        'from_text' => Jalali::format($from, false), 'to_text' => Jalali::format($to, false)];
}

function crew_report(array $period, string $member): array
{
    $scope = admin_scope();
    $report = CrewReport::build($period['from'], $period['to'], $scope->isManager() ? null : $scope->areas);
    if ($member !== '') {
        $report['members'] = array_values(array_filter($report['members'], static fn (array $m): bool => $m['key'] === $member));
    }
    return $report;
}

function crew_query(array $period, string $member = ''): string
{
    return http_build_query(array_filter(['from' => Jalali::latin($period['from_text']), 'to' => Jalali::latin($period['to_text']),
        'member' => $member], static fn ($v) => $v !== ''));
}

/** میله‌های افقی: [عنوان => تعداد]. */
function crew_bars(array $values): string
{
    $max = max([1, ...array_values($values)]);
    $total = max(1, array_sum($values));
    $html = '<table class="bars">';
    foreach ($values as $label => $n) {
        $html .= '<tr><td class="bl">' . e((string) $label) . '</td><td><div class="bar-track"><div class="bar" style="width:'
            . round($n * 100 / $max) . '%"></div></div></td><td class="bn">' . fa_digits((string) $n)
            . '<small>' . fa_digits((string) round($n * 100 / $total)) . '٪</small></td></tr>';
    }
    return $html . '</table>';
}

/** روزهای یک نفر. */
function crew_days_table(array $member): string
{
    $html = '<table><tr><th>تاریخ</th><th>شروع</th><th>پایان</th><th>ساعت</th><th>بازدید</th><th>بازرسی</th>'
        . '<th>مشترک نامتعارف</th><th>اضافه‌کار</th><th>بیرون شهر</th><th>مسافت</th><th>آبادی‌ها</th></tr>';
    foreach ($member['days'] as $d) {
        $html .= '<tr><td class="nw">' . e(CrewSheets::weekday($d['first']) . ' ' . fa_digits(CrewSheets::date($d['first'])))
            . ($d['holiday'] !== null ? ' <span class="tag tag-overdue">' . e($d['holiday']) . '</span>' : '') . '</td>'
            . '<td>' . e(fa_digits(CrewSheets::time($d['first']))) . '</td><td>' . e(fa_digits(CrewSheets::time($d['last']))) . '</td>'
            . '<td>' . fa_digits((string) $d['hours']) . '</td><td>' . fa_digits((string) $d['visits']) . '</td>'
            . '<td>' . fa_digits((string) $d['inspections']) . '</td><td>' . fa_digits((string) $d['subscribers']) . '</td>'
            . '<td>' . fa_digits((string) $d['overtime']) . '</td><td>' . fa_digits((string) $d['outside']) . '</td>'
            . '<td>' . fa_digits((string) $d['km']) . ' km</td><td>' . e(implode('، ', $d['places'])) . '</td></tr>';
    }
    return $html . '</table>';
}

/** بازدیدهای شماره‌خورده، همان شماره‌های نقشه. */
function crew_visits_table(array $visits): string
{
    if ($visits === []) {
        return '<p class="muted">بازدیدی نیست.</p>';
    }
    $html = '<table><tr><th>#</th><th>زمان</th><th>تابلو یا مورد</th><th>آبادی</th><th>بازرسی</th><th>تفکیک</th></tr>';
    foreach ($visits as $v) {
        $types = implode('، ', array_map(static fn ($k, $n) => CrewWork::TYPE_NAMES[$k] . ' ' . fa_digits((string) $n),
            array_keys($v['types']), $v['types']));
        $html .= '<tr><td>' . fa_digits((string) $v['n']) . '</td><td class="nw">' . e(Jalali::format($v['first']))
            . ' تا ' . e(fa_digits(CrewSheets::time($v['last']))) . '</td><td>' . e(ltr($v['label'])) . '</td><td>'
            . e($v['place'] ?? '—') . '</td><td>' . fa_digits((string) $v['inspections']) . '</td><td>' . e($types) . '</td></tr>';
    }
    return $html . '</table>';
}

/** راهنمای رنگ نقطه‌های نقشه. */
function crew_legend(): string
{
    $html = '<p class="muted" style="display:flex;gap:14px;flex-wrap:wrap">';
    foreach (CrewWork::TYPE_NAMES as $k => $name) {
        $html .= '<span><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:'
            . CrewMap::COLORS[$k] . '"></span> ' . e($name) . '</span>';
    }
    return $html . '<span><span style="display:inline-block;width:14px;height:14px;border-radius:50%;background:#1E3A8A"></span>'
        . ' بازدید (شماره جدول)</span></p>';
}
