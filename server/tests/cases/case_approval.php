<?php
declare(strict_types=1);

/**
 * چرخه تأیید و کشیدن پرونده توسط مدیر — همان مسیری که تا این هفته اصلاً
 * کار نمی‌کرد، چون پرونده‌ای روی سرور نبود که درخواست تأیید به آن وصل شود.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $expertToken
 * @var string $managerToken
 * @var string $reportId
 * @var string $mediaId
 */

echo "\n— چرخه تأیید —\n";
$submit = $probe->json('POST', 'approvals/submit', ['report_id' => $reportId], $expertToken);
check('کارشناس پرونده را برای تأیید می‌فرستد', ($submit['body']['ok'] ?? false) === true, $submit['raw']);

$pending = $probe->json('GET', 'approvals', [], $managerToken);
$queue = $pending['body']['data']['pending'] ?? [];
equals('پرونده در صف مدیر است', 1, count($queue));
equals('با کد رهگیری خودش', 'M-401-050614-482917', $queue[0]['tracking_code'] ?? '');

$notified = (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE kind = 'approval_pending'")->fetchColumn();
equals('مدیر اعلان گرفت', 1, $notified);

$expertQueue = $probe->json('GET', 'approvals', [], $expertToken);
equals('کارشناس صف مدیر را نمی‌بیند', 403, $expertQueue['status']);

echo "\n— مدیر پرونده را می‌کشد —\n";
$manifest = $probe->json('GET', 'sync/manifest&since=0', [], $managerToken);
$listed = $manifest['body']['data']['reports'] ?? [];
equals('مدیر پرونده را در فهرست می‌بیند', 1, count($listed));

$pulled = $probe->json('GET', "sync/report&id=$reportId", [], $managerToken);
$case = $pulled['body']['data']['report'] ?? [];
check('پرونده کامل پایین می‌آید', ($pulled['body']['ok'] ?? false) === true, $pulled['raw']);
equals('با دستگاهش', 1, count($case['devices'] ?? []));
equals('با حاضرینش', 1, count($case['attendees'] ?? []));
equals('با رسانه‌اش', 1, count($case['media'] ?? []));
equals('ردیف دستگاه درست خوانده شد', 1, (int) ($case['devices'][0]['row_number'] ?? 0));
// ناحیه باید تا اینجا سالم برسد: مدیر از روی همین تصمیم می‌گیرد پرونده مال
// کدام ناحیه ایلام است.
equals('ناحیه در پرونده کشیده‌شده هست', '410', $case['area_code'] ?? '');
check('synced_at سرور همراه پرونده نمی‌رود', !array_key_exists('synced_at', $case));

$managerFile = $probe->raw("sync/file&kind=media&id=$mediaId", $managerToken);
equals('مدیر تصویر را می‌گیرد', 200, $managerFile['status']);

echo "\n— تصمیم مدیر —\n";
$decide = $probe->json('POST', 'approvals/decide', [
    'report_id' => $reportId,
    'decision' => 2,
    'comment' => 'مختصات ناقص است',
], $managerToken);
check('مدیر پرونده را برمی‌گرداند', ($decide['body']['ok'] ?? false) === true, $decide['raw']);

$returned = $pdo->query("SELECT decision, comment FROM approvals WHERE report_id = '$reportId'")
    ->fetch(PDO::FETCH_ASSOC);
equals('تصمیم ثبت شد', 2, (int) ($returned['decision'] ?? 0));
equals('با نظر مدیر', 'مختصات ناقص است', $returned['comment'] ?? '');

$expertNote = $pdo->query("SELECT COUNT(*) FROM notifications WHERE kind = 'returned'")->fetchColumn();
equals('کارشناس اعلان برگشت گرفت', 1, (int) $expertNote);

$twice = $probe->json('POST', 'approvals/decide', [
    'report_id' => $reportId,
    'decision' => 1,
    'comment' => '',
], $managerToken);
equals('تصمیم دوباره روی همان درخواست رد می‌شود', 409, $twice['status']);

$expertDecides = $probe->json('POST', 'approvals/decide', [
    'report_id' => $reportId,
    'decision' => 1,
], $expertToken);
equals('کارشناس نمی‌تواند خودش را تأیید کند', 403, $expertDecides['status']);

echo "\n— اصلاح و تأیید نهایی —\n";
$resubmit = $probe->json('POST', 'approvals/submit', ['report_id' => $reportId], $expertToken);
check('کارشناس دوباره می‌فرستد', ($resubmit['body']['ok'] ?? false) === true);

$approve = $probe->json('POST', 'approvals/decide', [
    'report_id' => $reportId,
    'decision' => 1,
    'comment' => 'تأیید شد',
], $managerToken);
check('مدیر تأیید می‌کند', ($approve['body']['ok'] ?? false) === true, $approve['raw']);

$history = (int) $pdo->query("SELECT COUNT(*) FROM approvals WHERE report_id = '$reportId'")->fetchColumn();
equals('هر دو رفت‌وبرگشت در سابقه ماند', 2, $history);

echo "\n— آمار واحدها —\n";
$stats = $probe->json('GET', 'stats/units&from=0&to=9999999999999', [], $managerToken);
check('گزارش عملکرد واحدها پاسخ می‌دهد', ($stats['body']['ok'] ?? false) === true, $stats['raw']);

$notFound = $probe->json('GET', 'sync/report&id=00000000-0000-0000-0000-000000000000', [], $managerToken);
equals('پرونده ناموجود، ۴۰۴ می‌گیرد', 404, $notFound['status']);

$badRoute = $probe->json('GET', 'no/such/route', [], $managerToken);
equals('مسیر ناموجود، ۴۰۴ می‌گیرد', 404, $badRoute['status']);

require __DIR__ . '/manager_recovery.php';
