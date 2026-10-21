<?php
declare(strict_types=1);

/**
 * گردش پرونده برق غیرمجاز از سر تا ته: گزارش اپ میدانی پرونده می‌شود و نزد مجری
 * غیرمجاز همان شهرستان می‌رود، ستاد باخبر می‌شود؛ مجری رمزارز آن را نمی‌بیند و مدیر
 * فقط ناظر است؛ ارجاع به کارشناس، بازرسی، تأیید و ارسال به واحد با مجری شهرستان؛ اگر
 * او ارجاع ندهد ستاد هشدار می‌گیرد و اگر ستاد هم ندهد، ارجاع خودکار به کارشناس.
 * از persons.php: شخص «علی نمونه» با کاربری‌های ۴۱۰.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $managerToken
 * @var string $illegal ستاد
 * @var string $exec410 مجری رمزارز ۴۱۰
 * @var string $legal410
 * @var string $illCode
 * @var string $expCode
 * @var array<string, string> $made
 * @var string $reset رمز تازه کارشناس ۴۱۰
 * @var callable $tick
 * @var callable $id
 * @var callable $nid
 */

echo "\n— برق غیرمجاز: گزارش میدانی تا پرونده —\n";
$pdo->exec('DELETE FROM login_attempts');
$reporterId = (string) ($probe->json('POST', 'persons/save', ['full_name' => 'گزارشگر دره‌شهر', 'national_id' => $nid('450222333'),
    'home_area' => '410'], $managerToken)['body']['data']['id'] ?? '');
$field = $probe->json('POST', 'persons/accounts', ['person_id' => $reporterId, 'ticks' => ['410:field'],
    'permissions' => ['410:field' => 2]], $managerToken)['body']['data']['made'][0] ?? [];
bindDevice($probe, $pdo, $managerToken, (string) ($field['user_code'] ?? ''), 'field', 'FFFF-0000-1111-2222');
$reporter = (string) ($probe->json('POST', 'auth/login', ['user_code' => $field['user_code'] ?? '', 'password' => $field['password'] ?? '',
    'app' => 'field', 'device_code' => 'FFFF-0000-1111-2222'])['body']['data']['token'] ?? '');
check('گزارشگر اپ میدانی وارد شد', $reporter !== '');
$uuid = static fn (): string => vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex(random_bytes(16)), 4));
$ghItem = static fn (string $address) => ['id' => $uuid(), 'kind' => 2, 'created_at' => (int) (microtime(true) * 1000), 'updated_at' => (int) (microtime(true) * 1000),
    'latitude' => 33.0, 'longitude' => 47.3, 'accuracy' => 6, 'address' => $address, 'description' => 'سیم کشی از تیر', 'priority' => 0,
    'payload' => ['crew' => []]];
$first = $ghItem('دره‌شهر، کوی بهار');
$sent = $probe->json('POST', 'field/item', ['item' => $first, 'files' => []], $reporter);
$ghCode = (string) ($sent['body']['data']['tracking_code'] ?? '');
check('گزارش برق غیرمجاز رسید', str_starts_with($ghCode, 'GH-'), $sent['raw']);
$case = $pdo->query("SELECT r.*, f.executor_id, f.county_due_at, f.origin FROM reports r JOIN case_flow f ON f.report_id = r.id
                     WHERE r.tracking_code = '$ghCode'")->fetch(PDO::FETCH_ASSOC) ?: [];
$ghCase = (string) ($case['id'] ?? '');
equals('پرونده غیرمجاز با همان کد و ناحیه گزارشگر', [1, '410', 3], [(int) ($case['case_kind'] ?? 0), $case['area_code'] ?? null, (int) ($case['origin'] ?? 0)]);
equals('نزد مجری غیرمجاز همان شهرستان', $id($illCode), $case['executor_id'] ?? null);
equals('گزارش میدانی به پرونده وصل شد', $ghCase, (string) $pdo->query("SELECT report_id FROM field_items WHERE id = '{$first['id']}'")->fetchColumn());
check('مجری شهرستان و ستاد باخبر شدند', (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE report_id = '$ghCase' AND kind = 'illegal_new'
    AND ((role = 6 AND area_code = '410') OR role = 5)")->fetchColumn() === 2);

$illArea = (string) ($probe->json('POST', 'auth/login', ['user_code' => $illCode, 'password' => $made[$illCode],
    'app' => 'illegal', 'device_code' => 'CCCC-0000-1111-2222'])['body']['data']['token'] ?? '');
$boardIds = static fn (string $t) => array_column($probe->json('GET', 'cases/board', [], $t)['body']['data']['cases'] ?? [], 'id');
check('روی میز مجری غیرمجاز شهرستان', in_array($ghCase, $boardIds($illArea), true));
check('روی میز ستاد', in_array($ghCase, $boardIds($illegal), true));
check('مجری رمزارز همان ناحیه نمی‌بیند', !in_array($ghCase, $boardIds($exec410), true));
check('مدیر ناظر است و می‌بیند', in_array($ghCase, $boardIds($managerToken), true));
$expertId = $id($expCode);
equals('مدیر ارجاع نمی‌دهد', 409, $probe->json('POST', 'cases/expert', ['report_id' => $ghCase, 'expert_id' => $expertId], $managerToken)['status']);
$refer = $probe->json('POST', 'cases/expert', ['report_id' => $ghCase, 'expert_id' => $expertId, 'note' => 'فوری'], $illArea);
check('مجری شهرستان به کارشناس ارجاع داد', ($refer['body']['ok'] ?? false) === true, $refer['raw']);

echo "\n— بازرسی کارشناس، تأیید و ارسال —\n";
$expertTok = (string) ($probe->json('POST', 'auth/login', ['user_code' => $expCode, 'password' => $reset, 'app' => 'expert',
    'device_code' => 'DDDD-0000-1111-2222'])['body']['data']['token'] ?? '');
$assigned = $probe->json('GET', 'cases/assigned', [], $expertTok)['body']['data']['assigned'] ?? [];
equals('کارشناس پرونده غیرمجاز را می‌گیرد', [$ghCase, 1], [$assigned[0]['report_id'] ?? null, $assigned[0]['case_kind'] ?? null]);
$pulled = $probe->json('GET', "sync/report&id=$ghCase", [], $expertTok)['body']['data']['report'] ?? [];
$pulled['status'] = 1;
$pulled['case_kind'] = 0;
$pulled['updated_at'] = (int) $pulled['updated_at'] + 5000;
$pulled['description'] = 'انشعاب غیرمجاز از تیر برق';
$push = $probe->json('POST', 'sync/report', ['report' => $pulled, 'device_code' => 'DDDD-0000-1111-2222'], $expertTok);
check('بازرسی رسید', ($push['body']['ok'] ?? false) === true, $push['raw']);
equals('حوزه پرونده را گوشی عوض نمی‌کند', 1, (int) $pdo->query("SELECT case_kind FROM reports WHERE id = '$ghCase'")->fetchColumn());
equals('گزارشگر نتیجه را در اپ میدانی می‌بیند', 3, (int) $pdo->query("SELECT status FROM field_items WHERE id = '{$first['id']}'")->fetchColumn());
$probe->json('POST', 'approvals/submit', ['report_id' => $ghCase], $expertTok);
equals('نتیجه به مجری شهرستان و ستاد رسید، نه مدیر', [1, 1, 0], array_map(static fn ($r) => (int) $pdo->query(
    "SELECT COUNT(*) FROM notifications WHERE report_id = '$ghCase' AND kind = 'approval_pending' AND $r")->fetchColumn(),
    ["role = 6 AND area_code = '410'", 'role = 5', 'role = 1']));
equals('ستاد پیش از مهلت جای مجری شهرستان تأیید نمی‌کند', 403,
    $probe->json('POST', 'approvals/decide', ['report_id' => $ghCase, 'decision' => 1], $illegal)['status']);
equals('مدیر تأیید نمی‌کند', 403, $probe->json('POST', 'approvals/decide', ['report_id' => $ghCase, 'decision' => 1], $managerToken)['status']);
$decide = $probe->json('POST', 'approvals/decide', ['report_id' => $ghCase, 'decision' => 1], $illArea);
check('مجری شهرستان تأیید کرد', ($decide['body']['ok'] ?? false) === true, $decide['raw']);
equals('مدیر به واحد نمی‌فرستد', 403, $probe->json('POST', 'dispatches/create', ['report_id' => $ghCase, 'unit' => 2], $managerToken)['status']);
$out = $probe->json('POST', 'dispatches/create', ['report_id' => $ghCase, 'unit' => 2, 'deadline_days' => 5], $illArea);
check('مجری شهرستان به حقوقی همان شهرستان فرستاد', ($out['body']['ok'] ?? false) === true, $out['raw']);
// رمز حقوقی ۴۱۰ در persons.php عوض شد و نشست قبلی بسته شد.
$legal410 = (string) ($probe->json('POST', 'auth/login', ['user_code' => '410-legal', 'password' => '12345678'])['body']['data']['token'] ?? '');
$inbox = $probe->json('GET', 'dispatches/inbox', [], $legal410)['body']['data']['dispatches'] ?? [];
$row = array_values(array_filter($inbox, static fn ($d) => $d['report_id'] === $ghCase))[0] ?? [];
equals('صندوق واحد نوع پرونده را می‌گوید', 'برق غیرمجاز', $row['kind_name'] ?? null);

echo "\n— ارجاع نشد: هشدار به ستاد، بعد ارجاع خودکار —\n";
$second = $ghItem('دره‌شهر، روستای سیکان');
$code2 = (string) ($probe->json('POST', 'field/item', ['item' => $second, 'files' => []], $reporter)['body']['data']['tracking_code'] ?? '');
$case2 = (string) $pdo->query("SELECT id FROM reports WHERE tracking_code = '$code2'")->fetchColumn();
$due = (int) $pdo->query("SELECT county_due_at FROM case_flow WHERE report_id = '$case2'")->fetchColumn();
$tick($due + 60_000);
$flow2 = $pdo->query("SELECT * FROM case_flow WHERE report_id = '$case2'")->fetch(PDO::FETCH_ASSOC) ?: [];
check('مجری شهرستان ارجاع نداد: هشدار به ستاد', ($flow2['hq_alert_at'] ?? null) !== null && ($flow2['hq_due_at'] ?? null) !== null);
check('ستاد اعلان گرفت', (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE report_id = '$case2' AND role = 5 AND kind = 'county_late'")->fetchColumn() === 1);
$stages = array_column($probe->json('GET', 'cases/board', [], $illegal)['body']['data']['cases'] ?? [], 'stage', 'id');
equals('میز ستاد: نزد ستاد', 'with_hq', $stages[$case2] ?? null);
$tick((int) $flow2['hq_due_at'] + 60_000);
$auto = $pdo->query("SELECT f.expert_id, f.auto_referred, r.expert_code FROM case_flow f JOIN reports r ON r.id = f.report_id
                     WHERE f.report_id = '$case2'")->fetch(PDO::FETCH_ASSOC) ?: [];
equals('ستاد هم ارجاع نداد: خودکار به کارشناس همان شهرستان', [$expertId, 1, $expCode],
    [$auto['expert_id'] ?? null, (int) ($auto['auto_referred'] ?? 0), $auto['expert_code'] ?? null]);

echo "\n— امتیاز و عملکرد حوزه غیرمجاز —\n";
$scores = $probe->json('GET', 'manage/scores&to=99999999999999', [], $illegal)['body']['data'] ?? [];
equals('امتیاز ستاد، حوزه برق غیرمجاز', 1, $scores['case_kind'] ?? null);
check('مجری غیرمجاز شهرستان در جدول مجریان', in_array($id($illCode), array_column($scores['executors'] ?? [], 'id'), true));
$crypto = $probe->json('GET', 'manage/scores&to=99999999999999', [], $managerToken)['body']['data'] ?? [];
check('امتیاز مدیر پیش‌فرض رمزارز است و مجری غیرمجاز در آن نیست', ($crypto['case_kind'] ?? null) === 0
    && !in_array($id($illCode), array_column($crypto['executors'] ?? [], 'id'), true));
$perf = $probe->json('GET', 'stats/units&kind=1', [], $managerToken)['body']['data']['units'] ?? [];
equals('مدیر عملکرد واحدها در برق غیرمجاز را می‌گیرد', 1, (int) ($perf[0]['sent'] ?? 0));
