<?php
declare(strict_types=1);

/*
 * بخش‌های صفحه شخص: جدول ناحیه × نقش، کد نصب هر اپ، و کار شخص در هر ناحیه. فقط
 * از person.php خوانده می‌شود.
 */

/**
 * جدول تیک‌ها: هر ناحیه یک بخش بازشونده (ناحیه‌هایی که شخص در آن کاربری دارد باز)،
 * در هر بخش دسته‌های نقش، و برای نقش‌های اپ میدانی تیک مجوز بازرسی و گزارش.
 *
 * @param list<array<string, mixed>> $accounts
 */
function person_matrix(array $accounts): string
{
    $have = [];
    foreach ($accounts as $a) {
        if ((int) $a['active'] === 1) {
            $have[($a['area'] ?? '-') . ':' . $a['kind']] = (int) $a['permissions'];
        }
    }
    $html = '<form method="post">' . csrf_field() . '<input type="hidden" name="action" value="accounts">';
    $hq = isset($have['-:illegal_hq']);
    $html .= '<p><label class="check"><input type="checkbox" name="tick[]" value="-:illegal_hq"' . ($hq ? ' checked' : '') . '> '
        . e(Persons::PROVINCE_KINDS['illegal_hq']['name']) . ' (استانی)</label></p>';
    foreach (Areas::options() as $code => $label) {
        $code = (string) $code;
        $ticked = [];
        foreach (array_keys(Persons::KINDS) as $kind) {
            if (isset($have["$code:$kind"])) {
                $ticked[] = Persons::kindName($kind);
            }
        }
        $html .= '<details' . ($ticked !== [] ? ' open' : '') . ' style="border:1px solid #e2e8f0;border-radius:8px;padding:6px 10px;margin:6px 0">'
            . '<summary><strong>' . e($label) . '</strong>' . ($ticked === [] ? '' : ' <span class="muted">— ' . e(implode('، ', $ticked)) . '</span>')
            . '</summary>';
        foreach (Persons::GROUPS as $title => $kinds) {
            $html .= '<div style="margin:6px 0"><span class="muted">' . e($title) . ':</span> ';
            foreach ($kinds as $kind) {
                $key = "$code:$kind";
                $on = isset($have[$key]);
                $html .= '<label class="check" style="display:inline-block;margin-left:14px;font-weight:normal">'
                    . '<input type="checkbox" name="tick[]" value="' . e($key) . '"' . ($on ? ' checked' : '') . '> ' . e(Persons::kindName($kind));
                if (Persons::KINDS[$kind]['role'] === Auth::ROLE_FIELD) {
                    $bits = $have[$key] ?? 3;
                    foreach ([Auth::PERM_INSPECT => 'بازرسی', Auth::PERM_REPORT => 'گزارش'] as $bit => $name) {
                        $html .= ' <input type="checkbox" name="perm[' . e($key) . '][]" value="' . $bit . '"' . (($bits & $bit) !== 0 ? ' checked' : '')
                            . ' title="' . $name . '"><span class="muted">' . $name . '</span>';
                    }
                }
                $html .= '</label>';
            }
            $html .= '</div>';
        }
        $html .= '</details>';
    }
    return $html . '<p class="muted">هر تیک تازه یک کاربری با کد پیشنهادی و رمز یک‌بارمصرف می‌سازد؛ تیک برداشته، کاربری را
        غیرفعال می‌کند (پرونده‌ها و سابقه‌اش می‌ماند).</p><button type="submit">ذخیره نقش‌ها</button></form>';
}

/** کد نصب هر اپ؛ کد تازه جای قبلی را می‌گیرد و نشست نصب قبلی بسته می‌شود. */
function person_devices(string $personId, array $devices): string
{
    $html = '<table><tr><th>اپ</th><th>کد نصب</th><th></th></tr>';
    foreach (PersonDevices::APPS as $app => $name) {
        $code = $devices[$app] ?? '';
        $html .= '<tr><td>' . e($name) . '</td><td><form method="post" class="row" style="justify-content:flex-start;gap:8px;margin:0">'
            . csrf_field() . '<input type="hidden" name="action" value="device"><input type="hidden" name="app" value="' . e($app) . '">'
            . '<input name="device_code" value="' . e(implode('-', str_split($code, 4))) . '" dir="ltr" style="max-width:260px" placeholder="—">'
            . '<button type="submit">ثبت</button></form></td><td class="muted">' . ($code === '' ? 'ثبت نشده' : '') . '</td></tr>';
    }
    return $html . '</table><p class="muted">کد هر نصب پایین صفحه ورود همان اپ است. با نصب دوباره اپ، کد تازه را اینجا بنویسید؛
        کاربری‌ها و رمزها همان می‌مانند.</p>';
}

/** کار شخص: محل خدمت اصلی اول، بعد هر شهرستان دیگر؛ در هر شهرستان هر نقش جدا. */
function person_work(array $work): string
{
    if ($work === []) {
        return '<p class="muted">این شخص هنوز کاربری‌ای ندارد.</p>';
    }
    $html = '';
    foreach ($work as $block) {
        $html .= '<h4 style="margin:14px 0 4px">' . e($block['name']) . ($block['home'] ? ' <span class="tag tag-seen">محل خدمت اصلی</span>' : '') . '</h4>'
            . '<table><tr><th>نقش</th><th>کد کاربری</th><th>کار در این بازه</th><th>امتیاز</th></tr>';
        foreach ($block['roles'] as $role) {
            $figures = implode('، ', array_map(static fn ($f) => e($f['label']) . ': ' . fa_digits((string) $f['value']), $role['figures']));
            $scores = implode('، ', array_map(static fn ($s) => e($s['kind']) . ': ' . ($s['score'] === null ? '—' : fa_digits((string) $s['score'])),
                $role['scores']));
            $html .= '<tr><td>' . e($role['kind_name']) . ($role['active'] ? '' : ' <span class="tag tag-overdue">غیرفعال</span>') . '</td>'
                . '<td dir="ltr" style="text-align:right"><code>' . e($role['user_code']) . '</code></td><td>' . ($figures ?: '—')
                . '</td><td>' . ($scores ?: '—') . '</td></tr>';
        }
        $html .= '</table>';
    }
    return $html;
}
