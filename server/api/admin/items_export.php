<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// همان فیلترهای فهرست، همه سطرها. سقف برای اینکه یک کلیک هاست را از کار نیندازد.
$filter = FieldAdmin::filters($_GET);
$rows = Db::all(FieldAdmin::listSql($filter['where']) . ' LIMIT 20000', $filter['params']);

$header = ['کد رهگیری', 'نوع', 'وضعیت', 'مهلت گذشته', 'اولویت', 'ثبت‌کننده', 'کد ثبت‌کننده', 'تاریخ ثبت', 'تاریخ رسیدن',
           'آدرس', 'پلاک', 'عرض جغرافیایی', 'طول جغرافیایی', 'دقت (متر)', 'شرح', 'ارجاع به', 'مهلت'];
$now = Db::now();
$data = [];
foreach ($rows as $row) {
    $data[] = [
        (string) $row['tracking_code'],
        FieldAdmin::KIND_NAMES[(int) $row['kind']] ?? '',
        FieldAdmin::STATUS_NAMES[(int) $row['status']] ?? '',
        FieldAdmin::isOverdue($row, $now) ? 'بله' : '',
        $row['priority'] === null ? '' : (FieldAdmin::PRIORITY_NAMES[(int) $row['priority']] ?? ''),
        (string) $row['reporter'],
        (string) $row['reporter_code'],
        Jalali::format((int) $row['created_at']),
        Jalali::format((int) $row['received_at']),
        (string) $row['address'],
        (string) $row['plate'],
        $row['latitude'] === null ? null : (float) $row['latitude'],
        $row['longitude'] === null ? null : (float) $row['longitude'],
        $row['accuracy'] === null ? null : round((float) $row['accuracy'], 1),
        (string) $row['description'],
        (string) $row['expert'],
        $row['due_at'] === null ? '' : Jalali::format((int) $row['due_at'], false),
    ];
}
Xlsx::send('field-items-' . date('Ymd'), 'موردهای میدانی', $header, $data);
