<?php
declare(strict_types=1);

/**
 * پنل وب مثل اپ مدیر: صف تأیید و تصمیم از صفحه پرونده، ارسال به واحد، عملکرد
 * واحدها، درخواست ثبت دستگاه، و داشبورد پرونده‌ها.
 *
 * از تست‌های قبلی: $reportId و $mediaId (case_push)، $expertToken.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— پنل وب مثل اپ مدیر —\n";
$jar = tempnam(sys_get_temp_dir(), 'admin-desk');
$probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$csrfOf = static function (string $html): string {
    preg_match('/name="csrf" value="([0-9a-f]{64})"/', $html, $m);
    return $m[1] ?? '';
};
$code = 'M-401-050614-482917';

$probe->json('POST', 'approvals/submit', ['report_id' => $reportId, 'comment' => 'لطفاً بررسی شود'], $expertToken);
$queue = $probe->page('admin/approvals.php', $jar)['body'];
check('صف تأیید، پرونده فرستاده‌شده را دارد', str_contains($queue, $code) && str_contains($queue, 'لطفاً بررسی شود'));
$dashboard = $probe->page('admin/index.php', $jar)['body'];
check('داشبورد شمار انتظار تأیید را دارد', str_contains($dashboard, 'در انتظار تأیید شما') && str_contains($dashboard, 'approvals.php'));

$case = $probe->page("admin/case.php?id=$reportId", $jar)['body'];
check('صفحه پرونده فرم تصمیم دارد', str_contains($case, 'برگشت برای اصلاح') && str_contains($case, 'name="decision"'));
$token = $csrfOf($case);
$bad = $probe->page("admin/case.php?id=$reportId", $jar, ['csrf' => $token, 'action' => 'decide', 'decision' => '2', 'comment' => '']);
check('برگشت بی‌توضیح پذیرفته نیست', $bad['status'] === 200 && str_contains($bad['body'], 'چه چیزی باید اصلاح شود'));
$before = (int) $pdo->query("SELECT updated_at FROM reports WHERE id = '$reportId'")->fetchColumn();
$ok = $probe->page("admin/case.php?id=$reportId", $jar,
    ['csrf' => $token, 'action' => 'decide', 'decision' => '2', 'comment' => 'تصویر کنتور واضح نیست']);
equals('تصمیم ثبت و بازگشت به پرونده', 302, $ok['status']);
$row = $pdo->query("SELECT approval_state, approval_comment, updated_at FROM reports WHERE id = '$reportId'")->fetch(PDO::FETCH_ASSOC);
check('وضعیت تأیید پرونده «برگشت»', (int) $row['approval_state'] === 3 && $row['approval_comment'] === 'تصویر کنتور واضح نیست');
equals('updated_at گوشی دست نمی‌خورد', $before, (int) $row['updated_at']);
check('کارشناس اعلان برگشت گرفت', (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE kind = 'returned' AND report_id = '$reportId'
    AND body LIKE '%واضح نیست%'")->fetchColumn() === 1);
check('صف تأیید خالی شد', !str_contains($probe->page('admin/approvals.php', $jar)['body'], 'لطفاً بررسی شود</td>'));
check('سابقه تصمیم در پرونده', str_contains($probe->page("admin/case.php?id=$reportId", $jar)['body'], 'تصویر کنتور واضح نیست'));

$count = static fn (): int => (int) $pdo->query("SELECT COUNT(*) FROM dispatches WHERE report_id = '$reportId'")->fetchColumn();
$n = $count();
$sent = $probe->page("admin/case.php?id=$reportId", $jar, ['csrf' => $token, 'action' => 'dispatch', 'unit' => '1',
    'deadline_days' => '۵', 'note' => 'برای پیگیری حراست', 'format' => '0', 'items' => [$mediaId, 'not-ours']]);
equals('ارسال به واحد', 302, $sent['status']);
equals('یک سطر ارسال تازه', $n + 1, $count());
$d = $pdo->query("SELECT * FROM dispatches WHERE report_id = '$reportId' ORDER BY dispatched_at DESC LIMIT 1")->fetch(PDO::FETCH_ASSOC);
check('واحد، راه سامانه و مدارک همین پرونده', (int) $d['unit'] === 1 && (int) $d['channel'] === 0
    && json_decode($d['included_items'], true) === [$mediaId]);
check('مهلت پنج روز با رقم فارسی', abs((int) $d['deadline_at'] - (int) $d['dispatched_at'] - 5 * 86_400_000) < 1000);
check('واحد اعلان گرفت', (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE unit = 1 AND kind = 'dispatch'
    AND report_id = '$reportId'")->fetchColumn() >= 1);
$noUnit = $probe->page("admin/case.php?id=$reportId", $jar, ['csrf' => $token, 'action' => 'dispatch', 'unit' => '9']);
check('واحد نامعتبر رد می‌شود', str_contains($noUnit['body'], 'واحد مقصد را انتخاب کنید') && $count() === $n + 1);

$pdo->prepare('UPDATE dispatches SET deadline_at = ? WHERE id = ?')->execute([1_000_000, $d['id']]);
$perf = $probe->page('admin/performance.php', $jar)['body'];
check('عملکرد واحدها: واحد حراست و ارسال مهلت‌گذشته', str_contains($perf, 'واحد حراست') && str_contains($perf, 'ارسال‌های با مهلت گذشته')
    && str_contains($perf, $code));
check('فیلتر تاریخ عملکرد', str_contains($probe->page('admin/performance.php?from=' . urlencode('۱۴۲۰/۰۱/۰۱'), $jar)['body'],
    'در این بازه ارسالی'));
$units = $probe->json('GET', 'stats/units', [], $managerToken)['body']['data']['units'] ?? [];
check('API عملکرد همان آمار مشترک را می‌دهد', in_array(1, array_map('intval', array_column($units, 'unit')), true));

$requestId = '6f1c2b1e-0000-4000-8000-00000000d0d0';
$pdo->prepare('INSERT INTO device_requests (id, device_code, full_name, phone, county, status, created_at) VALUES (?, ?, ?, ?, ?, 0, ?)')
    ->execute([$requestId, 'WEBDEV42', 'کارشناس تازه', '09120000000', 'مهران', 1_760_000_000_000]);
$users = $probe->page('admin/users.php', $jar)['body'];
check('درخواست ثبت دستگاه در کاربران', str_contains($users, 'کارشناس تازه') && str_contains($users, 'WEBDEV42'));
$form = $probe->page("admin/user.php?request=$requestId", $jar)['body'];
check('ساخت کاربر از درخواست، فرم را پر می‌کند', str_contains($form, 'value="کارشناس تازه"') && str_contains($form, 'value="WEBDEV42"'));
$probe->page('admin/users.php', $jar, ['csrf' => $csrfOf($users), 'action' => 'reject_request', 'id' => $requestId]);
equals('رد درخواست', 2, (int) $pdo->query("SELECT status FROM device_requests WHERE id = '$requestId'")->fetchColumn());

$probe->page('admin/logout.php', $jar);
@unlink($jar);
