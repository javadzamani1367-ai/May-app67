<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_item_view.php';

admin_require(ADMIN_ROLES);

// گزارش یک مورد به Word: مشخصات، چک‌لیست، فهرست فایل‌ها و تاریخچه.
$id = (string) ($_GET['id'] ?? '');
$item = Field::isUuid($id) ? Db::one(
    'SELECT i.*, u.full_name AS reporter, u.user_code AS reporter_code, a.full_name AS expert
     FROM field_items i JOIN users u ON u.id = i.user_id LEFT JOIN users a ON a.id = i.assigned_to WHERE i.id = ?',
    [$id]
) : null;
if ($item === null || !admin_scope()->canSeeField($item)) {
    http_response_code(404);
    exit('مورد پیدا نشد.');
}
$payload = json_decode((string) $item['payload'], true);
$payload = is_array($payload) ? $payload : [];
$lat = $item['latitude'];
$info = array_values(array_filter([
    ['کد رهگیری', (string) $item['tracking_code']],
    ['نوع', FieldAdmin::KIND_NAMES[(int) $item['kind']] ?? ''],
    ['وضعیت', FieldAdmin::STATUS_NAMES[(int) $item['status']] ?? ''],
    ['اولویت', $item['priority'] === null ? '' : (FieldAdmin::PRIORITY_NAMES[(int) $item['priority']] ?? '')],
    ['ثبت‌کننده', $item['reporter'] . ' (' . $item['reporter_code'] . ')'],
    ['تاریخ ثبت', Jalali::format((int) $item['created_at'])],
    ['ارجاع به', (string) $item['expert']],
    ['مهلت', $item['due_at'] === null ? '' : Jalali::format((int) $item['due_at'], false)],
    ['آدرس', (string) $item['address']],
    ['پلاک', (string) $item['plate']],
    ['مختصات', $lat === null ? '' : sprintf('%.6f, %.6f', (float) $lat, (float) $item['longitude'])
        . ($item['accuracy'] === null ? '' : ' (± ' . (int) round((float) $item['accuracy']) . ' m)')],
    ['شرح', (string) $item['description']],
], static fn ($r) => trim($r[1]) !== ''));

$files = [];
foreach (Db::all('SELECT * FROM field_files WHERE item_id = ? AND complete = 1 ORDER BY role, captured_at', [$id]) as $f) {
    $label = FILE_ROLE_NAMES[(int) $f['role']] ?? '';
    $detail = [Jalali::format($f['captured_at'] === null ? null : (int) $f['captured_at'])];
    if ($f['asset_type'] !== null) {
        $detail[] = ((int) $f['asset_type'] === Field::ASSET_PANEL ? 'تابلو' : 'تیر') . ' ' . $f['plate'];
    }
    $detail[] = 'SHA-256: ' . $f['sha256'];
    $files[] = [$label, implode("\n", $detail)];
}
$events = [];
foreach (Db::all('SELECT e.*, u.full_name AS who FROM field_events e LEFT JOIN users u ON u.id = e.user_id
                  WHERE e.item_id = ? ORDER BY e.at, e.id', [$id]) as $ev) {
    $what = (EVENT_NAMES[$ev['action']] ?? $ev['action'])
        . ($ev['to_status'] === null ? '' : ': ' . (FieldAdmin::STATUS_NAMES[(int) $ev['to_status']] ?? ''));
    $events[] = [Jalali::format((int) $ev['at']), trim($what . ' — ' . ($ev['who'] ?? '') . "\n" . (string) $ev['note'])];
}

$blocks = [['h', 'گزارش مورد ' . $item['tracking_code']], ['table', $info]];
$details = payload_rows((int) $item['kind'], $payload);
if ($details !== []) {
    array_push($blocks, ['h', 'جزئیات'], ['table', $details]);
}
if ((int) $item['kind'] === Field::KIND_LINE) {
    $poles = array_map(static fn ($r) => ['تیر ' . $r[0] . ($r[1] === '' ? '' : ' — ' . $r[1]),
        $r[2] . "\nنسبت به قبلی: " . $r[3] . ' · انشعاب ' . $r[4] . "\n" . $r[5] . ($r[6] !== '' ? "\n" . $r[6] : '')], FieldLine::poleTable($payload));
    if ($poles !== []) {
        array_push($blocks, ['h', 'تیرها'], ['table', $poles]);
    }
    $subscribers = array_map(static fn ($r) => ['تیر ' . $r[0] . ' — مشترک ' . $r[1] . ($r[2] === '' ? '' : ' — ' . $r[2]),
        implode("\n", array_filter([$r[3] . ' · ' . $r[4], $r[5] === '' ? '' : 'کد پستی: ' . $r[5], $r[6], $r[7], $r[8], $r[9]]))],
        FieldSubscribers::table($payload));
    if ($subscribers !== []) {
        $limits = FieldSubscribers::limitsText($payload);
        array_push($blocks, ['h', 'مشترکان نامتعارف']);
        if ($limits !== null) {
            $blocks[] = ['p', $limits];
        }
        $blocks[] = ['table', $subscribers];
    }
}
if ($files !== []) {
    array_push($blocks, ['h', 'فایل‌ها'], ['p', 'فایل‌ها روی سرور نگه داشته می‌شوند؛ اثر انگشت هر فایل برای سنجش اصالت آمده است.'],
        ['table', $files]);
}
array_push($blocks, ['h', 'تاریخچه'], ['table', $events]);
Docx::send('field-' . preg_replace('/[^A-Za-z0-9-]/', '', (string) $item['tracking_code']), $blocks);
