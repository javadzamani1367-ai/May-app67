<?php
declare(strict_types=1);

/**
 * عملکرد اکیپ در پنل: سه کار یک روز (تابلو، تیربه‌تیر از همان تابلو، بازرسی رمزارز)
 * با اکیپ، صفحه عملکرد، ثبت اضافه‌کار تأییدشده، اکسل و Word.
 *
 * از تست‌های قبلی: $inspector (field_cycle)، $newId.
 *
 * @var HttpProbe $probe
 */

echo "\n— عملکرد اکیپ —\n";
$at = static fn (string $hm): int => (new DateTimeImmutable('2026-10-18 ' . $hm, new DateTimeZone('Asia/Tehran')))->getTimestamp() * 1000;
$crew = ['lead' => ['name' => 'رهبر آزمون'], 'lineman' => ['name' => 'سیم‌بان آزمون']];
$ph = ['r' => 30, 's' => 31, 't' => 32];
$base = ['created_at' => $at('08:50'), 'updated_at' => $at('08:50'), 'latitude' => 33.6379, 'longitude' => 46.4203, 'accuracy' => 5];
$panel = ['id' => $newId(), 'kind' => 4, 'plate' => 'P-900', 'position_at' => $at('09:00'),
          'payload' => ['v' => 2, 'crew' => $crew, 'main' => $ph, 'feeders' => []]] + $base;
$line = ['id' => $newId(), 'kind' => 6, 'plate' => 'P-900', 'position_at' => $at('09:20'),
         'payload' => ['v' => 2, 'crew' => $crew, 'feeder' => 'A', 'start' => $ph, 'poles' => [
             ['lat' => 33.639, 'lon' => 46.421, 'at' => $at('09:30'), 'state' => 'measured', 'phases' => $ph],
             ['lat' => 33.640, 'lon' => 46.422, 'at' => $at('09:45'), 'state' => 'measured', 'phases' => $ph],
         ]]] + $base;
$crypto = ['id' => $newId(), 'kind' => 1, 'position_at' => $at('16:30'), 'description' => 'صدای فن ماینر',
           'payload' => ['v' => 2, 'crew' => $crew + ['crypto' => ['name' => 'مجری آزمون']], 'signs' => [], 'team' => []]] + $base;
foreach (['تابلو' => $panel, 'تیربه‌تیر' => $line, 'رمزارز' => $crypto] as $label => $item) {
    $sent = $probe->json('POST', 'field/item', ['item' => $item, 'files' => []], $inspector);
    equals("$label با اکیپ پذیرفته می‌شود", 200, $sent['status'], $sent['raw']);
}
$noCrew = ['id' => $newId(), 'payload' => ['v' => 2, 'signs' => [], 'team' => []]] + $crypto;
equals('رمزارز نسخه ۲ بی‌اکیپ رد می‌شود', 422, $probe->json('POST', 'field/item', ['item' => $noCrew, 'files' => []], $inspector)['status']);

$jar = tempnam(sys_get_temp_dir(), 'admin-crew');
$probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$range = 'from=1405/07/26&to=1405/07/26';
$page = $probe->page('admin/crew.php?' . $range, $jar)['body'];
check('صفحه عملکرد اکیپ نام‌ها را دارد', str_contains($page, 'رهبر آزمون') && str_contains($page, 'مجری آزمون'));
check('دو بازدید و پنج بازرسی', str_contains($page, '<b>۲</b><span>بازدید</span>') && str_contains($page, '<b>۵</b><span>بازرسی</span>'), $page);
check('نقشه بازدیدها کشیده شد', str_contains($page, '<svg') && str_contains($page, 'P-900'));
preg_match('/name="csrf" value="([^"]+)"/', $page, $token);
$probe->page('admin/crew.php?' . $range, $jar, ['csrf' => $token[1] ?? '', 'member_key' => 'name:رهبر آزمون', 'hours' => '۲٫۵', 'note' => 'تأیید شد']);
$page = $probe->page('admin/crew.php?' . $range . '&member=' . urlencode('name:رهبر آزمون'), $jar)['body'];
check('اضافه‌کار تأییدشده ثبت شد', str_contains($page, 'value="۲.۵"') || str_contains($page, 'value="۲٫۵"'), $page);
check('کار روزانه یک نفر', str_contains($page, 'کار روزانه رهبر آزمون') && str_contains($page, '۰۹:۰۰'));
$xlsx = $probe->page('admin/crew_export.php?' . $range, $jar);
check('اکسل عملکرد اکیپ', $xlsx['status'] === 200 && str_starts_with($xlsx['body'], 'PK'));
$docx = $probe->page('admin/crew_docx.php?' . $range, $jar);
check('گزارش Word عملکرد اکیپ', $docx['status'] === 200 && str_starts_with($docx['body'], 'PK'));
$probe->page('admin/logout.php', $jar);
@unlink($jar);

// اپ مدیر: همان گزارش، اضافه‌کار و فایل‌ها.
$range = '&from=' . $at('00:00') . '&to=' . ($at('23:59') + 59_999);
equals('کارشناس گزارش اکیپ را نمی‌بیند', 403, $probe->json('GET', 'manage/crew/report' . $range, [], $expertToken)['status']);
$api = $probe->json('GET', 'manage/crew/report' . $range, [], $managerToken)['body']['data'] ?? [];
equals('اپ مدیر: سه نفر و دو بازدید', [3, 2], [count($api['members'] ?? []), count($api['visits'] ?? [])]);
$leadRow = array_values(array_filter($api['members'] ?? [], static fn ($m) => $m['name'] === 'رهبر آزمون'))[0] ?? [];
equals('اپ مدیر: اضافه‌کار تأییدشده پنل دیده می‌شود', 2.5, $leadRow['approved']['hours'] ?? null);
equals('اپ مدیر: پنج بازرسی', 5, $leadRow['total']['inspections'] ?? null);
$saved = $probe->json('POST', 'manage/crew/overtime', ['member_key' => 'name:سیم‌بان آزمون', 'hours' => '۳',
    'from' => $at('00:00'), 'to' => $at('23:59') + 59_999], $managerToken);
equals('اپ مدیر اضافه‌کار ثبت می‌کند', 200, $saved['status'], $saved['raw']);
equals('عدد نادرست اضافه‌کار رد می‌شود', 422, $probe->json('POST', 'manage/crew/overtime', ['member_key' => 'x', 'hours' => 'abc',
    'from' => $at('00:00'), 'to' => $at('23:59')], $managerToken)['status']);
$file = $probe->raw('manage/crew/export' . $range . '&format=docx', $managerToken);
check('اپ مدیر Word می‌گیرد', $file['status'] === 200 && str_starts_with($file['bytes'], 'PK'), (string) $file['status']);

