<?php
declare(strict_types=1);

/**
 * سوابق بازرسی و مقایسه: خوانش‌های هر تابلو و تیر، افزایش نامتعارف و هشدارش (فقط مدیر و
 * مجری رمزارز ناحیه)، سوابق گوشی بی نام، دامنه دید هر نقش، رد مورد، خروجی‌ها، ارجاع از
 * سوابق به کارشناس رمزارز، و حق ساختن PDF و Word.
 *
 * از تست‌های قبلی: $inspector (f-200)، $managerToken، $exec401، $exec410، $illegal، $newId.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— سوابق بازرسی و مقایسه —\n";
$pdo->exec("UPDATE users SET areas = '401' WHERE user_code = 'f-200'");
$crewH = ['lead' => ['name' => 'رهبر سوابق'], 'lineman' => ['name' => 'سیم‌بان سوابق']];
$dayAt = static fn (int $day): int => 1761000000000 + $day * 86_400_000;
$panelItem = static fn (int $day, array $main, string $plate) => ['id' => $newId(), 'kind' => 4, 'plate' => $plate,
    'created_at' => $dayAt($day), 'updated_at' => $dayAt($day), 'position_at' => $dayAt($day),
    'latitude' => 33.64, 'longitude' => 46.42, 'accuracy' => 5, 'address' => 'میدان آزمون سوابق',
    'payload' => ['v' => 2, 'crew' => $crewH, 'main' => $main,
        'feeders' => ['A' => ['state' => 'measured', 'phases' => ['r' => 40, 's' => 41, 't' => 39]]]]];
$days = [
    $panelItem(1, ['r' => 100, 's' => 100, 't' => 100], '۷۷-۱۰۰'),
    $panelItem(2, ['r' => 105, 's' => 99, 't' => 98], '77100'),
    $panelItem(3, ['r' => 160, 's' => 101, 't' => 100], '77 100'),
];
foreach ($days as $item) {
    $sent = $probe->json('POST', 'field/item', ['item' => $item, 'files' => []], $inspector);
    equals('بازرسی تابلو پذیرفته شد', 200, $sent['status'], $sent['raw']);
}
equals('پلاک یکسان‌شده: سه خوانش کلید کل یک تابلو', 3,
    (int) $pdo->query("SELECT COUNT(*) FROM field_readings WHERE plate_key = '77100' AND part = 'main' AND area_code = '401'")->fetchColumn());
equals('فیدر A هم خوانش جدا دارد', 3, (int) $pdo->query("SELECT COUNT(*) FROM field_readings WHERE plate_key = '77100' AND part = 'A'")->fetchColumn());
$flag = static fn (string $id): mixed => $pdo->query("SELECT anomaly_at FROM field_items WHERE id = '$id'")->fetchColumn() ?: null;
check('افزایش ۶۰ آمپری فاز R نامتعارف است', $flag($days[2]['id']) !== null);
check('افزایش پنج آمپری نامتعارف نیست', $flag($days[1]['id']) === null);
$note = (string) $pdo->query("SELECT anomaly_note FROM field_readings WHERE item_id = '{$days[2]['id']}' AND part = 'main'")->fetchColumn();
check('یادداشت تغییر فاز R', str_starts_with($note, 'R: '), $note);
$alerts = $pdo->query("SELECT role, area_code FROM notifications WHERE kind = 'field_anomaly' ORDER BY role")->fetchAll(PDO::FETCH_ASSOC);
equals('هشدار فقط به مدیر و مجری رمزارز ناحیه ۴۰۱', [[1, null], [4, '401']],
    array_map(static fn ($a) => [(int) $a['role'], $a['area_code']], $alerts));

$phone = $probe->json('GET', 'field/history&plate=' . urlencode('77-100') . '&asset=1&part=main', [], $inspector)['body']['data'] ?? [];
equals('گوشی: سه سابقه، تازه‌ترین اول', [160.0, 105.0, 100.0], array_map('floatval', array_column($phone['rows'] ?? [], 'r')));
check('گوشی نام ثبت‌کننده را نمی‌گیرد', !array_key_exists('who', $phone['rows'][0] ?? []));
check('گوشی حد افزایش را می‌گیرد', ($phone['settings']['history_rise_pct'] ?? null) === 30);
$bundle = $probe->json('GET', 'field/history/bundle&since=0', [], $inspector)['body']['data'] ?? [];
$mainKey = array_values(array_filter($bundle['keys'] ?? [], static fn ($k) => $k['plate_key'] === '77100' && $k['part'] === 'main'))[0] ?? [];
equals('بسته بی‌اینترنت: همان سه خوانش', 3, count($mainKey['rows'] ?? []));

$search = static fn (string $token, string $q = '') => $probe->json('GET', 'manage/history&plate=77100' . $q, [], $token)['body']['data'] ?? [];
$mgr = $search($managerToken);
$main = array_values(array_filter($mgr['keys'] ?? [], static fn ($k) => $k['part'] === 'main'))[0] ?? [];
equals('مدیر: سه خوانش و یک نامتعارف', [3, 1], [$main['count'] ?? null, $main['anomalies'] ?? null]);
check('مدیر خروجی می‌گیرد', ($mgr['can_export'] ?? false) === true);
$exec = $search($exec401);
check('مجری رمزارز ۴۰۱ سوابق ناحیه‌اش را می‌بیند، بی خروجی از اپ', count($exec['keys'] ?? []) === 2 && ($exec['can_export'] ?? true) === false);
equals('مجری ناحیه دیگر سوابق ۴۰۱ را نمی‌بیند', [], $search($exec410)['keys'] ?? null);
check('مجری ستاد غیرمجاز سوابق را می‌بیند', count($search($illegal)['keys'] ?? []) === 2);
equals('خروجی اپ برای مجری رمزارز نیست', 403, $probe->json('GET', 'manage/history/export&format=xlsx', [], $exec401)['status']);
check('خروجی اکسل اپ برای مدیر', str_starts_with($probe->json('GET', 'manage/history/export&format=xlsx&plate=77100', [], $managerToken)['raw'], 'PK'));
$groups = $probe->json('GET', 'manage/history/item&id=' . $days[2]['id'], [], $illegal)['body']['data']['groups'] ?? [];
check('صفحه مورد برای ستاد: سوابق کلید کل و فیدر A', count($groups) === 2 && count($groups[0]['rows'] ?? []) === 3
    && ($groups[0]['rows'][0]['who'] ?? null) !== null);
$hqItem = $probe->json('GET', 'manage/field/item&id=' . $days[2]['id'], [], $illegal)['body']['data']['item'] ?? [];
check('ستاد بازرسی را می‌بیند ولی کاری رویش ندارد', ($hqItem['next'] ?? null) === []);
equals('ستاد تغییر وضعیت بازرسی ندارد', 409, $probe->json('POST', 'manage/field/status',
    ['id' => $days[2]['id'], 'status' => 1, 'note' => ''], $illegal)['status']);

$reject = $probe->json('POST', 'manage/field/status', ['id' => $days[1]['id'], 'status' => 5, 'note' => 'خوانش اشتباه'], $managerToken);
equals('مدیر بازرسی روز دوم را رد کرد', 200, $reject['status'], $reject['raw']);
$phone = $probe->json('GET', 'field/history&plate=77100&asset=1&part=main', [], $inspector)['body']['data']['rows'] ?? [];
equals('خوانش مورد ردشده در سوابق نمی‌آید', [160.0, 100.0], array_map('floatval', array_column($phone, 'r')));

$expert2001 = (string) $pdo->query("SELECT id FROM users WHERE user_code = '2001'")->fetchColumn();
$refer = ['area' => '401', 'asset' => 1, 'plate_key' => '77100', 'part' => 'main', 'expert_id' => $expert2001, 'note' => 'بررسی شود'];
equals('ستاد غیرمجاز از سوابق ارجاع رمزارز نمی‌دهد', 403, $probe->json('POST', 'manage/history/refer', $refer, $illegal)['status']);
$referred = $probe->json('POST', 'manage/history/refer', $refer, $exec401);
$reportId = (string) ($referred['body']['data']['report_id'] ?? '');
check('مجری رمزارز از سوابق به کارشناس ارجاع داد', $reportId !== '', $referred['raw']);
$case = $pdo->query("SELECT case_kind, report_type, expert_code, area_code, address FROM reports WHERE id = '$reportId'")->fetch(PDO::FETCH_ASSOC);
equals('پرونده رمزارز «گزارش همکار» ناحیه ۴۰۱ نزد کارشناس', [0, 4, '2001', '401', 'میدان آزمون سوابق'],
    [(int) ($case['case_kind'] ?? -1), (int) ($case['report_type'] ?? 0), $case['expert_code'] ?? null, $case['area_code'] ?? null, $case['address'] ?? null]);

echo "\n— سوابق در پنل و حق ساختن PDF و Word —\n";
$web = tempnam(sys_get_temp_dir(), 'history-web');
$probe->page('admin/login.php', $web, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$page = $probe->page('admin/history.php?plate=77100', $web)['body'];
check('صفحه سوابق پنل', str_contains($page, 'تابلو ۷۷۱۰۰ — کلید کل') || str_contains($page, 'کلید کل'), mb_substr($page, 0, 300));
$series = $probe->page('admin/history.php?k_area=401&k_asset=1&k_plate=77100&part=main', $web)['body'];
check('رشته کلید کل با نمودار و درصد تغییر', str_contains($series, '<polyline') && str_contains($series, '٪)'));
check('اکسل پنل', str_starts_with($probe->page('admin/history_export.php?format=xlsx&plate=77100', $web)['body'], 'PK'));
check('Word پنل', str_starts_with($probe->page('admin/history_export.php?format=docx&plate=77100', $web)['body'], 'PK'));
$itemPage = $probe->page('admin/item.php?id=' . $days[2]['id'], $web)['body'];
check('صفحه مورد پنل: سوابق و مقایسه', str_contains($itemPage, 'سوابق و مقایسه'));

$execWeb = tempnam(sys_get_temp_dir(), 'history-exec');
$probe->page('admin/login.php', $execWeb, ['user_code' => '401-exec', 'password' => 'area-pass-123']);
check('مجری رمزارز در پنل سوابق را می‌بیند', str_contains($probe->page('admin/history.php?plate=77100', $execWeb)['body'], 'کلید کل'));
equals('فرم رسمی PDF برای مجری رمزارز نیست', 'board.php', $probe->page("admin/case_print.php?id=$reportId", $execWeb)['location']);
equals('Word پرونده برای مجری رمزارز نیست', 'board.php', $probe->page("admin/case_docx.php?id=$reportId", $execWeb)['location']);
check('دکمه PDF در صفحه پرونده مجری نیست', !str_contains($probe->page("admin/case.php?id=$reportId", $execWeb)['body'], 'case_print.php'));
$social = $probe->json('POST', 'dispatches/create', ['report_id' => $reportId, 'unit' => 2, 'channel' => 1], $exec401);
equals('مجری رمزارز در شبکه اجتماعی نمی‌فرستد', 403, $social['status'], $social['raw']);
$system = $probe->json('POST', 'dispatches/create', ['report_id' => $reportId, 'unit' => 2, 'channel' => 0], $exec401);
equals('مجری رمزارز از راه سامانه به واحد می‌فرستد', 200, $system['status'], $system['raw']);
equals('مدیر در شبکه اجتماعی می‌فرستد', 200, $probe->json('POST', 'dispatches/create',
    ['report_id' => $reportId, 'unit' => 1, 'channel' => 1], $managerToken)['status']);
