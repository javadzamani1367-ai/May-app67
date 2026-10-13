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
