<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// اکسل پرونده‌ها با همان فیلترهای آرشیو، و برگه دوم آمار تجمیعی همان پرونده‌ها
// — مثل خروجی اکسل نرم‌افزار ویندوز.
$filter = CaseArchive::filters($_GET);
$rows = array_map([CaseArchive::class, 'excelRow'], CaseArchive::rows($filter, 20000));
$s = CaseArchive::stats($filter);
$stats = [
    ['تعداد کل پرونده‌ها', $s['cases']],
    ['دستگاه‌های ثبت‌شده', $s['devices']],
    ['مجموع توان (وات)', round($s['watt'])],
    ['کنتور دستکاری‌شده', $s['tampered']],
    ['برداشت قبل از کنتور', $s['before_meter']],
    ['', ''],
];
foreach (['status' => [CaseLabels::STATUSES, 'وضعیت'], 'type' => [CaseLabels::REPORT_TYPES, 'نوع گزارش']] as $key => [$names, $title]) {
    foreach ($s[$key] as $row) {
        $stats[] = [$title . ': ' . (CaseLabels::of($names, $row['k']) ?? '—'), (int) $row['n']];
    }
    $stats[] = ['', ''];
}
foreach ($s['county'] as $row) {
    $stats[] = ['شهرستان: ' . ((string) $row['k'] ?: '—'), (int) $row['n']];
}
Xlsx::sendBook('cases-' . date('Ymd'), [
    ['پرونده‌ها', CaseArchive::EXCEL_HEADER, $rows],
    ['آمار', ['شاخص', 'مقدار'], $stats],
]);
