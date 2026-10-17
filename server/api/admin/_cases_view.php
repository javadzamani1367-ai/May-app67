<?php
declare(strict_types=1);

/*
 * اجزای مشترک صفحه‌های آرشیو پرونده‌ها: شاخص‌ها، فرم فیلتر، برچسب‌ها،
 * صفحه‌بندی و نمودار میله‌ای. فقط از صفحه‌های case*.php خوانده می‌شود.
 */

/** رنگ ثابت هر نوع گزارش، همان رنگ کارت پرونده در اپ (Color.kt). */
const CASE_TYPE_COLORS = [1 => '#334155', 2 => '#1d4ed8', 3 => '#7c3aed', 4 => '#0e7490', 5 => '#c2410c', 6 => '#0f766e'];

function case_styles(): string
{
    return '<style>
      main { max-width: 1180px; }
      .kpis { display:grid; grid-template-columns:repeat(auto-fit, minmax(150px, 1fr)); gap:10px; margin:12px 0; }
      .kpi { border:1px solid var(--line); border-radius:12px; padding:12px 14px; background:#f8fafc; }
      .kpi b { display:block; font-size:1.6em; color:var(--navy); line-height:1.3; }
      .kpi span { color:var(--muted); font-size:.88em; }
      .kpi.warn b { color:#b45309; } .kpi.danger b { color:#b91c1c; } .kpi.ok b { color:#15803d; }
      .ctag { display:inline-flex; align-items:center; gap:6px; padding:1px 10px; border-radius:999px;
              font-size:.85em; font-weight:bold; white-space:nowrap; }
      .ctag i { width:8px; height:8px; border-radius:50%; display:inline-block; }
      .bars td { border:0; padding:5px 6px; } .bars tr:hover td { background:none; }
      .bars .bl { width:36%; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:150px; }
      .bars .bn { width:1%; white-space:nowrap; text-align:left; } .bars .bn small { display:block; color:var(--muted); font-size:.78em; line-height:1.2; }
      td.nw { white-space:nowrap; } .bar { height:12px; border-radius:6px; background:var(--teal-2); min-width:2px; }
      .bar-track { background:#e2e8f0; border-radius:6px; width:100%; }
      .section-grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(330px, 1fr)); gap:14px; }
      .gallery { display:grid; grid-template-columns:repeat(auto-fill, minmax(170px, 1fr)); gap:10px; }
      .gallery figure { margin:0; border:1px solid var(--line); border-radius:10px; overflow:hidden; background:#f8fafc; }
      .gallery img { width:100%; height:130px; object-fit:cover; display:block; }
      .gallery figcaption { font-size:.78em; color:var(--muted); padding:6px 8px; line-height:1.5; }
      .actions-bar { display:flex; gap:8px; flex-wrap:wrap; } .actions-bar button { margin-top:0; }
    </style>';
}

/** شاخص‌های بالای آرشیو: شمار، توان، دستگاه و یافته‌های فنی همان پرونده‌های فیلترشده. */
function case_kpis(array $s): string
{
    $tile = static fn (string $value, string $label, string $tone = '') =>
        '<div class="kpi ' . $tone . '"><b>' . e($value) . '</b><span>' . e($label) . '</span></div>';
    $byStatus = array_column($s['status'], 'n', 'k');
    return case_styles() . '<div class="kpis">'
        . $tile(fa_digits((string) $s['cases']), 'پرونده')
        . $tile(fa_digits((string) ($byStatus[0] ?? 0)), 'در دست اقدام', 'warn')
        . $tile(fa_digits((string) ($byStatus[1] ?? 0)), 'بازدید شده', 'ok')
        . $tile(fa_digits((string) $s['devices']), 'دستگاه ثبت‌شده')
        . $tile((string) CaseLabels::number($s['watt'] / 1000), 'کیلووات توان کشف‌شده')
        . $tile(fa_digits((string) $s['tampered']), 'دستکاری کنتور', 'danger')
        . '</div>';
}

function case_type_tag(int $type): string
{
    $color = CASE_TYPE_COLORS[$type] ?? '#64748b';
    return '<span class="ctag" style="background:' . $color . '1f;color:' . $color . '"><i style="background:' . $color
        . '"></i>' . e(CaseLabels::REPORT_TYPES[$type] ?? '—') . '</span>';
}

function case_status_tag(array $row): string
{
    $status = (int) $row['status'];
    $html = '<span class="tag ' . (CaseLabels::STATUS_TAGS[$status] ?? 'tag-sent') . '">'
        . e(CaseLabels::STATUSES[$status] ?? '—') . '</span>';
    $approval = (int) $row['approval_state'];
    if ($approval === 1 || $approval === 3) {
        $html .= ' <span class="tag ' . ($approval === 3 ? 'tag-overdue' : 'tag-seen') . '">'
            . e(CaseLabels::APPROVALS[$approval]) . '</span>';
    }
    return $html;
}

/** فرم فیلتر مشترک فهرست و آمار؛ شهرستان‌ها و کارشناس‌ها از خود پرونده‌ها. */
function case_filter_form(array $values, string $action): string
{
    $select = static function (string $name, string $label, array $options) use ($values): string {
        $html = '<label>' . e($label) . '<select name="' . $name . '"><option value="">همه</option>';
        foreach ($options as $value => $text) {
            $selected = (string) ($values[$name] ?? '') === (string) $value ? ' selected' : '';
            $html .= '<option value="' . e((string) $value) . '"' . $selected . '>' . e((string) $text) . '</option>';
        }
        return $html . '</select></label>';
    };
    $scope = admin_scope();
    [$own, $ownParams] = $scope->reportWhere('r');
    $counties = array_column(Db::all("SELECT DISTINCT r.county FROM reports r WHERE LENGTH(r.county) > 0 AND $own
        ORDER BY r.county", $ownParams), 'county', 'county');
    $areas = array_intersect_key(Areas::options(), array_flip($scope->isManager() ? array_keys(Areas::NAMES) : $scope->areas));
    $experts = [];
    foreach (Db::all("SELECT DISTINCT r.expert_code, u.full_name FROM reports r LEFT JOIN users u ON u.user_code = r.expert_code
        WHERE LENGTH(r.expert_code) > 0 AND $own ORDER BY u.full_name", $ownParams) as $x) {
        $experts[$x['expert_code']] = CaseReport::expert(trim((string) $x['full_name']), (string) $x['expert_code']);
    }
    $input = static fn (string $name, string $label, string $hint = '') => '<label>' . e($label) . '<input name="' . $name
        . '" placeholder="' . e($hint) . '" value="' . e($values[$name] ?? '') . '"></label>';
    return '<form method="get" action="' . e($action) . '"><div class="grid">'
        . $input('q', 'جستجو', 'کد، آدرس، اشتراک، مالک، پرونده، قبض')
        . $select('type', 'نوع گزارش', CaseLabels::REPORT_TYPES)
        . $select('status', 'وضعیت', CaseLabels::STATUSES)
        . $select('approval', 'تأیید مدیر', CaseLabels::APPROVALS)
        . $select('area', 'ناحیه', $areas)
        . $select('county', 'شهرستان', $counties)
        . $select('expert', 'کارشناس', $experts)
        . $input('from', 'از تاریخ گزارش', '۱۴۰۵/۰۱/۰۱')
        . $input('to', 'تا تاریخ گزارش', '۱۴۰۵/۱۲/۲۹')
        . '</div><label class="check"><input type="checkbox" name="tampered" value="1"'
        . (isset($values['tampered']) ? ' checked' : '') . '> فقط پرونده‌های دارای دستکاری کنتور</label>'
        . '<button type="submit">نمایش</button>'
        . ($values !== [] ? ' <a href="' . e($action) . '">پاک کردن فیلترها</a>' : '') . '</form>';
}

function case_pager(array $values, int $page, int $pages, int $total): string
{
    $html = '<p class="muted">' . fa_digits((string) $total) . ' پرونده';
    if ($pages > 1) {
        $html .= ' — صفحه ';
        foreach (array_unique([1, max(1, $page - 2), max(1, $page - 1), $page, min($pages, $page + 1),
            min($pages, $page + 2), $pages]) as $p) {
            $html .= $p === $page ? ' <strong>' . fa_digits((string) $p) . '</strong>'
                : ' <a href="' . e(query_with($values, ['page' => (string) $p])) . '">' . fa_digits((string) $p) . '</a>';
        }
    }
    return $html . '</p>';
}

/**
 * نمودار میله‌ای ساده: هر ردیف [عنوان، شمار، متن کنار]. بی هیچ فایل بیرونی.
 *
 * @param list<array{0:string, 1:int, 2:string}> $rows
 */
function case_bars(array $rows, ?string $color = null): string
{
    if ($rows === []) {
        return '<p class="muted">داده‌ای نیست.</p>';
    }
    $max = max(1, max(array_column($rows, 1)));
    $html = '<table class="bars">';
    foreach ($rows as [$label, $count, $side]) {
        $width = round(100 * $count / $max, 1);
        $html .= '<tr><td class="bl">' . $label . '</td><td><div class="bar-track"><div class="bar" style="width:'
            . $width . '%' . ($color !== null ? ';background:' . $color : '') . '"></div></div></td>'
            . '<td class="bn"><b>' . fa_digits((string) $count) . '</b>' . ($side === '' ? '' : '<small>' . e($side) . '</small>')
            . '</td></tr>';
    }
    return $html . '</table>';
}
