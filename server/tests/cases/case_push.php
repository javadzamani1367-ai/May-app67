<?php
declare(strict_types=1);

/**
 * پرونده بالا می‌رود، تصویرش پشت سرش، و مدیر می‌کشدش پایین.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $expertToken
 * @var string $managerToken
 */

echo "\n— فرستادن پرونده —\n";
$reportId = '11111111-1111-1111-1111-111111111111';
$mediaId = 'aaaaaaaa-1111-2222-3333-444444444444';
$mediaPath = "media/$reportId/1757600500000.jpg";

$report = [
    'id' => $reportId,
    'tracking_code' => 'M-401-050614-482917',
    'report_type' => 3,
    'status' => 1,
    'expert_code' => '1042',
    'report_date' => 1757000000000,
    'visit_date' => 1757600000000,
    'created_at' => 1756900000000,
    'updated_at' => 1757700000000,
    'county' => 'دره‌شهر',
    'district' => 'ماژین',
    'address' => 'روستای نمونه',
    'owner_name' => 'نام مالک',
    'owner_national_id' => '4569876543',
    'phase_type' => 1,
    'amperage_r' => 32.0,
    'voltage_r' => 228.0,
    'total_watt' => 21930.0,
    'approval_state' => 0,
    'description' => 'شرح بازدید',
    // ردیف فرزند با ستون row_number — همان ستونی که کلمه رزرو بود و
    // بدون بک‌کوت، همین درخواست را با خطای نحوی می‌شکست.
    'devices' => [[
        'id' => 'dddddddd-1111-2222-3333-444444444444',
        'report_id' => $reportId,
        'row_number' => 1,
        'model' => 'S19',
        'serial_number' => 'SN-1',
        'power_watt' => 3250.0,
        'entry_method' => 0,
    ]],
    'attendees' => [[
        'id' => 'cccccccc-1111-2222-3333-444444444444',
        'report_id' => $reportId,
        'organization' => 1,
        'full_name' => 'سرگرد نمونه',
        'position' => 'افسر',
    ]],
    'media' => [[
        'id' => $mediaId,
        'report_id' => $reportId,
        'type' => 0,
        'file_path' => $mediaPath,
        'caption' => 'کنتور',
        'captured_at' => 1757600500000,
        'size_bytes' => 0,
    ]],
];

$push = $probe->json('POST', 'sync/report', [
    'report' => $report,
    'device_code' => 'DEVICE-AAA',
], $expertToken);
check('پرونده ذخیره می‌شود', ($push['body']['ok'] ?? false) === true, $push['raw']);

$row = $pdo->query("SELECT * FROM reports WHERE id = '$reportId'")->fetch(PDO::FETCH_ASSOC);
check('پرونده در جدول هست', is_array($row));
equals('فارسی سالم رسید', 'دره‌شهر', $row['county'] ?? '');
equals('ولتاژ سالم رسید', 228.0, (float) ($row['voltage_r'] ?? 0));
equals('دستگاه ثبت شد', 1, (int) $pdo->query("SELECT COUNT(*) FROM devices WHERE report_id = '$reportId'")->fetchColumn());
equals('حاضر ثبت شد', 1, (int) $pdo->query("SELECT COUNT(*) FROM attendees WHERE report_id = '$reportId'")->fetchColumn());

$missing = $push['body']['data']['missing_files'] ?? [];
equals('سرور می‌گوید یک فایل کم دارد', 1, count($missing));
equals('و می‌گوید کدام', $mediaId, $missing[0]['id'] ?? '');

echo "\n— فرستادن تصویر —\n";
$sample = sys_get_temp_dir() . '/probe-photo.jpg';
file_put_contents($sample, random_bytes(2048));

$upload = $probe->upload("sync/file&kind=media&id=$mediaId", 'file', $sample, $expertToken);
check('تصویر بالا می‌رود', ($upload['body']['ok'] ?? false) === true, $upload['raw']);
equals('روی همان مسیر گوشی نشست', $mediaPath, $upload['body']['data']['path'] ?? '');

$size = (int) $pdo->query("SELECT size_bytes FROM media WHERE id = '$mediaId'")->fetchColumn();
equals('حجم روی سطر نوشته شد', 2048, $size);

$again = $probe->json('POST', 'sync/report', [
    'report' => array_merge($report, ['updated_at' => 1757700000001]),
    'device_code' => 'DEVICE-AAA',
], $expertToken);
equals(
    'بار دوم، سرور دیگر آن فایل را نمی‌خواهد',
    0,
    count($again['body']['data']['missing_files'] ?? ['x'])
);

echo "\n— دسترسی به فایل —\n";
$download = $probe->raw("sync/file&kind=media&id=$mediaId", $expertToken);
equals('کارشناس فایل خودش را می‌گیرد', 200, $download['status']);
equals('و همان بایت‌ها را', file_get_contents($sample), $download['bytes']);

// حساب یک واحد: وارد سامانه هست، ولی پرونده مال او نیست.
$unitPassword = 'unit-pass-1';
$pdo->prepare('UPDATE users SET password_hash = ? WHERE user_code = ?')
    ->execute([password_hash($unitPassword, PASSWORD_DEFAULT), 'unit1']);
$unitLogin = $probe->json('POST', 'auth/login', ['user_code' => 'unit1', 'password' => $unitPassword]);
$unitToken = $unitLogin['body']['data']['token'] ?? '';
check('حساب واحد وارد می‌شود', $unitToken !== '', $unitLogin['raw']);

$strangerExpert = $probe->json('POST', 'users/save', [
    'user_code' => '2050',
    'full_name' => 'کارشناس دیگر',
    'role' => 0,
    'device_code' => 'DEVICE-CCC',
    'password' => 'other-pass-1',
    'active' => 1,
], $managerToken);
check('کارشناس دوم ثبت می‌شود', ($strangerExpert['body']['ok'] ?? false) === true);
$strangerToken = $probe->json('POST', 'auth/login', [
    'user_code' => '2050',
    'password' => 'other-pass-1',
    'device_code' => 'DEVICE-CCC',
])['body']['data']['token'] ?? '';

$forbidden = $probe->raw("sync/file&kind=media&id=$mediaId", $strangerToken);
equals('کارشناس دیگر به تصویر این پرونده نمی‌رسد', 403, $forbidden['status']);

$notMine = $probe->json('GET', "sync/report&id=$reportId", [], $strangerToken);
equals('و خود پرونده را هم نمی‌گیرد', 403, $notMine['status']);

require __DIR__ . '/case_approval.php';
