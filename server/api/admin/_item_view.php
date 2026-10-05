<?php
declare(strict_types=1);

/*
 * نمایش جزئیات یک مورد میدانی: چک‌لیست هر نوع، فایل‌ها و تاریخچه.
 * فقط از item.php خوانده می‌شود؛ خودش ورودی نمی‌گیرد.
 */

const FILE_ROLE_NAMES = [
    Field::ROLE_PHOTO => 'عکس',
    Field::ROLE_PHOTO_STAMPED => 'عکس با مهر',
    Field::ROLE_VIDEO => 'فیلم',
    Field::ROLE_AUDIO => 'صدا',
    Field::ROLE_THERMAL => 'ترموویژن (اصل)',
    Field::ROLE_THERMAL_EXIF => 'ترموویژن با مختصات',
    Field::ROLE_SIDECAR => 'شناسنامه فایل (JSON)',
    Field::ROLE_TRACK => 'مسیر (gzip)',
];

const EVENT_NAMES = ['registered' => 'ثبت از گوشی', 'status' => 'تغییر وضعیت', 'merged' => 'ادغام'];

/** یک ردیف «عنوان: مقدار»؛ مقدار خالی نوشته نمی‌شود. */
function detail_row(string $label, ?string $value): string
{
    if ($value === null || trim($value) === '') {
        return '';
    }
    return '<tr><th style="width:35%">' . e($label) . '</th><td>' . nl2br(e($value)) . '</td></tr>';
}

/** کلیدهای چک‌لیست به برچسب فارسی؛ کلید ناشناخته همان‌طور نوشته می‌شود تا گم نشود. */
function labels(array $keys, array $names): string
{
    return implode('، ', array_map(static fn ($k) => $names[$k] ?? (string) $k, $keys));
}

function number_text(mixed $value): ?string
{
    return is_numeric($value) ? fa_digits((string) $value) : null;
}

/** چک‌لیست هر نوع گزارش، از payload. */
function render_payload(int $kind, array $p): string
{
    $html = '';
    if ($kind === Field::KIND_CRYPTO) {
        $html .= detail_row('آمپر اندازه‌گیری‌شده', number_text($p['amperage'] ?? null));
        $html .= detail_row('تعداد تقریبی دستگاه', number_text($p['miner_count'] ?? null));
        $html .= detail_row('نوع دستگاه', $p['miner_type'] ?? null);
        $html .= detail_row('نشانه‌ها', labels((array) ($p['signs'] ?? []), FieldLabels::SIGNS));
    }
    if ($kind === Field::KIND_ILLEGAL) {
        $consumers = [];
        foreach ((array) ($p['consumers'] ?? []) as $key => $count) {
            $consumers[] = (FieldLabels::CONSUMERS[$key] ?? $key) . ' × ' . fa_digits((string) $count);
        }
        $html .= detail_row('مصرف‌کننده‌ها', implode('، ', $consumers));
        $html .= detail_row('مصرف‌کننده دیگر', $p['other_consumer'] ?? null);
        $html .= detail_row('کاربری', isset($p['usage']) ? (FieldLabels::USAGES[$p['usage']] ?? (string) $p['usage']) : null);
        $html .= detail_row('تخلف', labels((array) ($p['violations'] ?? []), FieldLabels::VIOLATIONS));
        $html .= detail_row('شماره کنتور یا قبض', $p['meter_or_bill'] ?? null);
    }
    if ($kind === Field::KIND_CRYPTO || $kind === Field::KIND_ILLEGAL) {
        $team = (array) ($p['team'] ?? []);
        $yes = array_keys(array_filter($team, static fn ($v) => $v === true));
        $no = array_keys(array_filter($team, static fn ($v) => $v === false));
        $html .= detail_row('برای اکیپ: بله', labels($yes, FieldLabels::TEAM_QUESTIONS));
        $html .= detail_row('برای اکیپ: خیر', labels($no, FieldLabels::TEAM_QUESTIONS));
        $html .= detail_row('بهترین زمان', isset($team['best_time']) ? (FieldLabels::BEST_TIMES[$team['best_time']] ?? null) : null);
        $html .= detail_row('تعداد ورودی', number_text($team['entrances'] ?? null));
        $html .= detail_row('توضیح ورودی', $team['entrance_note'] ?? null);
    }
    if ($kind === Field::KIND_FEEDER) {
        $html .= render_feeders($p);
    }
    if ($kind === Field::KIND_THERMAL) {
        $html .= detail_row('شروع مسیر', isset($p['started_at']) ? Jalali::format((int) $p['started_at']) : null);
        $html .= detail_row('پایان مسیر', isset($p['ended_at']) ? Jalali::format((int) $p['ended_at']) : null);
        $html .= detail_row('نقطه‌های مسیر', number_text($p['points'] ?? null));
        $skew = $p['clock_skew_s'] ?? null;
        $html .= detail_row('اختلاف ساعت گوشی با GPS (ثانیه)', number_text($skew));
    }
    return $html === '' ? '' : '<table>' . $html . '</table>';
}

function phases_text(mixed $phases): string
{
    $p = (array) $phases;
    return 'R ' . (number_text($p['r'] ?? null) ?? '—') . ' / S ' . (number_text($p['s'] ?? null) ?? '—')
        . ' / T ' . (number_text($p['t'] ?? null) ?? '—');
}

function render_feeders(array $p): string
{
    $html = detail_row('کلید کل (آمپر)', phases_text($p['main'] ?? []));
    $states = ['measured' => 'اندازه‌گیری شد', 'absent' => 'وجود ندارد یا قطع است', 'not_measured' => 'اندازه‌گیری نشد'];
    foreach ((array) ($p['feeders'] ?? []) as $name => $feeder) {
        $feeder = (array) $feeder;
        $state = (string) ($feeder['state'] ?? '');
        $text = $states[$state] ?? '—';
        if ($state === 'measured') {
            $text .= ': ' . phases_text($feeder['phases'] ?? []);
        } elseif ($state === 'not_measured') {
            $text .= ' — ' . (string) ($feeder['reason'] ?? '');
        }
        $html .= detail_row('فیدر ' . $name, $text);
    }
    $check = (array) ($p['check'] ?? []);
    if ($check !== []) {
        $verdict = ($check['possible'] ?? false) !== true ? 'کنترل مجموع ممکن نبود'
            : (($check['ok'] ?? false) === true ? 'مجموع فیدرها با کلید کل می‌خواند' : 'اختلاف بیش از حد مجاز');
        $verdict .= ' (حد مجاز ' . fa_digits((string) ($check['tolerance_pct'] ?? '—')) . '٪، کف '
            . fa_digits((string) ($check['tolerance_min_a'] ?? '—')) . ' آمپر)';
        $html .= detail_row('کنترل مجموع', $verdict);
        $html .= detail_row('عدم تعادل فازها (٪)', number_text($check['imbalance_pct'] ?? null));
    }
    return $html . detail_row('توضیح', $p['note'] ?? null);
}

/** فایل‌ها با پیش‌نمایش عکس؛ دماهای ترموویژن از payload. */
function render_files(array $files, array $payload): string
{
    if ($files === []) {
        return '<p class="muted">فایلی ندارد.</p>';
    }
    $thermal = (array) ($payload['files'] ?? []);
    $html = '<table><tr><th>فایل</th><th>نوع</th><th>زمان</th><th>تخصیص و دما</th></tr>';
    foreach ($files as $f) {
        $role = (int) $f['role'];
        $link = 'file.php?id=' . urlencode((string) $f['id']);
        $image = str_starts_with((string) $f['mime'], 'image/') && (int) $f['complete'] === 1;
        $cell = (int) $f['complete'] !== 1 ? '<span class="tag tag-seen">هنوز نرسیده</span>'
            : ($image ? '<a href="' . e($link) . '" target="_blank"><img src="' . e($link)
                . '" loading="lazy" alt="" style="max-width:140px;max-height:110px;border-radius:8px"></a>'
                : '<a href="' . e($link) . '">دریافت</a>');
        $notes = [];
        if ($f['asset_type'] !== null) {
            $notes[] = ((int) $f['asset_type'] === Field::ASSET_PANEL ? 'تابلو' : 'تیر') . ' ' . (string) $f['plate'];
        }
        if ((int) $f['location_uncertain'] === 1) {
            $notes[] = 'موقعیت نامطمئن';
        }
        $temps = (array) (((array) ($thermal[$f['id']] ?? []))['temperatures'] ?? []);
        if ($temps !== []) {
            $notes[] = 'بیشینه ' . (number_text($temps['max'] ?? null) ?? '—') . '° — R1: '
                . (number_text($temps['r1_max'] ?? null) ?? '—') . '/' . (number_text($temps['r1_min'] ?? null) ?? '—')
                . '/' . (number_text($temps['r1_avg'] ?? null) ?? '—') . '°';
        }
        if ((string) $f['note'] !== '') {
            $notes[] = (string) $f['note'];
        }
        $html .= '<tr><td>' . $cell . '</td><td>' . e(FILE_ROLE_NAMES[$role] ?? '—') . '</td><td>'
            . e(Jalali::format($f['captured_at'] === null ? null : (int) $f['captured_at'])) . '</td><td>'
            . nl2br(e(implode("\n", $notes))) . '</td></tr>';
    }
    return $html . '</table>';
}

/** زنجیره کامل یک مورد، از ثبت تا امروز. */
function render_events(array $events): string
{
    $html = '<table><tr><th>زمان</th><th>کاربر</th><th>رویداد</th><th>توضیح</th></tr>';
    foreach ($events as $ev) {
        $what = EVENT_NAMES[$ev['action']] ?? (string) $ev['action'];
        if ($ev['to_status'] !== null) {
            $what .= ': ' . ($ev['from_status'] === null ? '' : (FieldAdmin::STATUS_NAMES[(int) $ev['from_status']] ?? '') . ' ← ')
                . (FieldAdmin::STATUS_NAMES[(int) $ev['to_status']] ?? '');
        }
        $html .= '<tr><td>' . e(Jalali::format((int) $ev['at'])) . '</td><td>' . e($ev['who'] ?? '—') . '</td><td>'
            . e($what) . '</td><td>' . nl2br(e((string) $ev['note'])) . '</td></tr>';
    }
    return $html . '</table>';
}
