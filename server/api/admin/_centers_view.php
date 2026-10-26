<?php
declare(strict_types=1);

// تکه‌های مشترک صفحه‌های آمار جامع در پنل مدیر و پرتال واحدها: بازه، فرم ویرایش سطر
// با انتخاب میان سامانه و فایل، و تاریخچه هر خانه.

function center_styles(): string
{
    return '<style>
      .ctable { font-size:.85em } .ctable td, .ctable th { padding:6px; white-space:nowrap }
      .cscroll { overflow-x:auto } .conflict { background:#fff7ed; border:1px solid #fdba74; border-radius:10px; padding:8px; margin-top:8px }
      .srcs { font-size:.8em; color:#475569 } .srcs b { color:#0f172a } .fields { display:grid; grid-template-columns:repeat(auto-fill,minmax(230px,1fr)); gap:4px 14px }
      .warn { color:#9a3412 } .badge { display:inline-block; padding:0 8px; border-radius:999px; font-size:.8em; background:#fee2e2; color:#7f1d1d }
      .badge.amber { background:#fef3c7; color:#78350f } .kpis { display:flex; flex-wrap:wrap; gap:10px }
      .kpi { flex:1 1 140px; border:1px solid #e2e8f0; border-radius:12px; padding:10px } .kpi b { display:block; font-size:1.3em; color:#0b1f3a }
    </style>';
}

function center_csrf_field(): string
{
    $_SESSION['center_csrf'] ??= bin2hex(random_bytes(16));
    return '<input type="hidden" name="_c" value="' . e($_SESSION['center_csrf']) . '">';
}

function center_csrf_check(): void
{
    $sent = (string) ($_POST['_c'] ?? '');
    if ($sent === '' || !hash_equals((string) ($_SESSION['center_csrf'] ?? ''), $sent)) {
        http_response_code(400);
        exit('درخواست نامعتبر است. صفحه را دوباره باز کنید.');
    }
}

/** بازه از سال شمسی یا «از» و «تا»؛ پیش‌فرض سال جاری. @return array{0:int, 1:int} */
function center_range(array $q): array
{
    $from = CenterValues::date((string) ($q['from'] ?? ''));
    $to = CenterValues::date((string) ($q['to'] ?? ''));
    if ($from !== null || $to !== null) {
        $start = $from === null ? 0 : RewardRules::dayStart($from);
        $end = $to === null ? Db::now() : RewardRules::dayStart($to) + 86_400_000 - 1;
        return [min($start, $end), max($start, $end)];
    }
    $year = (int) Jalali::latin((string) ($q['year'] ?? ''));
    return CenterCharts::year($year >= 1390 && $year <= 1500 ? $year : FieldCodes::jalaliYear(Db::now()));
}

function center_range_form(array $q, ?array $areas, string $extra = ''): string
{
    $now = FieldCodes::jalaliYear(Db::now());
    $year = (string) ($q['year'] ?? '');
    $html = '<form method="get"><div class="grid"><label>سال<select name="year"><option value="">—</option>';
    for ($y = $now; $y >= $now - 6; $y--) {
        $html .= '<option value="' . $y . '"' . ((string) $y === Jalali::latin($year) || ($year === '' && $y === $now && empty($q['from'])) ? ' selected' : '')
            . '>' . fa_digits((string) $y) . '</option>';
    }
    $html .= '</select></label><label>یا از تاریخ<input name="from" placeholder="۱۴۰۴/۰۱/۰۱" value="' . e((string) ($q['from'] ?? '')) . '"></label>'
        . '<label>تا تاریخ<input name="to" placeholder="۱۴۰۴/۱۲/۲۹" value="' . e((string) ($q['to'] ?? '')) . '"></label>';
    if ($areas === null) {
        $html .= '<label>ناحیه<select name="area"><option value="">همه</option>';
        foreach (array_keys(Areas::NAMES) as $code) {
            $html .= '<option value="' . e((string) $code) . '"' . (($q['area'] ?? '') === (string) $code ? ' selected' : '') . '>'
                . e(Areas::label((string) $code)) . '</option>';
        }
        $html .= '</select></label>';
    }
    return $html . '<label>جستجو<input name="q" value="' . e((string) ($q['q'] ?? '')) . '" placeholder="اشتراک، قبض، نام…"></label></div>'
        . '<button type="submit">نمایش</button> ' . $extra . '</form>';
}

function center_show(string $key, mixed $value): string
{
    $column = CenterColumns::get($key);
    return $column === null ? '' : CenterValues::display($column['type'], $value);
}

/** یک خانه فرم: مقدار فعلی، منبعش، و اگر ناسازگار است انتخاب میان سامانه، فایل و مقدار دیگر. */
function center_field(string $key, array $row, bool $editable): string
{
    $c = CenterColumns::get($key);
    $value = $row['values'][$key] ?? null;
    $from = ['system' => 'سامانه', 'file' => 'فایل', 'own' => 'نوشته کاربر', 'none' => ''][$row['from'][$key] ?? 'none'] ?? '';
    $label = '<label>' . e(CenterColumns::label($key)) . ($from !== '' ? ' <span class="srcs">(' . e($from) . ')</span>' : '');
    $input = center_input($key, $c, $value, $editable);
    $html = $label . $input . '</label>';
    if (in_array($key, $row['conflicts'], true) && $editable) {
        $html .= '<div class="conflict"><b>ناسازگار:</b> سامانه «' . e(center_show($key, $row['system'][$key] ?? null))
            . '» و فایل «' . e(center_show($key, $row['file'][$key] ?? null)) . '»'
            . '<label class="check"><input type="radio" name="c[' . e($key) . ']" value="system" style="width:auto"> مقدار سامانه</label>'
            . '<label class="check"><input type="radio" name="c[' . e($key) . ']" value="file" style="width:auto"> مقدار فایل</label>'
            . '<label class="check"><input type="radio" name="c[' . e($key) . ']" value="own" style="width:auto"> مقدار نوشته‌شده در خانه بالا</label></div>';
    } elseif (($row['system'][$key] ?? null) !== null && ($row['from'][$key] ?? '') !== 'system' && $editable) {
        $html .= '<div class="srcs">سامانه: <b>' . e(center_show($key, $row['system'][$key])) . '</b>'
            . ' <label class="check" style="display:inline-flex"><input type="checkbox" name="c[' . e($key) . ']" value="system" style="width:auto"> برگرد به مقدار سامانه</label></div>';
    }
    return '<div>' . $html . '</div>';
}

function center_input(string $key, array $c, mixed $value, bool $editable): string
{
    $name = 'v[' . e($key) . ']';
    $disabled = $editable ? '' : ' disabled';
    if ($c['type'] === 'tick') {
        return '<input type="hidden" name="' . $name . '" value="0"' . $disabled . '>'
            . '<input type="checkbox" name="' . $name . '" value="1" style="width:auto"' . ($value ? ' checked' : '') . $disabled . '>';
    }
    $text = match ($c['type']) {
        'date' => $value === null ? '' : Jalali::format((int) $value, false),
        'money', 'int', 'kw' => $value === null ? '' : (string) $value,
        default => (string) ($value ?? ''),
    };
    $list = CenterColumns::choices($key);
    $html = '<input name="' . $name . '" value="' . e($text) . '"' . $disabled
        . (in_array($c['type'], ['money', 'int', 'kw', 'sheba'], true) ? ' dir="ltr" inputmode="decimal"' : '')
        . ($list !== [] ? ' list="dl-' . e($key) . '"' : '') . '>';
    if ($list !== []) {
        $html .= '<datalist id="dl-' . e($key) . '">' . implode('', array_map(static fn ($o) => '<option value="' . e($o) . '">', $list)) . '</datalist>';
    }
    return $html;
}

/** فرم سطر، بخش به بخش؛ ستون‌هایی که کاربر نمی‌بیند نمی‌آیند. */
function center_form(array $row, array $access): string
{
    $writable = array_flip(CenterEdit::writable($access));
    $see = array_flip($access['see']);
    $html = '<form method="post">' . center_csrf_field() . '<input type="hidden" name="action" value="save">';
    foreach (CenterColumns::SECTIONS as $title => $keys) {
        $keys = array_values(array_filter($keys, static fn ($k) => isset($see[$k])));
        if ($keys === []) {
            continue;
        }
        $html .= '<h3 style="margin-top:16px">' . e($title) . '</h3><div class="fields">';
        foreach ($keys as $key) {
            $html .= center_field($key, $row, isset($writable[$key]) && !$row['locked']);
        }
        $html .= '</div>';
    }
    if (($row['hint']['text'] ?? '') !== '') {
        $html .= '<p class="srcs">آخرین مرحله واحدها: ' . e($row['hint']['text'])
            . (($row['hint']['suggest'] ?? null) ? ' — پیشنهاد وضعیت: <b>' . e($row['hint']['suggest']) . '</b>' : '') . '</p>';
    }
    return $html . ($row['locked'] ? '<p class="warn">سال این سطر قفل است.</p>' : '<button type="submit">ذخیره</button>') . '</form>';
}

/** خانه‌هایی که کاربر واقعاً عوض کرده، و انتخاب‌هایش، از فرم ارسال‌شده. @return array{0:array, 1:array} */
function center_posted(array $row): array
{
    $values = [];
    $choices = [];
    foreach ((array) ($_POST['v'] ?? []) as $key => $raw) {
        $c = CenterColumns::get((string) $key);
        if ($c === null || is_array($raw)) {
            continue;
        }
        $choice = $_POST['c'][$key] ?? null;
        $new = CenterValues::normalize($c['type'], trim((string) $raw));
        if ($choice === 'own' || ($choice === null && !CenterValues::same($c['type'], $row['values'][$key] ?? null, $new))) {
            $values[$key] = trim((string) $raw);
        }
    }
    foreach ((array) ($_POST['c'] ?? []) as $key => $choice) {
        if (in_array($choice, ['system', 'file'], true)) {
            $choices[(string) $key] = $choice;
            unset($values[$key]);
        }
    }
    return [$values, $choices];
}

function center_changes(array $changes): string
{
    if ($changes === []) {
        return '<p class="muted">تغییری ثبت نشده.</p>';
    }
    $how = ['edit' => 'ویرایش', 'choose:system' => 'انتخاب سامانه', 'choose:file' => 'انتخاب فایل', 'import' => 'ورود فایل',
        'add' => 'افزودن سطر', 'delete' => 'حذف'];
    $html = '<div class="cscroll"><table class="ctable"><tr><th>زمان</th><th>کاربر</th><th>ستون</th><th>پیش</th><th>پس</th><th>روش</th></tr>';
    foreach ($changes as $c) {
        $html .= '<tr><td>' . e(Jalali::format($c['at'])) . '</td><td>' . e($c['who'] ?? '—') . '</td><td>' . e($c['label'])
            . '</td><td>' . e(fa_digits((string) ($c['old'] ?? ''))) . '</td><td>' . e(fa_digits((string) ($c['new'] ?? '')))
            . '</td><td>' . e($how[$c['how']] ?? $c['how']) . '</td></tr>';
    }
    return $html . '</table></div>';
}
