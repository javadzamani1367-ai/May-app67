<?php
declare(strict_types=1);

/**
 * کار ناحیه‌ای از سر تا ته: مدیر گزارشی ثبت می‌کند و فراموش می‌کند بسپارد، سرور
 * خودش به مجری ناحیه می‌دهد، مجری به کارشناس ناحیه، کارشناس دیر می‌کند و هشدار
 * پله‌پله تا مدیر بالا می‌رود؛ بعد بازدید، تأیید مجری، ارسال به واحد همان ناحیه با
 * مرحله اقدام، سریال تکراری که فقط مدیر می‌بیند، امتیازها، و برق غیرمجاز.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $managerToken
 * @var string $config
 */

echo "\n— کاربرهای ناحیه —\n";
$make = static function (array $data) use ($probe, $managerToken): void {
    $saved = $probe->json('POST', 'users/save', $data + ['password' => 'area-pass-123'], $managerToken);
    check('ساخت ' . $data['user_code'], ($saved['body']['ok'] ?? false) === true, $saved['raw']);
};
$make(['user_code' => '401-exec', 'full_name' => 'مجری ایلام', 'role' => 4, 'areas' => '401']);
$make(['user_code' => '410-exec', 'full_name' => 'مجری دره‌شهر', 'role' => 4, 'areas' => ['410']]);
$make(['user_code' => '401-legal', 'full_name' => 'حقوقی ایلام', 'role' => 2, 'unit' => 2, 'areas' => '401']);
$make(['user_code' => '410-legal', 'full_name' => 'حقوقی دره‌شهر', 'role' => 2, 'unit' => 2, 'areas' => '410']);
$make(['user_code' => '2001', 'full_name' => 'کارشناس ایلام', 'role' => 0, 'areas' => '401', 'device_code' => 'DEV-401']);
$make(['user_code' => 'ill', 'full_name' => 'مجری غیرمجاز', 'role' => 5]);
$bad = $probe->json('POST', 'users/save', ['user_code' => 'x-exec', 'full_name' => 'بی‌ناحیه', 'role' => 4, 'password' => 'area-pass-123'], $managerToken);
equals('مجری بی‌ناحیه پذیرفته نیست', 400, $bad['status']);

$token = static function (string $code, ?string $device = null) use ($probe): string {
    $login = $probe->json('POST', 'auth/login', ['user_code' => $code, 'password' => 'area-pass-123'] + ($device ? ['device_code' => $device] : []));
    return (string) ($login['body']['data']['token'] ?? '');
};
$exec401 = $token('401-exec');
$exec410 = $token('410-exec');
$legal401 = $token('401-legal');
$legal410 = $token('410-legal');
$expert401 = $token('2001', 'DEV-401');
$illegal = $token('ill');
check('همه وارد شدند', $exec401 !== '' && $exec410 !== '' && $legal401 !== '' && $expert401 !== '' && $illegal !== '');
$id = static fn (string $code) => (string) $pdo->query("SELECT id FROM users WHERE user_code = '$code'")->fetchColumn();
$tick = static function (int $at) use ($config): void {
    exec('INSPECTION_CONFIG=' . escapeshellarg($config) . ' php ' . escapeshellarg(__DIR__ . '/../lib/tick.php') . ' ' . $at, $out);
};

echo "\n— ثبت مدیر و ارجاع خودکار —\n";
$intake = $probe->json('POST', 'cases/intake', ['report_type' => 3, 'area_code' => '401', 'address' => 'ایلام، کوی بانک',
    'subscriber_name' => 'مشترک نمونه', 'note' => 'گزارش مردمی تلفنی'], $managerToken);
$caseId = (string) ($intake['body']['data']['id'] ?? '');
check('مدیر گزارش ثبت کرد', $caseId !== '', $intake['raw']);
$case = $pdo->query("SELECT * FROM reports WHERE id = '$caseId'")->fetch(PDO::FETCH_ASSOC);
check('کد موقت سرور با S', (bool) preg_match('/^M-401-\d{6}-S\d{4}$/', (string) $case['temp_code']), (string) $case['temp_code']);
$flow = $pdo->query("SELECT * FROM case_flow WHERE report_id = '$caseId'")->fetch(PDO::FETCH_ASSOC);
check('مهلت ارجاع مدیر تعیین شد', $flow['refer_due_at'] !== null);
equals('مجری ناحیه دیگر پرونده را نمی‌بیند', 404, $probe->json('GET', "cases/flow&report_id=$caseId", [], $exec410)['status']);
$board = $probe->json('GET', 'cases/board', [], $exec401)['body']['data']['cases'] ?? [];
$onBoard = array_values(array_filter($board, static fn ($c) => $c['id'] === $caseId))[0] ?? [];
equals('روی میز مجری ناحیه، در انتظار ارجاع مدیر', 'awaiting_referral', $onBoard['stage'] ?? '');

$tick((int) $flow['refer_due_at'] + 60_000);
$flow = $pdo->query("SELECT * FROM case_flow WHERE report_id = '$caseId'")->fetch(PDO::FETCH_ASSOC);
equals('پس از مهلت، خودکار به مجری ناحیه رسید', $id('401-exec'), (string) $flow['executor_id']);
equals('ارجاع خودکار علامت خورد', 1, (int) $flow['auto_referred']);
equals('مجری اعلان گرفت', 1, (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE user_id = '{$id('401-exec')}' AND kind = 'case_referred'")->fetchColumn());

echo "\n— ارجاع به کارشناس و هشدار پله‌ای —\n";
$wrong = $probe->json('POST', 'cases/expert', ['report_id' => $caseId, 'expert_id' => $id('1042')], $exec401);
equals('کارشناس ناحیه دیگر پذیرفته نیست', 409, $wrong['status']);
$assign = $probe->json('POST', 'cases/expert', ['report_id' => $caseId, 'expert_id' => $id('2001'), 'note' => 'فوری'], $exec401);
check('مجری به کارشناس ناحیه ارجاع داد', ($assign['body']['ok'] ?? false) === true, $assign['raw']);
equals('پرونده به نام کارشناس شد', '2001', (string) $pdo->query("SELECT expert_code FROM reports WHERE id = '$caseId'")->fetchColumn());
$assigned = $probe->json('GET', 'cases/assigned', [], $expert401)['body']['data']['assigned'] ?? [];
equals('کارشناس ارجاع را می‌بیند', $caseId, $assigned[0]['report_id'] ?? '');
$manifest = $probe->json('GET', 'sync/manifest&since=0', [], $expert401)['body']['data']['reports'] ?? [];
check('پرونده در همگام‌سازی کارشناس هست', in_array($caseId, array_column($manifest, 'id'), true));
$execManifest = $probe->json('GET', 'sync/manifest&since=0', [], $exec410)['body']['data']['reports'] ?? [];
check('مجری ۴۱۰ پرونده ۴۰۱ را نمی‌گیرد', !in_array($caseId, array_column($execManifest, 'id'), true));

$flow = $pdo->query("SELECT * FROM case_flow WHERE report_id = '$caseId'")->fetch(PDO::FETCH_ASSOC);
$tick((int) $flow['due_at'] + 60_000);
$flow = $pdo->query("SELECT * FROM case_flow WHERE report_id = '$caseId'")->fetch(PDO::FETCH_ASSOC);
check('کارشناس دیر کرد: هشدار به مجری', $flow['expert_alert_at'] !== null);
equals('مجری هشدار گرفت', 1, (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE user_id = '{$id('401-exec')}' AND kind = 'expert_late'")->fetchColumn());
$tick((int) $flow['expert_alert_at'] + 10 * 86_400_000);
$alert = (string) $pdo->query("SELECT body FROM notifications WHERE role = 1 AND kind = 'executor_late' AND report_id = '$caseId'")->fetchColumn();
check('مجری هم پیگیری نکرد: هشدار به مدیر با روزهای هر دو', str_contains($alert, 'مجری') && str_contains($alert, 'کارشناس'), $alert);

echo "\n— بازدید، تأیید مجری و دیده‌شدن —\n";
$pulled = $probe->json('GET', "sync/report&id=$caseId", [], $expert401)['body']['data']['report'] ?? [];
$pulled['status'] = 1;
$pulled['updated_at'] = (int) $pulled['updated_at'] + 5000;
$pulled['visit_date'] = (int) $pulled['updated_at'];
$pulled['devices'] = [['id' => 'dddddddd-0000-0000-0000-000000000001', 'row_number' => 1, 'model' => 'S19', 'serial_number' => 'abc-12 345',
    'power_watt' => 3250, 'entry_method' => 0]];
$push = $probe->json('POST', 'sync/report', ['report' => $pulled, 'device_code' => 'DEV-401'], $expert401);
check('بازدید رسید', ($push['body']['ok'] ?? false) === true, $push['raw']);
check('زمان بازدید در گردش ثبت شد', $pdo->query("SELECT visited_at FROM case_flow WHERE report_id = '$caseId'")->fetchColumn() !== null);
equals('کارشناس دیگر پرونده را نمی‌نویسد', 403, $probe->json('POST', 'sync/report', ['report' => $pulled], $expertToken)['status']);
$probe->json('POST', 'approvals/submit', ['report_id' => $caseId], $expert401);
$probe->json('GET', "cases/flow&report_id=$caseId", [], $exec401);
$mine = $probe->json('GET', 'cases/mine', [], $expert401)['body']['data']['statuses'] ?? [];
equals('کارشناس می‌بیند که مجری دید', 4, $mine[0]['seen_role'] ?? null);
$decide = $probe->json('POST', 'approvals/decide', ['report_id' => $caseId, 'decision' => 1], $exec401);
check('مجری تأیید کرد', ($decide['body']['ok'] ?? false) === true, $decide['raw']);
equals('تأیید روی پرونده نشست', 2, (int) $pdo->query("SELECT approval_state FROM reports WHERE id = '$caseId'")->fetchColumn());
equals('مجری ناحیه دیگر تأیید نمی‌کند', 404, $probe->json('POST', 'approvals/decide', ['report_id' => $caseId, 'decision' => 1], $exec410)['status']);

echo "\n— واحد ناحیه و مراحل اقدام —\n";
$sent = $probe->json('POST', 'dispatches/create', ['report_id' => $caseId, 'unit' => 2, 'deadline_days' => 7], $exec401);
check('مجری به حقوقی فرستاد', ($sent['body']['ok'] ?? false) === true, $sent['raw']);
equals('کارشناس به واحد نمی‌فرستد', 403, $probe->json('POST', 'dispatches/create', ['report_id' => $caseId, 'unit' => 0], $expert401)['status']);
$inbox = $probe->json('GET', 'dispatches/inbox', [], $legal401)['body']['data'] ?? [];
equals('حقوقی ایلام می‌گیرد', 1, count($inbox['dispatches'] ?? []));
check('حقوقی دره‌شهر نمی‌گیرد', !in_array($caseId, array_column($probe->json('GET', 'dispatches/inbox', [], $legal410)['body']['data']['dispatches'] ?? [], 'report_id'), true));
$dispatchId = (string) ($inbox['dispatches'][0]['id'] ?? '');
equals('بی مرحله پذیرفته نیست', 400, $probe->json('POST', 'dispatches/respond', ['dispatch_id' => $dispatchId, 'body' => 'بررسی شد'], $legal401)['status']);
$respond = $probe->json('POST', 'dispatches/respond', ['dispatch_id' => $dispatchId, 'body' => 'شکوائیه تنظیم شد', 'stage' => 1], $legal401);
check('واحد مرحله ثبت کرد', ($respond['body']['ok'] ?? false) === true, $respond['raw']);
equals('مرحله روی ارسال نشست', 1, (int) $pdo->query("SELECT stage FROM dispatches WHERE id = '$dispatchId'")->fetchColumn());
equals('واحد ناحیه دیگر پاسخ نمی‌دهد', 404, $probe->json('POST', 'dispatches/respond', ['dispatch_id' => $dispatchId, 'body' => 'x', 'stage' => 2], $legal410)['status']);

echo "\n— بانک دستگاه‌ها —\n";
$other = $pulled;
$other['id'] = 'cccccccc-4010-4010-4010-000000000001';
$other['tracking_code'] = 'M-401-050701-111111';
$other['temp_code'] = null;
$other['devices'][0]['id'] = 'dddddddd-0000-0000-0000-000000000002';
$other['devices'][0]['serial_number'] = 'ABC12345';
$probe->json('POST', 'sync/report', ['report' => $other, 'device_code' => 'DEV-401'], $expert401);
equals('سریال تکراری یک هشدار ساخت', 1, (int) $pdo->query('SELECT COUNT(*) FROM device_alerts')->fetchColumn());
equals('فقط مدیر هشدار تکراری گرفت', 1, (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE kind = 'device_duplicate' AND role = 1")->fetchColumn());
equals('هشدار تکراری برای مجری نیست', 0, (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE kind = 'device_duplicate' AND (role <> 1 OR user_id IS NOT NULL)")->fetchColumn());
equals('مجری بانک دستگاه را نمی‌بیند', 403, $probe->json('GET', 'manage/devices', [], $exec401)['status']);
$found = $probe->json('GET', 'manage/devices&q=ABC12345', [], $managerToken)['body']['data']['devices'] ?? [];
equals('مدیر هر دو جای سریال را می‌بیند', 2, count($found));
equals('دفعات دیده‌شدن', 2, $found[0]['seen_in'] ?? null);

echo "\n— امتیازها —\n";
// زمان‌های ساختگی تست (ارجاع خودکار و هشدارها) در آینده‌اند؛ بازه تا همان‌جا.
$scores = $probe->json('GET', 'manage/scores&from=0&to=99999999999999', [], $managerToken)['body']['data'] ?? [];
equals('مدیر همه ۱۷ ناحیه را دارد', 17, count($scores['areas'] ?? []));
$own = $probe->json('GET', 'manage/scores&from=0', [], $exec401)['body']['data'] ?? [];
equals('مجری فقط ناحیه خودش', ['ایلام — ناحیه ۴۰۱'], array_column($own['areas'] ?? [], 'name'));
$execRow = array_values(array_filter($scores['executors'] ?? [], static fn ($r) => $r['name'] === 'مجری ایلام'))[0] ?? [];
check('مجری بی‌اقدام امتیاز کامل نمی‌گیرد', ($execRow['score'] ?? 100) < 100, json_encode($execRow, JSON_UNESCAPED_UNICODE));

echo "\n— برق غیرمجاز —\n";
$mgrId = $id('mgr');
$pdo->exec("INSERT INTO field_items (id, kind, tracking_code, user_id, created_at, client_updated_at, received_at, updated_at, status, area_code, description)
            VALUES ('99999999-0000-0000-0000-000000000001', 2, 'GH-1405-000900', '$mgrId', 1, 1, 1, 1, 0, '410', 'انشعاب غیرمجاز'),
                   ('99999999-0000-0000-0000-000000000002', 1, 'RZ-1405-000900', '$mgrId', 1, 1, 1, 1, 0, '401', 'ماینر')");
$codes = static fn (string $t) => array_column($probe->json('GET', 'manage/field/items', [], $t)['body']['data']['items'] ?? [], 'tracking_code');
check('مجری غیرمجاز گزارش غیرمجاز را می‌بیند', in_array('GH-1405-000900', $codes($illegal), true));
check('مجری غیرمجاز رمزارز را نمی‌بیند', !in_array('RZ-1405-000900', $codes($illegal), true));
check('مجری رمزارز گزارش رمزارز اپ میدانی را نمی‌بیند', !in_array('RZ-1405-000900', $codes($exec401), true));
check('مجری ۴۰۱ غیرمجاز ۴۱۰ را نمی‌بیند', !in_array('GH-1405-000900', $codes($exec401), true));
$hand = $probe->json('POST', 'manage/field/hand', ['id' => '99999999-0000-0000-0000-000000000001', 'executor_id' => $id('401-exec'), 'note' => 'پیگیری شود'], $illegal);
check('مجری غیرمجاز به مجری ایلام سپرد', ($hand['body']['ok'] ?? false) === true, $hand['raw']);
check('حالا مجری ایلام می‌بیند', in_array('GH-1405-000900', $codes($exec401), true));
$item = $probe->json('GET', 'manage/field/item&id=99999999-0000-0000-0000-000000000001', [], $illegal)['body']['data']['item'] ?? [];
check('سپارنده سپردن را در تاریخچه می‌بیند', str_contains(json_encode($item['events'] ?? [], JSON_UNESCAPED_UNICODE), 'سپردن به مجری'));
equals('مجری رمزارز نمی‌تواند به مجری دیگر بسپارد', 409, $probe->json('POST', 'manage/field/hand',
    ['id' => '99999999-0000-0000-0000-000000000001', 'executor_id' => $id('410-exec')], $exec401)['status']);
