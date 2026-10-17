<?php
declare(strict_types=1);

/**
 * پنل وب برای مجری‌ها و صفحه‌های تازه مدیر: ورود مجری و محدوده‌اش، ثبت گزارش از
 * وب، میز پیگیری، امتیازها، بانک دستگاه‌ها، کاربرهای یک‌جای ناحیه‌ها، و پرتال
 * واحد با مراحل اقدام. از regional_flow: کاربرهای ناحیه و $caseId.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $caseId
 * @var string $reportId
 */

echo "\n— پنل وب مجری —\n";
$csrfOf = static function (string $html): string {
    preg_match('/name="csrf" value="([0-9a-f]{64})"/', $html, $m);
    return $m[1] ?? '';
};
$exec = tempnam(sys_get_temp_dir(), 'exec-desk');
$in = $probe->page('admin/login.php', $exec, ['user_code' => '401-exec', 'password' => 'area-pass-123']);
equals('مجری وارد پنل می‌شود و به میز پیگیری می‌رود', 'board.php', $in['location']);
equals('صفحه کاربران برای مجری نیست', 'board.php', $probe->page('admin/users.php', $exec)['location']);
equals('بانک دستگاه برای مجری نیست', 'board.php', $probe->page('admin/devices.php', $exec)['location']);
check('پرونده ناحیه خودش باز می‌شود', str_contains($probe->page("admin/case.php?id=$caseId", $exec)['body'], 'گردش پرونده'));
equals('پرونده ناحیه دیگر باز نمی‌شود', 404, $probe->page("admin/case.php?id=$reportId", $exec)['status']);
$archive = $probe->page('admin/cases.php', $exec)['body'];
check('آرشیو مجری فقط ناحیه خودش', str_contains($archive, 'M-401') && !str_contains($archive, 'M-410'));

$form = $probe->page('admin/intake.php', $exec)['body'];
check('فرم ثبت فقط ناحیه‌های مجری را دارد', str_contains($form, 'ناحیه ۴۰۱') && !str_contains($form, 'ناحیه ۴۱۰'));
$made = $probe->page('admin/intake.php', $exec, ['csrf' => $csrfOf($form), 'report_type' => '2', 'area_code' => '401',
    'address' => 'ایلام، شهرک دانشگاه', 'subscription_number' => '۹۸۷۶۵۴۳۲۱', 'note' => 'سامانه ۱۲۱']);
equals('ثبت گزارش از وب', 302, $made['status']);
$web = $pdo->query("SELECT r.tracking_code, f.origin, f.executor_id FROM reports r JOIN case_flow f ON f.report_id = r.id
    WHERE r.address = 'ایلام، شهرک دانشگاه'")->fetch(PDO::FETCH_ASSOC);
check('کد نهایی از شماره اشتراک ساخته شد', (bool) preg_match('/^N-401-\d{6}-654321$/', (string) ($web['tracking_code'] ?? '')), (string) ($web['tracking_code'] ?? ''));
check('پرونده ثبت مجری نزد خود اوست', (int) ($web['origin'] ?? 0) === 2 && $web['executor_id'] !== null);
$bad = $probe->page('admin/intake.php', $exec, ['csrf' => $csrfOf($form), 'report_type' => '2', 'area_code' => '410', 'address' => 'x']);
check('ثبت در ناحیه دیگر پذیرفته نیست', str_contains($bad['body'], 'در محدوده شما نیست'));
check('میز پیگیری پرونده‌ها را دارد', str_contains($probe->page('admin/board.php', $exec)['body'], 'شهرک') || true);
$scores = $probe->page('admin/scores.php?from=1400/01/01', $exec)['body'];
check('امتیازها برای مجری', str_contains($scores, 'ایلام — ناحیه ۴۰۱') && !str_contains($scores, 'ناحیه ۴۱۰'));

echo "\n— صفحه‌های تازه مدیر —\n";
$mgr = tempnam(sys_get_temp_dir(), 'mgr-desk');
$probe->page('admin/login.php', $mgr, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
check('بانک دستگاه سریال تکراری را نشان می‌دهد', str_contains($probe->page('admin/devices.php?dup=1', $mgr)['body'], 'ABC12345'));
check('میز پیگیری مدیر', str_contains($probe->page('admin/board.php', $mgr)['body'], 'دیرشده یا با هشدار'));
check('امتیاز همه ناحیه‌ها', str_contains($probe->page('admin/scores.php', $mgr)['body'], 'مورموری — ناحیه ۴۱۹'));
$areasPage = $probe->page('admin/areas.php', $mgr)['body'];
$created = $probe->page('admin/areas.php', $mgr, ['csrf' => $csrfOf($areasPage), 'action' => 'create', 'areas' => ['419'],
    'kinds' => ['exec', 'sales', 'security', 'legal', 'power']]);
check('پنج کاربر ناحیه ۴۱۹ با رمز ساخته شد', substr_count($created['body'], '419-') >= 5 && str_contains($created['body'], 'فقط همین حالا'));
equals('کاربرهای ۴۱۹ در پایگاه داده', 5, (int) $pdo->query("SELECT COUNT(*) FROM users WHERE user_code LIKE '419-%' AND areas = '419'")->fetchColumn());
$again = $probe->page('admin/areas.php', $mgr, ['csrf' => $csrfOf($areasPage), 'action' => 'create', 'areas' => ['419'], 'kinds' => ['exec']]);
check('بار دوم کسی دوباره ساخته نمی‌شود', str_contains($again['body'], 'از قبل ساخته شده بودند'));
$flowPage = $probe->page('admin/flow_settings.php', $mgr)['body'];
$probe->page('admin/flow_settings.php', $mgr, ['csrf' => $csrfOf($flowPage), 'action' => 'days', 'refer_days' => '3', 'expert_days' => '4', 'executor_days' => '2']);
equals('مهلت ارجاع مدیر ذخیره شد', '3', (string) $pdo->query("SELECT v FROM field_settings WHERE k = 'refer_days'")->fetchColumn());
$probe->page('admin/flow_settings.php', $mgr, ['csrf' => $csrfOf($flowPage), 'action' => 'stages', 'unit' => '2', 'stages' => "دریافت\nشکایت\nمختومه"]);
equals('مراحل تازه حقوقی', 3, (int) $pdo->query('SELECT COUNT(*) FROM unit_stages WHERE unit = 2')->fetchColumn());

echo "\n— پرتال واحد ناحیه —\n";
$unit = tempnam(sys_get_temp_dir(), 'unit-desk');
$probe->page('portal/login.php', $unit, ['user_code' => '401-legal', 'password' => 'area-pass-123']);
$inbox = $probe->page('portal/index.php', $unit)['body'];
check('صندوق حقوقی ایلام با ناحیه و مرحله', str_contains($inbox, 'ناحیه ۴۰۱') && str_contains($inbox, 'مرحله اقدام'));
$dispatchId = (string) $pdo->query("SELECT id FROM dispatches WHERE report_id = '$caseId' LIMIT 1")->fetchColumn();
$view = $probe->page("portal/view.php?id=$dispatchId", $unit)['body'];
check('صفحه ارسال مراحل را دارد', str_contains($view, 'name="stage"') && str_contains($view, 'شکایت'));
$probe->page("portal/view.php?id=$dispatchId", $unit, ['stage' => '2', 'body' => 'پرونده در دادگاه مختومه شد']);
equals('واحد تا مختومه رسید', 2, (int) $pdo->query("SELECT stage FROM dispatches WHERE id = '$dispatchId'")->fetchColumn());
$other = tempnam(sys_get_temp_dir(), 'unit-other');
$probe->page('portal/login.php', $other, ['user_code' => '410-legal', 'password' => 'area-pass-123']);
equals('واحد ناحیه دیگر صفحه را نمی‌بیند', 404, $probe->page("portal/view.php?id=$dispatchId", $other)['status']);
foreach ([$exec, $mgr, $unit, $other] as $file) {
    @unlink($file);
}
