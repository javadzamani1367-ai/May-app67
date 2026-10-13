<?php
declare(strict_types=1);

/**
 * ارجاع‌ها در اپ کارشناس (مرحله ۸): فهرست و QR فقط برای کارشناسی که ارجاع به
 * او رسیده، عکس گزارش‌دهنده، پذیرش، فرم سبک نتیجه، و نتیجه خودکار وقتی
 * پرونده «گزارش همکار» با کد RZ- بازدیدشده به سرور می‌رسد.
 *
 * از تست‌های قبلی: $push (field_admin_tools)، $expertToken، $reporter، $managerToken،
 * $report و $photo (field_cycle).
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— اپ کارشناس: ارجاع‌های من —\n";
$expertId = (string) $pdo->query("SELECT id FROM users WHERE user_code = '1042'")->fetchColumn();
$crypto = $push(33.7000, 46.5000);
$illegalRef = $push(33.7100, 46.5100, 2);
$byCode = $push(33.7200, 46.5200);
foreach ([$crypto, $illegalRef, $byCode] as $it) {
    $pdo->prepare('UPDATE field_items SET status = 2, assigned_to = ?, due_at = ? WHERE id = ?')
        ->execute([$expertId, 4102444800000, $it['id']]);
}

$list = $probe->json('GET', 'referrals', [], $expertToken);
$ids = array_column($list['body']['data']['referrals'] ?? [], 'id');
check('کارشناس ارجاع‌های خودش را می‌گیرد', in_array($crypto['id'], $ids, true) && in_array($illegalRef['id'], $ids, true));
$one = array_values(array_filter($list['body']['data']['referrals'] ?? [], static fn ($r) => $r['id'] === $crypto['id']))[0] ?? [];
check('چک‌لیست از پیش به فارسی می‌آید', in_array(['نشانه‌ها', 'صدای مداوم فن یا وزوز، به‌خصوص شب'], $one['details'] ?? [], true));
check('نام گزارش‌دهنده بی‌اجازه مدیر نمی‌آید', array_key_exists('reporter', $one) && $one['reporter'] === null);
equals('کاربر میدانی به ارجاع‌ها راه ندارد', 403, $probe->json('GET', 'referrals', [], $reporter)['status']);
equals('مدیر هم (فقط کارشناس)', 403, $probe->json('GET', 'referrals', [], $managerToken)['status']);

equals('QR ارجاع خود کارشناس باز می‌شود', $crypto['code'],
    $probe->json('GET', 'referrals/item&id=' . $crypto['id'], [], $expertToken)['body']['data']['referral']['tracking_code'] ?? '');
$notMine = $probe->json('GET', 'referrals/item&id=' . $far['id'], [], $expertToken);
equals('QR ارجاعی که به او نیست، رد می‌شود', 'not_yours', $notMine['body']['error']['code'] ?? '');

$pdo->prepare('UPDATE field_items SET assigned_to = ? WHERE id = ?')->execute([$expertId, $report['id']]);
equals('عکس گزارش‌دهنده به کارشناس ارجاع می‌رسد', $photo['sha256'],
    hash('sha256', $probe->raw('referrals/file&id=' . $photo['id'], $expertToken)['bytes'] ?? ''));
$pdo->prepare('UPDATE field_items SET assigned_to = NULL WHERE id = ?')->execute([$report['id']]);
equals('بی‌ارجاع، عکس داده نمی‌شود', 404, $probe->raw('referrals/file&id=' . $photo['id'], $expertToken)['status']);

echo "\n— ارجاع: پذیرش و فرم نتیجه —\n";
$caseId = $newId();
equals('پذیرش ثبت شد', true, $probe->json('POST', 'referrals/accept',
    ['id' => $crypto['id'], 'report_id' => $caseId], $expertToken)['body']['data']['accepted'] ?? null);
equals('پذیرش دوباره همان پرونده بی‌اشکال است', 200, $probe->json('POST', 'referrals/accept',
    ['id' => $crypto['id'], 'report_id' => $caseId], $expertToken)['status']);
equals('پرونده دوم برای همان ارجاع رد می‌شود', 409, $probe->json('POST', 'referrals/accept',
    ['id' => $crypto['id'], 'report_id' => $newId()], $expertToken)['status']);

$send = static fn (string $outcome, string $note = '') => $probe->json('POST', 'referrals/result',
    ['id' => $illegalRef['id'], 'outcome' => $outcome, 'note' => $note], $expertToken);
equals('نتیجه ناشناخته رد می‌شود', 409, $send('maybe')['status']);
equals('«سایر» بی‌توضیح رد می‌شود', 409, $send('other')['status']);
equals('نتیجه ثبت شد', true, $send('confirmed', 'انشعاب غیرمجاز به چاه کشاورزی')['body']['data']['saved'] ?? null);
$row = $pdo->query("SELECT status, result FROM field_items WHERE id = '{$illegalRef['id']}'")->fetch(PDO::FETCH_ASSOC);
equals('ارجاع «نتیجه ثبت شد» شد', 3, (int) $row['status']);
equals('نتیجه با توضیح ذخیره شد', ['confirmed', 'انشعاب غیرمجاز به چاه کشاورزی'],
    [json_decode($row['result'], true)['outcome'] ?? null, json_decode($row['result'], true)['note'] ?? null]);
equals('همان نتیجه دوباره (پاسخ گم‌شده) پذیرفته است', 200, $send('confirmed', 'انشعاب غیرمجاز به چاه کشاورزی')['status']);
equals('نتیجه دیگر روی ارجاع بسته رد می‌شود', 409, $send('not_found')['status']);
check('مدیر اعلان نتیجه گرفت', (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE kind = 'field_result'")->fetchColumn() >= 1);

echo "\n— ارجاع: نتیجه خودکار از پرونده بازدید —\n";
$case = ['id' => $caseId, 'tracking_code' => $crypto['code'], 'report_type' => 4, 'status' => 0, 'expert_code' => '1042',
         'report_date' => 1760000000000, 'created_at' => 1760000000000, 'updated_at' => 1760000100000,
         'county' => 'ایلام', 'area_code' => '401', 'approval_state' => 0, 'description' => 'بازدید ارجاع'];
$probe->json('POST', 'sync/report', ['report' => $case, 'device_code' => 'X'], $expertToken);
equals('پرونده در دست اقدام هنوز نتیجه نیست', 2, (int) $pdo->query("SELECT status FROM field_items WHERE id = '{$crypto['id']}'")->fetchColumn());
$probe->json('POST', 'sync/report', ['report' => ['status' => 1, 'updated_at' => 1760000200000] + $case, 'device_code' => 'X'], $expertToken);
$row = $pdo->query("SELECT status, result FROM field_items WHERE id = '{$crypto['id']}'")->fetch(PDO::FETCH_ASSOC);
equals('بازدیدشده که رسید، ارجاع نتیجه گرفت', [3, 'case'], [(int) $row['status'], json_decode($row['result'], true)['outcome'] ?? null]);

$linked = ['id' => $newId(), 'tracking_code' => $byCode['code'], 'status' => 1, 'updated_at' => 1760000300000] + $case;
$probe->json('POST', 'sync/report', ['report' => $linked, 'device_code' => 'X'], $expertToken);
$row = $pdo->query("SELECT status, report_id FROM field_items WHERE id = '{$byCode['id']}'")->fetch(PDO::FETCH_ASSOC);
equals('بی‌پذیرش هم، با کد RZ- وصل و بسته شد', [3, $linked['id']], [(int) $row['status'], $row['report_id']]);

$jar = tempnam(sys_get_temp_dir(), 'admin-ref');
$probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$page = $probe->page('admin/item.php?id=' . $crypto['id'], $jar)['body'];
check('پنل مدیر نتیجه و پرونده را نشان می‌دهد', str_contains($page, 'بازدید ثبت شد') && str_contains($page, 'پذیرش کارشناس'));
$probe->page('admin/logout.php', $jar);
@unlink($jar);

$pdo->exec('ALTER TABLE field_items DROP INDEX idx_field_items_report, DROP COLUMN report_id, DROP COLUMN result');
$pdo->exec('UPDATE schema_meta SET version = 3 WHERE id = 1');
$probe->json('GET', 'ping');
equals('نصب نسخه ۳ خودش به آخرین نسخه رسید', Migrations::latest(), (int) $pdo->query('SELECT version FROM schema_meta WHERE id = 1')->fetchColumn());
