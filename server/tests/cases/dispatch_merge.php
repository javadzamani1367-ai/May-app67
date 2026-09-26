<?php
declare(strict_types=1);

/**
 * ارسال‌های گوشی به سرور می‌رسند.
 *
 * گوشی سوابق ارسال را همیشه همراه پرونده می‌فرستاد و سرور نادیده‌شان
 * می‌گرفت؛ گزارش عملکرد واحدها صفر می‌ماند و پرتال واحد خالی. این‌ها همان
 * دو چیزی است که مدیر روی گوشی واقعی دید.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $expertToken
 * @var string $managerToken
 * @var array $report
 * @var string $reportId
 */

echo "\n— ارسال به واحدها —\n";
$dispatchId = 'eeeeeeee-1111-2222-3333-444444444444';
$withDispatch = $report;
// تازه‌تر از نسخه سرور، وگرنه سرور به حق نادیده‌اش می‌گیرد: تصمیم مدیر در
// تست قبلی زمان پرونده را جلو برده است.
$serverTime = (int) $pdo->query("SELECT updated_at FROM reports WHERE id = '$reportId'")->fetchColumn();
$withDispatch['updated_at'] = $serverTime + 1000;
$withDispatch['dispatches'] = [[
    'id' => $dispatchId,
    'report_id' => $reportId,
    'unit' => 2,
    'included_items' => '["' . $mediaId . '"]',
    'note' => 'برای اقدام حقوقی',
    'output_format' => 0,
    'dispatched_at' => 1757800000000,
    'channel' => 0,
    'deadline_at' => 1758400000000,
    'status' => 0,
]];

$push = $probe->json('POST', 'sync/report', ['report' => $withDispatch, 'device_code' => 'DEVICE-AAA'], $expertToken);
check('پرونده با ارسالش پذیرفته می‌شود', ($push['body']['ok'] ?? false) === true, $push['raw']);
equals('ارسال روی سرور ثبت شد', 1, (int) $pdo->query("SELECT COUNT(*) FROM dispatches WHERE id = '$dispatchId'")->fetchColumn());
equals('واحد مقصد درست است', 2, (int) $pdo->query("SELECT unit FROM dispatches WHERE id = '$dispatchId'")->fetchColumn());
equals('آرایه موارد دست‌نخورده رسید', '["' . $mediaId . '"]',
    (string) $pdo->query("SELECT included_items FROM dispatches WHERE id = '$dispatchId'")->fetchColumn());
equals('واحد حقوقی اعلان گرفت', 1, (int) $pdo->query(
    "SELECT COUNT(*) FROM notifications WHERE unit = 2 AND kind = 'dispatch' AND report_id = '$reportId'"
)->fetchColumn());

// واحد در پرتال می‌بیندش و پاسخ می‌دهد؛ پاسخ مال سرور است.
$pdo->exec("UPDATE dispatches SET status = 2, answered_at = 1758000000000, answer = 'اقدام شد' WHERE id = '$dispatchId'");

$withDispatch['updated_at'] = $serverTime + 2000;
$withDispatch['dispatches'][0]['note'] = 'یادداشت اصلاح‌شده';
$again = $probe->json('POST', 'sync/report', ['report' => $withDispatch, 'device_code' => 'DEVICE-AAA'], $expertToken);
check('فرستادن دوباره پذیرفته می‌شود', ($again['body']['ok'] ?? false) === true, $again['raw']);
equals('دوباره فرستادن ارسال را تکرار نمی‌کند', 1,
    (int) $pdo->query("SELECT COUNT(*) FROM dispatches WHERE report_id = '$reportId'")->fetchColumn());
equals('پاسخ واحد با فرستادن دوباره پاک نمی‌شود', 2,
    (int) $pdo->query("SELECT status FROM dispatches WHERE id = '$dispatchId'")->fetchColumn());
equals('یادداشت گوشی به‌روز می‌شود', 'یادداشت اصلاح‌شده',
    (string) $pdo->query("SELECT note FROM dispatches WHERE id = '$dispatchId'")->fetchColumn());
equals('اعلان تکراری ساخته نشد', 1, (int) $pdo->query(
    "SELECT COUNT(*) FROM notifications WHERE unit = 2 AND kind = 'dispatch' AND report_id = '$reportId'"
)->fetchColumn());

$stats = $probe->json('GET', 'stats/units&from=0&to=9999999999999', [], $managerToken);
$legal = null;
foreach ($stats['body']['data']['units'] ?? [] as $unitRow) {
    if ((int) $unitRow['unit'] === 2) {
        $legal = $unitRow;
    }
}
check('گزارش عملکرد دیگر صفر نیست', $legal !== null, $stats['raw']);
equals('یک ارسال به حقوقی', 1, (int) ($legal['sent'] ?? 0));
equals('و یک پاسخ', 1, (int) ($legal['answered'] ?? 0));
