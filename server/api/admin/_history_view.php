<?php
declare(strict_types=1);

/*
 * نمایش سوابق تابلو و تیر در پنل: جدول خوانش‌ها (تازه‌ترین بالا، با درصد تغییر هر فاز
 * نسبت به بار قبل) و نمودار کوچک سه فاز. هم صفحه سوابق و هم صفحه هر مورد از این‌ها می‌سازند.
 */

const HISTORY_PHASE_COLORS = ['r' => '#dc2626', 's' => '#d97706', 't' => '#2563eb'];

/** فیلترهای GET سوابق: ناحیه، تابلو یا تیر، پلاک، نوع بازرسی، بازه تاریخ شمسی، فقط نامتعارف. */
function history_filters(array $get): array
{
    $date = static function (string $key, bool $end) use ($get): ?int {
        $parts = Jalali::parse(trim((string) ($get[$key] ?? '')));
        return $parts === null ? null : Jalali::startOfDay(...$parts) + ($end ? 86_399_999 : 0);
    };
    $asset = (string) ($get['asset'] ?? '');
    $kind = (int) ($get['kind'] ?? 0);
    return ['area' => Areas::code((string) ($get['area'] ?? '')), 'asset' => $asset === '' ? null : (int) $asset,
        'plate' => trim((string) ($get['plate'] ?? '')), 'kind' => in_array($kind, Field::INSPECTION_KINDS, true) ? $kind : null,
        'from' => $date('from', false), 'to' => $date('to', true), 'anomaly' => ($get['anomaly'] ?? '') === '1'];
}

function history_num(?float $v): string
{
    return $v === null ? '—' : fa_digits(FieldHistory::num($v));
}

/** درصد تغییر نسبت به خوانش قبلی همان فاز؛ افزایش بیش از حد مجاز سرخ. */
function history_change(?float $now, ?float $before, int $risePct): string
{
    if ($now === null || $before === null || $before <= 0) {
        return '';
    }
    $pct = ($now - $before) / $before * 100;
    $style = $pct > $risePct ? 'color:#b91c1c;font-weight:bold' : ($pct < 0 ? 'color:#15803d' : 'color:#64748b');
    return ' <small style="' . $style . '">(' . ($pct > 0 ? '+' : '') . fa_digits((string) round($pct)) . '٪)</small>';
}

/** جدول یک رشته، از همان بازدید به قبل؛ [$names] کد مورد و ثبت‌کننده را هم نشان می‌دهد. */
function history_table(array $rows, string $part, bool $names = true): string
{
    if ($rows === []) {
        return '<p class="muted">سابقه‌ای نیست.</p>';
    }
    $thermal = $part === FieldReadings::PART_THERMAL;
    $rise = (int) FieldSettings::all()['history_rise_pct'];
    $html = '<div style="max-height:420px;overflow:auto"><table><tr><th>تاریخ و ساعت</th>'
        . ($thermal ? '<th>بیشینه دما</th><th>بیشینه کادر</th><th>میانگین کادر</th><th>تصویر</th>'
            : '<th>فاز R</th><th>فاز S</th><th>فاز T</th><th>نول</th>')
        . ($names ? '<th>مورد</th><th>ثبت‌کننده</th>' : '') . '</tr>';
    foreach ($rows as $i => $r) {
        $prev = $rows[$i + 1] ?? null;
        $mark = $r['anomaly'] ? ' style="background:#fef2f2"' : '';
        $html .= '<tr' . $mark . '><td class="nw">' . e(Jalali::format($r['at'])) . ($r['anomaly'] ? ' <span class="tag tag-overdue">نامتعارف</span>' : '') . '</td>';
        if ($thermal) {
            $img = $r['file_id'] === null ? '—' : '<a href="file.php?id=' . e((string) $r['file_id']) . '" target="_blank">'
                . '<img src="file.php?id=' . e((string) $r['file_id']) . '" loading="lazy" alt="" style="max-width:110px;max-height:80px;border-radius:6px"></a>';
            $html .= '<td>' . history_num($r['temp_max']) . '</td><td>' . history_num($r['temp_box_max']) . '</td><td>'
                . history_num($r['temp_box_avg']) . '</td><td>' . $img . '</td>';
        } else {
            foreach (FieldHistory::PHASES as $ph) {
                $html .= '<td>' . history_num($r[$ph]) . history_change($r[$ph], $prev[$ph] ?? null, $rise) . '</td>';
            }
            $html .= '<td>' . history_num($r['n']) . '</td>';
        }
        if ($names) {
            $html .= '<td><a href="item.php?id=' . e($r['item_id']) . '" dir="ltr">' . e((string) ($r['tracking_code'] ?? '')) . '</a></td><td>'
                . e((string) ($r['who'] ?? '—')) . '</td>';
        }
        $html .= '</tr>';
    }
    return $html . '</table></div>';
}

/** نمودار کوچک SVG سه فاز؛ قدیمی‌ترین در راست، مثل جدول‌های راست‌به‌چپ. */
function history_chart(array $rows): string
{
    $points = array_reverse(array_values(array_filter($rows, static fn ($r) => $r['r'] !== null || $r['s'] !== null || $r['t'] !== null)));
    if (count($points) < 2) {
        return '';
    }
    $max = 1.0;
    foreach ($points as $p) {
        foreach (FieldHistory::PHASES as $ph) {
            $max = max($max, (float) ($p[$ph] ?? 0));
        }
    }
    $max *= 1.1;
    [$w, $h, $pad] = [640, 160, 14];
    $n = count($points);
    $svg = '<svg viewBox="0 0 ' . $w . ' ' . $h . '" style="width:100%;max-width:640px;height:auto;background:#f8fafc;border-radius:8px" role="img" aria-label="نمودار سه فاز">';
    foreach (HISTORY_PHASE_COLORS as $ph => $color) {
        $line = [];
        foreach ($points as $i => $p) {
            if ($p[$ph] !== null) {
                $line[] = round($w - $pad - ($w - 2 * $pad) * $i / ($n - 1), 1) . ',' . round($h - $pad - ($h - 2 * $pad) * $p[$ph] / $max, 1);
            }
        }
        if (count($line) > 1) {
            $svg .= '<polyline fill="none" stroke="' . $color . '" stroke-width="2" points="' . implode(' ', $line) . '"/>';
        }
    }
    return $svg . '</svg><p class="muted" style="margin-top:2px"><span style="color:#dc2626">■ R</span> <span style="color:#d97706">■ S</span> '
        . '<span style="color:#2563eb">■ T</span> — بیشینه ' . history_num($max / 1.1) . ' آمپر</p>';
}

/** فرم ارجاع از سوابق به کارشناس رمزارز ناحیه؛ فقط مدیر و مجری رمزارز. */
function history_refer_form(array $key, Scope $scope, ?string $error, ?string $doneId): string
{
    if (!$scope->isManager() && !$scope->isExecutor()) {
        return '';
    }
    $html = '<div class="card" id="refer"><h3>ارجاع برای بازرسی رمزارز</h3>'
        . '<p class="muted">پرونده تازه «گزارش همکار» با نشانی آخرین بازرسی همین پلاک ساخته می‌شود و همان لحظه به کارشناس رمزارز ناحیه می‌رسد.</p>';
    if ($doneId !== null) {
        $html .= '<p class="notice">ارجاع شد. <a href="case.php?id=' . e($doneId) . '">صفحه پرونده</a></p>';
    }
    if ($error !== null) {
        $html .= '<p class="error">' . e($error) . '</p>';
    }
    $experts = $scope->experts($key['area']);
    if ($experts === []) {
        return $html . '<p class="muted">کارشناس رمزارز فعالی در این ناحیه نیست.</p></div>';
    }
    $html .= '<form method="post">' . csrf_field() . '<input type="hidden" name="action" value="refer"><div class="grid">'
        . '<label>کارشناس<select name="expert_id" required><option value="">—</option>';
    foreach ($experts as $x) {
        $html .= '<option value="' . e((string) $x['id']) . '">' . e((string) $x['name']) . '</option>';
    }
    return $html . '</select></label><label>مهلت بازدید (روز کاری، خالی یعنی پیش‌فرض)<input name="due_days" inputmode="numeric"></label></div>'
        . '<label>توضیح برای کارشناس<textarea name="note" rows="2"></textarea></label>'
        . '<button type="submit" style="background:#0e7490">ارجاع</button></form></div>';
}
