<?php
declare(strict_types=1);

/**
 * آمار جامع مراکز استخراج: سطر خودکار پرونده تأییدشده رمزارز (نه غیرمجاز، نه تأییدنشده)،
 * مقدارهای سامانه، دسترسی مدیر و مجری و واحد، دستورالعمل‌های پاداش با تاریخ اجرا و سقف و
 * پلکان، پاداش عوامل اجرایی و اداری، شبا، ورود فایل با جور کردن و ناسازگاری، قفل سال،
 * خروجی‌ها و صفحه‌های پنل و پرتال.
 *
 * از تست‌های قبلی: $managerToken، $exec401، $exec410، $legal401.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

require_once __DIR__ . '/../../api/lib/Xlsx.php';

echo "\n— آمار جامع مراکز —\n";
$at = 1748750400000; // ۱۴۰۴/۰۳/۱۱
$report = static function (string $id, int $kind, int $approval, array $extra = []) use ($pdo, $at): void {
    $row = $extra + ['id' => $id, 'report_type' => 3, 'status' => 1, 'report_date' => $at, 'created_at' => $at, 'updated_at' => $at,
        'county' => 'ایلام', 'district' => 'مرکزی', 'area_code' => '401', 'approval_state' => $approval, 'case_kind' => $kind,
        'tracking_code' => 'M-401-040311-' . substr($id, -6), 'expert_code' => '2001'];
    $cols = array_keys($row);
    $pdo->prepare('INSERT INTO reports (' . implode(',', $cols) . ') VALUES (' . implode(',', array_fill(0, count($cols), '?')) . ')')
        ->execute(array_values($row));
};
$cRep = 'c0000000-0000-4000-8000-000000000001';
$report($cRep, 0, 2, ['subscription_number' => '5649001', 'bill_number' => '7768845810320', 'subscriber_name' => 'مشترک آزمون',
    'total_watt' => 12500, 'tariff_type' => 1, 'meter_type' => 1, 'file_number' => '757001']);
$report('c0000000-0000-4000-8000-000000000002', 1, 2, ['subscription_number' => '999001']);
$report('c0000000-0000-4000-8000-000000000003', 0, 1, ['subscription_number' => '999002']);
foreach ([1, 2, 3] as $n) {
    $pdo->exec("INSERT INTO devices (id, report_id, `row_number`, power_watt, entry_method) VALUES ('c1000000-0000-4000-8000-00000000000$n', '$cRep', $n, 3000, 0)");
}
$pdo->exec("INSERT INTO attendees (id, report_id, organization, full_name) VALUES ('c2000000-0000-4000-8000-000000000001', '$cRep', 1, 'سرگرد آزمون')");
$pdo->exec("INSERT INTO attendees (id, report_id, organization, full_name, org_name) VALUES ('c2000000-0000-4000-8000-000000000002', '$cRep', 2, 'سروان آزمون', 'اطلاعات سپاه')");
$pdo->exec("INSERT INTO attendees (id, report_id, organization, full_name, position) VALUES ('c2000000-0000-4000-8000-000000000003', '$cRep', 0, 'مأمور آزمون', 'مأمور وصول')");
$pdo->exec("INSERT INTO attendees (id, report_id, organization, full_name, position) VALUES ('c2000000-0000-4000-8000-000000000004', '$cRep', 0, 'کارشناس برق', 'کارشناس')");
$pdo->exec("INSERT INTO attachments (id, report_id, category, file_path, added_at) VALUES ('c3000000-0000-4000-8000-000000000001', '$cRep', 2, 'x.pdf', $at)");

$list = static fn (string $token, string $q = '') => $probe->json('GET', 'centers&year=1404' . $q, [], $token);
$mgr = $list($managerToken)['body']['data'] ?? [];
equals('فقط پرونده تأییدشده رمزارز سطر گرفت', 1, count($mgr['rows'] ?? []));
$v = $mgr['rows'][0]['values'] ?? [];
equals('مقدارهای سامانه', [3, 9.0, 1, 1, 'صنعتی', 'دارای انشعاب', 12.5, 'دیجیتال', 'ایلام / مرکزی'],
    [$v['miners'] ?? null, (float) ($v['capacity_kw'] ?? 0), $v['commission'] ?? null, $v['with_found'] ?? null, $v['branch_type'] ?? null,
        $v['adjust_branch'] ?? null, (float) ($v['with_kw'] ?? 0), $v['meter_type'] ?? null, $v['county'] ?? null]);
$rowId = (string) ($mgr['rows'][0]['id'] ?? '');
equals('بار دوم سطر دوم نمی‌سازد', 1, count($list($managerToken)['body']['data']['rows'] ?? []));

equals('مجری بی اجازه مدیر دسترسی ندارد', 403, $list($exec401)['status']);
equals('واحد بی تیک مدیر دسترسی ندارد', 403, $list($legal401)['status']);
$set = $probe->json('POST', 'centers/settings', ['executor' => 1, 'units' => ['2' => ['status', 'collected_amount']],
    'excel' => [], 'word' => ['row', 'subscription', 'miners', 'status']], $managerToken);
equals('مدیر دسترسی‌ها را ذخیره کرد', 200, $set['status'], $set['raw']);
equals('مجری ۴۰۱ سطر ناحیه‌اش را می‌بیند', 1, count($list($exec401)['body']['data']['rows'] ?? []));
equals('مجری ۴۱۰ سطر ۴۰۱ را نمی‌بیند', 0, count($list($exec410)['body']['data']['rows'] ?? []));
equals('مجری تنظیمات را عوض نمی‌کند', 403, $probe->json('POST', 'centers/settings', ['executor' => 0], $exec401)['status']);
$unitRows = $list($legal401)['body']['data']['rows'] ?? [];
check('واحد فقط ستون‌های شناسایی و ستون‌های خودش را می‌بیند', count($unitRows) === 1 && !isset($unitRows[0]['values']['miners'])
    && array_key_exists('subscription', $unitRows[0]['values']));
$unitSave = $probe->json('POST', 'centers/row', ['id' => $rowId, 'values' => ['status' => 'دادگاه', 'energy_amount' => 5]], $legal401);
equals('واحد ستون خودش را نوشت', 'دادگاه', $unitSave['body']['data']['values']['status'] ?? null);
$full = $probe->json('GET', 'centers/row&id=' . $rowId, [], $managerToken)['body']['data'] ?? [];
check('ستون بسته واحد نوشته نشد', ($full['values']['energy_amount'] ?? null) === null);

$rule = static fn (array $body) => $probe->json('POST', 'centers/rules', $body, $managerToken);
$old = $rule(['kind' => 0, 'letter_no' => '۱۲۳/ص', 'effective_from' => '1400/01/01', 'spec' => ['mode' => 'progressive', 'cap' => '5000000',
    'tiers' => [['from' => 1, 'to' => 2, 'amount' => 1000000], ['from' => 3, 'to' => '', 'amount' => 2000000]]]]);
equals('دستورالعمل شناسایی ثبت شد', 200, $old['status'], $old['raw']);
$rule(['kind' => 0, 'effective_from' => '1405/01/01', 'spec' => ['tiers' => [['from' => 1, 'amount' => 99000000]]]]);
equals('پله‌های هم‌پوشان پذیرفته نیست', 400, $rule(['kind' => 0, 'effective_from' => '1404/01/01',
    'spec' => ['tiers' => [['from' => 1, 'to' => 5, 'amount' => 1], ['from' => 3, 'amount' => 2]]]])['status']);
$rule(['kind' => 1, 'effective_from' => '1400/01/01', 'spec' => ['mode' => 'bracket', 'orgs' => ['faraja', 'sepah', 'collection'],
    'split' => 'person', 'tiers' => [['from' => 1, 'amount' => 300000]]]]);
equals('وزن نقش‌ها باید ۱۰۰ باشد', 400, $rule(['kind' => 2, 'effective_from' => '1400/01/01',
    'spec' => ['pct' => 1.5, 'ceo_pct' => 0.5, 'staff_pct' => 1, 'roles' => [['label' => 'مجری رمزارز', 'weight' => 30, 'people' => 'الف']]]])['status']);
$rule(['kind' => 2, 'effective_from' => '1400/01/01', 'spec' => ['pct' => 1.5, 'ceo_pct' => 0.5, 'staff_pct' => 1, 'base' => 'energy',
    'roles' => [['label' => 'مجری رمزارز', 'weight' => 30, 'people' => 'الف'], ['label' => 'دبیر کمیسیون', 'weight' => 70, 'people' => "ب\nج"]]]]);

$save = $probe->json('POST', 'centers/row', ['id' => $rowId, 'values' => ['energy_amount' => '۱۰۰٬۰۰۰٬۰۰۰', 'sheba' => 'IR062960000000100324200001']], $managerToken);
$d = $save['body']['data'] ?? [];
equals('پاداش شناسایی پلکانی با دستورالعمل جاری در تاریخ گزارش', 4000000, (int) ($d['values']['finder_reward'] ?? 0));
equals('پاداش اجرایی میان سه عامل اجرایی حاضر', [900000, 3, 300000], [(int) ($d['values']['police_reward'] ?? 0),
    count($d['police']['people'] ?? []), (int) ($d['police']['people'][0]['share'] ?? 0)]);
equals('عوامل اداری: کل، مدیرعامل، کارکنان، نقش مجری', [1500000, 500000, 1000000, 300000],
    [(int) ($d['staff']['amount'] ?? 0), (int) ($d['staff']['ceo'] ?? 0), (int) ($d['staff']['staff'] ?? 0), (int) ($d['staff']['roles'][0]['amount'] ?? 0)]);
equals('شبای نادرست پذیرفته نیست', 400, $probe->json('POST', 'centers/row', ['id' => $rowId, 'values' => ['sheba' => 'IR062960000000100324200002']], $managerToken)['status']);
check('تاریخچه تغییرات با نام کاربر', count(array_filter($d['changes'] ?? [], static fn ($c) => $c['field'] === 'energy_amount' && $c['who'] !== null)) === 1);

// فایل اکسل با سرستون دیگر و ترتیب دیگر: یک سطر جور با تعداد ماینر متفاوت، یک سطر تازه.
$file = sys_get_temp_dir() . '/center-import-' . getmypid() . '.xlsx';
Xlsx::write($file, '1404', ['نام مشترک متخلف', 'شماره اشتراک', 'تاریخ گزارش', 'تعداد ماینر کشف شده (دستگاه)', 'مبلغ خسارت (ریال)', 'شناسه قبض مشترک متخلف'], [
    ['مشترک آزمون', '5649001', '1404/03/11', 5, 2000000, null],
    ['مشترک تازه', '7001', '1404/05/01', 12, null, '88001'],
    ['جمع', null, null, 17, null, null],
]);
$up = $probe->upload('centers/import&year=1404', 'file', $file, $managerToken);
$plan = $up['body']['data'] ?? [];
equals('پیش‌نمایش: یک جور، یک تازه، یک ناسازگار', [1, 1, 1], [$plan['summary']['match'] ?? null, $plan['summary']['new'] ?? null,
    $plan['summary']['conflicts'] ?? null], $up['raw']);
$asBytes = $probe->bytes('centers/import&year=1404', (string) file_get_contents($file), $managerToken)['body']['data']['summary'] ?? [];
equals('اپ فایل را با بایت‌های خام می‌فرستد', [1, 1], [$asBytes['match'] ?? null, $asBytes['new'] ?? null]);
equals('مجری فایل وارد نمی‌کند', 403, $probe->upload('centers/import&year=1404', 'file', $file, $exec401)['status']);
$applied = $probe->json('POST', 'centers/import/apply', ['token' => $plan['token'] ?? ''], $managerToken);
equals('ورود: یک به‌روز و یک تازه', [1, 1], [$applied['body']['data']['updated'] ?? null, $applied['body']['data']['created'] ?? null], $applied['raw']);
equals('نشانه پس از اجرا کار نمی‌کند', 404, $probe->json('POST', 'centers/import/apply', ['token' => $plan['token'] ?? ''], $managerToken)['status']);
$after = $probe->json('GET', 'centers/row&id=' . $rowId, [], $managerToken)['body']['data'] ?? [];
equals('ناسازگار: ماینر سامانه ۳ و فایل ۵', [['miners'], 3, 5], [$after['conflicts'] ?? null, $after['values']['miners'] ?? null, $after['file']['miners'] ?? null]);
equals('مقدار فایلی که سامانه ندارد می‌نشیند', 2000000, $after['values']['damage_amount'] ?? null);
$chosen = $probe->json('POST', 'centers/row', ['id' => $rowId, 'choices' => ['miners' => 'file']], $managerToken)['body']['data'] ?? [];
equals('انتخاب فایل: ۵ ماینر و پاداش با همان، تا سقف', [5, [], 5000000], [$chosen['values']['miners'] ?? null,
    $chosen['conflicts'] ?? null, (int) ($chosen['values']['finder_reward'] ?? 0)]);
$third = $probe->json('POST', 'centers/row', ['id' => $rowId, 'values' => ['miners' => 4]], $managerToken)['body']['data'] ?? [];
equals('مقدار سوم مدیر', [4, 'own'], [$third['values']['miners'] ?? null, $third['from']['miners'] ?? null]);
equals('دو سطر در سال', 2, count($list($managerToken)['body']['data']['rows'] ?? []));

$lock = $probe->json('POST', 'centers/settings', ['year' => 1404, 'lock' => 1], $managerToken);
equals('قفل سال', [true], array_column(array_filter($lock['body']['data']['years'] ?? [], static fn ($y) => $y['year'] === 1404), 'locked'), $lock['raw']);
equals('سطر سال قفل ویرایش نمی‌شود', 400, $probe->json('POST', 'centers/row', ['id' => $rowId, 'values' => ['status' => 'وصل']], $managerToken)['status']);
$probe->json('POST', 'centers/settings', ['year' => 1404, 'lock' => 0], $managerToken);
equals('پس از باز کردن ویرایش می‌شود', 200, $probe->json('POST', 'centers/row', ['id' => $rowId, 'values' => ['status' => 'وصل']], $managerToken)['status']);

$xlsx = $probe->raw('centers/export&year=1404&format=xlsx', $managerToken)['bytes'];
check('اکسل با سبک‌های فرم توانیر', str_starts_with($xlsx, 'PK') && str_contains($xlsx, 'styles.xml'));
$tmp = sys_get_temp_dir() . '/center-export-' . getmypid() . '.xlsx';
file_put_contents($tmp, $xlsx);
$zip = new ZipArchive();
$zip->open($tmp);
$sheet = (string) $zip->getFromName('xl/worksheets/sheet1.xml');
check('برگه راست‌به‌چپ با سرستون ادغام‌شده و سطر جمع', str_contains($sheet, 'rightToLeft="1"') && str_contains($sheet, 'E1:M1')
    && str_contains($sheet, 'جمع کل'));
check('برگه دوم جدول اطلاعات تکمیلی', str_contains((string) $zip->getFromName('xl/workbook.xml'), 'جدول اطلاعات تکمیلی'));
check('فونت B Titr فرم', str_contains((string) $zip->getFromName('xl/styles.xml'), 'B Titr'));
$zip->close();
$reimport = $probe->upload('centers/import&year=1404', 'file', $tmp, $managerToken)['body']['data']['summary'] ?? [];
equals('خروجی خود سامانه دوباره خوانده می‌شود و با همان دو سطر جور است', [2, 0], [$reimport['match'] ?? null, $reimport['new'] ?? null]);
check('Word', str_starts_with($probe->raw('centers/export&year=1404&format=docx', $exec401)['bytes'], 'PK'));
$staff = $probe->json('GET', 'centers/rewards&year=1404', [], $managerToken)['body']['data'] ?? [];
equals('گزارش پاداش عوامل اداری: جمع هر نفر', [1500000, 3], [(int) ($staff['totals']['amount'] ?? 0), count($staff['people'] ?? [])]);
check('اکسل گزارش پاداش اجرایی', str_starts_with($probe->raw('centers/rewards&year=1404&kind=agents&format=xlsx', $managerToken)['bytes'], 'PK'));
equals('مجری گزارش پاداش ندارد', 403, $probe->json('GET', 'centers/rewards&year=1404', [], $exec401)['status']);
$charts = $list($managerToken, '&charts=1')['body']['data']['charts'] ?? [];
equals('نمودار: دو مرکز در دو ماه', [2, 2], [$charts['totals']['centers'] ?? null, count($charts['months'] ?? [])]);
@unlink($file);
@unlink($tmp);

$jar = tempnam(sys_get_temp_dir(), 'cjar');
$probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$page = $probe->page('admin/centers.php?year=1404', $jar);
check('صفحه آمار جامع پنل', $page['status'] === 200 && str_contains($page['body'], 'مشترک تازه') && str_contains($page['body'], '<svg'));
$rowPage = $probe->page('admin/center_row.php?id=' . $rowId, $jar);
preg_match('/name="_c" value="([a-f0-9]+)"/', $rowPage['body'], $csrf);
$posted = $probe->page('admin/center_row.php?id=' . $rowId, $jar, ['_c' => $csrf[1] ?? '', 'action' => 'save',
    'v' => ['account_reason' => 'علی‌الحساب', 'status' => 'وصل']]);
check('ویرایش سطر از پنل', str_contains($posted['body'], 'ذخیره شد'), substr(strip_tags($posted['body']), 0, 300));
equals('پنل فقط خانه تغییرکرده را نوشت', ['account_reason'], array_values(array_diff(array_keys(
    $probe->json('GET', 'centers/row&id=' . $rowId, [], $managerToken)['body']['data']['own'] ?? []), ['energy_amount', 'sheba', 'miners', 'status'])));
foreach (['center_import.php', 'center_rules.php', 'center_settings.php', 'center_rewards.php?year=1404', 'index.php'] as $p) {
    equals("صفحه $p", 200, $probe->page('admin/' . $p, $jar)['status']);
}
$unitJar = tempnam(sys_get_temp_dir(), 'ujar');
$probe->page('portal/login.php', $unitJar, ['user_code' => '401-legal', 'password' => 'area-pass-123']);
$portal = $probe->page('portal/centers.php?year=1404', $unitJar);
check('پرتال واحد: آمار با ستون‌های خودش', $portal['status'] === 200 && str_contains($portal['body'], 'مبلغ وصول'));
@unlink($jar);
@unlink($unitJar);
