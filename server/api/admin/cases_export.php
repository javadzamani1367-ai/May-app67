<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require(CASE_ROLES);

// اکسل پرونده‌ها با همان فیلترهای آرشیو، و برگه دوم آمار تجمیعی همان پرونده‌ها
// — مثل خروجی اکسل نرم‌افزار ویندوز؛ با format=docx همان در Word رسمی.
$filter = CaseArchive::filters($_GET, admin_scope());
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
if (($_GET['format'] ?? '') === 'docx') {
    // Word رسمی: آمار تجمیعی و فهرست پرونده‌ها با ستون‌های اصلی، همان قلم‌های فرم.
    $keep = [0, 1, 2, 4, 7, 10, 13, 14, 15, 17];
    $header = array_map(static fn ($i) => CaseArchive::EXCEL_HEADER[$i] ?? '', $keep);
    OfficialDocx::send('cases-' . date('Ymd'), [
        'title' => 'آمار پرونده‌های بازدید مراکز استخراج رمزارز',
        'subtitle' => 'تهیه‌شده در ' . Jalali::format(Db::now(), false),
        'tables' => [
            ['caption' => 'آمار تجمیعی', 'header' => ['شاخص', 'مقدار'],
                'rows' => array_values(array_filter($stats, static fn ($r) => $r[0] !== '')), 'widths' => [3, 1]],
            ['caption' => 'فهرست پرونده‌ها', 'header' => $header,
                'rows' => array_map(static fn ($r) => array_map(static fn ($i) => $r[$i] ?? '', $keep), $rows)],
        ],
        'cell_size' => 18,
    ]);
    exit;
}
Xlsx::sendBook('cases-' . date('Ymd'), [
    ['پرونده‌ها', CaseArchive::EXCEL_HEADER, $rows],
    ['آمار', ['شاخص', 'مقدار'], $stats],
]);
