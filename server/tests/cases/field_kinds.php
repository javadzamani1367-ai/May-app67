<?php
declare(strict_types=1);

/**
 * دو نوع تازه اپ میدانی — سوختگی تجهیزات (SK) و آمپرگیری تیربه‌تیر (FT) — و
 * آنچه گزارش‌دهنده از نتیجه مورد خودش می‌بیند، به اندازه تنظیم مدیر.
 *
 * از تست‌های قبلی: $reporter و $inspector (field_cycle)، $newId، $illegalRef و
 * $crypto (referrals).
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— اپ میدانی: سوختگی تجهیزات و آمپرگیری تیربه‌تیر —\n";
$burn = ['id' => $newId(), 'kind' => 5, 'created_at' => 1760000000000, 'updated_at' => 1760000000000,
         'latitude' => 33.6500, 'longitude' => 46.4500, 'accuracy' => 4, 'description' => 'کلید کل و سه فیوز سوخته',
         'payload' => ['equipment' => ['main_switch' => 1, 'nh_fuse' => 3], 'causes' => ['overload_crypto'],
                       'outage' => true, 'team' => []]];
$sent = $probe->json('POST', 'field/item', ['item' => $burn, 'files' => []], $reporter);
check('سوختگی کد SK- می‌گیرد', str_starts_with((string) ($sent['body']['data']['tracking_code'] ?? ''), 'SK-'), $sent['raw']);
$empty = ['id' => $newId(), 'payload' => ['equipment' => ['meter' => 0]]] + $burn;
equals('سوختگی بی‌تجهیز رد می‌شود', 422, $probe->json('POST', 'field/item', ['item' => $empty, 'files' => []], $reporter)['status']);

$pole = static fn (float $lat, float $r, float $s, float $t): array =>
    ['lat' => $lat, 'lon' => 46.46, 'acc' => 4, 'state' => 'measured', 'phases' => ['r' => $r, 's' => $s, 't' => $t]];
$line = ['id' => $newId(), 'kind' => 6, 'created_at' => 1760000000000, 'updated_at' => 1760000000000,
         'latitude' => 33.6600, 'longitude' => 46.4600, 'accuracy' => 4, 'plate' => 'P-5521', 'address' => 'روستای آزمون',
         'payload' => ['feeder' => 'B', 'network' => 'abc', 'start' => ['r' => 100, 's' => 100, 't' => 100],
                       'poles' => [$pole(33.6600, 95, 96, 94), $pole(33.6605, 40, 45, 38)],
                       'analysis' => ['heavy' => [['from' => 1, 'to' => 2, 'drop' => 162]], 'rises' => [], 'tail_a' => 123,
                                      'heaviest' => ['from' => 1, 'to' => 2, 'drop' => 162]]]];
equals('کاربر بی‌مجوز بازرسی تیربه‌تیر نمی‌فرستد', 403,
    $probe->json('POST', 'field/item', ['item' => $line, 'files' => []], $reporter)['status']);
$sent = $probe->json('POST', 'field/item', ['item' => $line, 'files' => []], $inspector);
check('تیربه‌تیر کد FT- می‌گیرد', str_starts_with((string) ($sent['body']['data']['tracking_code'] ?? ''), 'FT-'), $sent['raw']);
$oneLine = ['id' => $newId(), 'payload' => ['feeder' => 'B', 'poles' => [$pole(33.66, 1, 2, 3)]]] + $line;
equals('تیربه‌تیر با یک تیر رد می‌شود', 422, $probe->json('POST', 'field/item', ['item' => $oneLine, 'files' => []], $inspector)['status']);

$jar = tempnam(sys_get_temp_dir(), 'admin-kinds');
$probe->page('admin/login.php', $jar, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$page = $probe->page('admin/item.php?id=' . $line['id'], $jar)['body'];
check('پنل نمودار و جدول تیرها را نشان می‌دهد',
    substr_count($page, '<polyline') === 3 && str_contains($page, 'بیشترین افت') && str_contains($page, 'افت ۱۶۲'));
// نسخه ب: اکیپ، فیدر فشار متوسط و مشترک نامتعارف؛ بی‌سیم‌بان پذیرفته نمی‌شود.
$house = ['phase' => 1, 'amps' => ['r' => 32], 'lat' => 33.6603, 'lon' => 46.4602, 'at' => 1760000100000,
          'olat' => 33.6603, 'olon' => 46.46021, 'oacc' => 3, 'name' => 'مغازه آزمون', 'postal_codes' => ['6931234567'],
          'abnormal' => true, 'amp_photos' => [1760000100500]];
$lineV2 = ['id' => $newId(), 'payload' => ['v' => 2, 'crew' => ['lead' => ['name' => 'مسئول آزمون']],
    'mv_feeder' => ['name' => 'فیدر ۲۰ آزمون'], 'abnormal_a' => ['single' => 25, 'three' => 40]] + $line['payload']] + $line;
$lineV2['payload']['poles'][0] += ['b1' => 2, 'b3' => 0, 'branches' => 2, 'subscribers' => [$house]];
equals('تیربه‌تیر نسخه ۲ بی‌سیم‌بان رد می‌شود', 422,
    $probe->json('POST', 'field/item', ['item' => $lineV2, 'files' => []], $inspector)['status']);
$lineV2['payload']['crew']['lineman'] = ['name' => 'سیم‌بان آزمون'];
$sent = $probe->json('POST', 'field/item', ['item' => $lineV2, 'files' => []], $inspector);
equals('تیربه‌تیر نسخه ۲ با اکیپ و مشترک پذیرفته می‌شود', 200, $sent['status'], $sent['raw']);
$page = $probe->page('admin/item.php?id=' . $lineV2['id'], $jar)['body'];
check('پنل اکیپ و فیدر فشار متوسط را نشان می‌دهد',
    str_contains($page, 'مسئول آزمون') && str_contains($page, 'سیم‌بان آزمون') && str_contains($page, 'فیدر ۲۰ آزمون'));
check('پنل جدول مشترکان نامتعارف را دارد', str_contains($page, 'مشترکان نامتعارف') && str_contains($page, 'مغازه آزمون')
    && str_contains($page, '6931234567') && str_contains($page, 'حد مجاز: تکفاز ۲۵'));
check('جدول تیرها انشعاب تکفاز و سه فاز را دارد', str_contains($page, 'تکفاز ۲، سه فاز ۰'));
$docx = $probe->page('admin/item_docx.php?id=' . $lineV2['id'], $jar);
check('Word مشترکان نامتعارف را دارد', $docx['status'] === 200 && str_starts_with($docx['body'], 'PK'));
$page = $probe->page('admin/item.php?id=' . $burn['id'], $jar)['body'];
check('پنل تجهیزات سوخته را به فارسی نشان می‌دهد', str_contains($page, 'کلید کل') && str_contains($page, '× ۳'));
check('فیلتر نوع، دو نوع تازه را دارد',
    str_contains($probe->page('admin/items.php?kind=5', $jar)['body'], 'سوختگی تجهیزات'));
$probe->page('admin/logout.php', $jar);
@unlink($jar);

echo "\n— اپ میدانی: نتیجه برای گزارش‌دهنده —\n";
$pdo->prepare('UPDATE field_items SET status = 3, updated_at = ?, result = ? WHERE id = ?')->execute([
    1760000500000, json_encode(['outcome' => 'confirmed', 'note' => 'انشعاب غیرمجاز به چاه', 'at' => 1760000400000,
        'by' => 'کارشناس آزمون'], JSON_UNESCAPED_UNICODE), $illegalRef['id']]);
$pdo->prepare('UPDATE field_items SET status = 5, updated_at = ? WHERE id = ?')->execute([1760000500000, $burn['id']]);
$pdo->prepare("INSERT INTO field_events (item_id, at, user_id, action, from_status, to_status, note)
               VALUES (?, ?, NULL, 'status', 0, 5, ?)")->execute([$burn['id'], 1760000450000, 'تصویر تجهیز سوخته واضح نیست']);
$setDetail = static fn (int $level) => $pdo->prepare(
    'INSERT INTO field_settings (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)')
    ->execute(['reporter_result_detail', (string) $level]);
$infoOf = static function (string $id) use ($probe, $reporter): ?array {
    foreach ($probe->json('GET', 'field/items&since=0', [], $reporter)['body']['data']['items'] ?? [] as $row) {
        if ($row['id'] === $id) {
            return $row['info'];
        }
    }
    return null;
};

$setDetail(1);
$info = $infoOf($illegalRef['id']);
equals('سطح ۱: خود نتیجه', ['confirmed', 'تخلف تأیید شد'], [$info['result']['outcome'] ?? null, $info['result']['outcome_name'] ?? null]);
check('سطح ۱: بی‌توضیح کارشناس', !isset($info['result']['note']));
check('نام کارشناس هرگز نمی‌رود', !str_contains(json_encode($info, JSON_UNESCAPED_UNICODE), 'کارشناس آزمون'));
$setDetail(2);
equals('سطح ۲: با توضیح کارشناس', 'انشعاب غیرمجاز به چاه', $infoOf($illegalRef['id'])['result']['note'] ?? null);
$setDetail(0);
equals('سطح ۰: فقط وضعیت', null, $infoOf($illegalRef['id']));
equals('دلیل رد در هر سطحی به گزارش‌دهنده می‌رسد', 'تصویر تجهیز سوخته واضح نیست', $infoOf($burn['id'])['note'] ?? null);

$caseReport = (string) $pdo->query("SELECT id FROM reports WHERE tracking_code = '{$crypto['code']}'")->fetchColumn();
$pdo->prepare('UPDATE field_items SET result = ?, updated_at = ? WHERE id = ?')->execute([
    json_encode(['outcome' => 'case', 'report_id' => $caseReport, 'at' => 1760000600000]), 1760000600000, $crypto['id']]);
$setDetail(2);
$case = $infoOf($crypto['id'])['result']['case'] ?? [];
equals('سطح ۲ رمزارز: کد پرونده بازدید', $crypto['code'], $case['tracking_code'] ?? null);
check('سطح ۲ رمزارز: شمار دستگاه‌ها', array_key_exists('devices', $case));
$setDetail(1);
