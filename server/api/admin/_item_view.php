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

const EVENT_NAMES = FieldManage::EVENT_NAMES;

/** یک ردیف «عنوان: مقدار»؛ مقدار خالی نوشته نمی‌شود. */
function detail_row(string $label, ?string $value): string
{
    if ($value === null || trim($value) === '') {
        return '';
    }
    return '<tr><th style="width:35%">' . e($label) . '</th><td>' . nl2br(e($value)) . '</td></tr>';
}

function number_text(mixed $value): ?string
{
    return FieldPresenter::numberText($value);
}

/** @return array<int, array{0:string, 1:string}> */
function payload_rows(int $kind, array $p): array
{
    return FieldPresenter::rows($kind, $p);
}

/** ردیف‌ها به جدول HTML. */
function rows_table(array $rows): string
{
    return $rows === [] ? '' : '<table>' . implode('', array_map(static fn ($r) => detail_row($r[0], $r[1]), $rows)) . '</table>';
}

function render_payload(int $kind, array $p): string
{
    return rows_table(payload_rows($kind, $p));
}

/** جدول تیرهای آمپرگیری تیربه‌تیر. */
function render_poles(array $p): string
{
    $rows = FieldLine::poleTable($p);
    if ($rows === []) {
        return '';
    }
    $html = '<table><tr><th>تیر</th><th>پلاک</th><th>آمپر</th><th>نسبت به قبلی</th><th>انشعاب</th><th>مختصات</th><th>ثبت موقعیت</th></tr>';
    foreach ($rows as $r) {
        $html .= '<tr><td>' . e($r[0]) . '</td><td>' . e(ltr($r[1])) . '</td><td>' . e($r[2]) . '</td><td>' . e($r[3])
            . '</td><td>' . e($r[4]) . '</td><td dir="ltr">' . e($r[5]) . '</td><td>' . e($r[6] ?: '—') . '</td></tr>';
    }
    return $html . '</table>';
}

/** مشترکان نامتعارف تیرها، با حد مجاز روز ارسال. */
function render_subscribers(array $p): string
{
    $rows = FieldSubscribers::table($p);
    if ($rows === []) {
        return '';
    }
    $limits = FieldSubscribers::limitsText($p);
    $html = ($limits === null ? '' : '<p class="muted">' . e($limits) . '</p>')
        . '<table><tr><th>تیر</th><th>مشترک</th><th>نام</th><th>آمپر</th><th>داوری</th><th>کد پستی</th>'
        . '<th>پرونده و اشتراک</th><th>مختصات منزل</th><th>ثبت موقعیت</th><th>عکس و فیلم</th></tr>';
    foreach ($rows as $r) {
        $tag = $r[4] === 'نامتعارف' ? '<span class="tag tag-overdue">' . e($r[4]) . '</span>' : e($r[4]);
        $html .= '<tr><td>' . e($r[0]) . '</td><td>' . e($r[1]) . '</td><td>' . e($r[2] ?: '—') . '</td><td>' . e($r[3])
            . '</td><td>' . $tag . '</td><td dir="ltr">' . e($r[5] ?: '—') . '</td><td>' . e($r[6] ?: '—')
            . '</td><td dir="ltr">' . e($r[7]) . '</td><td>' . e($r[8] ?: '—') . '</td><td>' . e($r[9] ?: '—') . '</td></tr>';
    }
    return $html . '</table>';
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
