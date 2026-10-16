<?php
declare(strict_types=1);

/**
 * موردهای میدانی در اپ مدیر (/manage/field/*): فهرست با شمار وضعیت‌ها، یک
 * مورد با جزئیات و تاریخچه، و تغییر وضعیت با همان قاعده‌های پنل وب.
 *
 * از تست‌های قبلی: $line و $burn (field_kinds)، $managerToken و $expertToken.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 */

echo "\n— اپ مدیر: موردهای میدانی —\n";
equals('کارشناس فهرست مدیر را نمی‌بیند', 403, $probe->json('GET', 'manage/field/items', [], $expertToken)['status']);
$list = $probe->json('GET', 'manage/field/items&kind=6', [], $managerToken);
$codes = array_column($list['body']['data']['items'] ?? [], 'id');
check('فیلتر نوع، مورد تیربه‌تیر را می‌آورد', in_array($line['id'], $codes, true), $list['raw']);
check('همه سطرها از همان نوع‌اند', array_unique(array_column($list['body']['data']['items'] ?? [], 'kind')) === [6]);
check('شمار وضعیت‌ها برای تب‌ها می‌آید', (int) ($list['body']['data']['counts']['0'] ?? 0) >= 1, $list['raw']);

$one = $probe->json('GET', 'manage/field/item&id=' . $line['id'], [], $managerToken)['body']['data']['item'] ?? [];
check('مورد با جزئیات تیرها', in_array('تیر ۲ — ', array_map(static fn ($d) => mb_substr($d['label'], 0, 7), $one['details'] ?? []), true)
    || count(array_filter($one['details'] ?? [], static fn ($d) => str_starts_with($d['label'], 'تیر ۲'))) === 1);
check('تاریخچه ثبت از گوشی را دارد', ($one['events'][0]['what'] ?? '') === 'ثبت از گوشی');
check('ارجاع با نیاز به کارشناس در گام‌های بعدی',
    in_array(['status' => 2, 'name' => 'ارجاع‌شده', 'needs_note' => false, 'needs_expert' => true], $one['next'] ?? [], true));
check('فهرست کارشناسان برای ارجاع', ($one['experts'] ?? []) !== []);
$v2 = $probe->json('GET', 'manage/field/item&id=' . $lineV2['id'], [], $managerToken)['body']['data']['item'] ?? [];
$labels = array_column($v2['details'] ?? [], 'label');
check('اپ مدیر: اکیپ و مشترک نامتعارف در جزئیات', in_array('مسئول اکیپ', $labels, true)
    && count(array_filter($labels, static fn ($l) => str_contains($l, 'مشترک نامتعارف'))) === 1);
equals('مورد ناموجود ۴۰۴', 404,
    $probe->json('GET', 'manage/field/item&id=00000000-0000-4000-8000-000000000000', [], $managerToken)['status']);

$refuse = $probe->json('POST', 'manage/field/status', ['id' => $line['id'], 'status' => 5, 'note' => ''], $managerToken);
check('رد بی‌دلیل پذیرفته نمی‌شود', $refuse['status'] === 409 && str_contains($refuse['raw'], 'دلیل'), $refuse['raw']);
$expertId = (string) $pdo->query('SELECT id FROM users WHERE role = 0 AND active = 1 ORDER BY created_at LIMIT 1')->fetchColumn();
$due = (int) (microtime(true) * 1000) + 3 * 86_400_000;
$referred = $probe->json('POST', 'manage/field/status',
    ['id' => $line['id'], 'status' => 2, 'assigned_to' => $expertId, 'due_at' => $due, 'note' => 'از تیر ۱ تا ۲ بررسی شود'], $managerToken);
$after = $referred['body']['data']['item'] ?? [];
check('ارجاع از اپ مدیر انجام شد', ($after['status'] ?? null) === 2 && ($after['due_at'] ?? null) === $due, $referred['raw']);
check('ارجاع در تاریخچه با نام کارشناس', str_starts_with((string) (end($after['events'])['note'] ?? ''), 'ارجاع به '));
check('کارشناس اعلان ارجاع گرفت', (int) $pdo->query("SELECT COUNT(*) FROM notifications WHERE kind = 'field_referral' AND user_id = "
    . $pdo->quote($expertId))->fetchColumn() >= 1);
equals('وضعیت ناممکن رد می‌شود', 409,
    $probe->json('POST', 'manage/field/status', ['id' => $burn['id'], 'status' => 4], $managerToken)['status']);
equals('عکس ناموجود ۴۰۴', 404, $probe->raw('manage/field/file&id=00000000-0000-4000-8000-000000000000', $managerToken)['status']);

echo "\n— ثبت موقعیت روی نقشه: زمان و موقعیت کاربر —\n";
$placed = ['id' => $newId(), 'position_at' => 1760000100000, 'observer_lat' => 33.65009, 'observer_lon' => 46.45,
           'observer_accuracy' => 4.4] + $burn;
$probe->json('POST', 'field/item', ['item' => $placed, 'files' => []], $reporter);
$row = $pdo->query("SELECT position_at, observer_lat, observer_accuracy FROM field_items WHERE id = '{$placed['id']}'")->fetch(PDO::FETCH_ASSOC);
equals('زمان و موقعیت کاربر ذخیره شد', [1760000100000, 33.65009, 4.4],
    [(int) $row['position_at'], (float) $row['observer_lat'], (float) $row['observer_accuracy']]);
$note = $probe->json('GET', 'manage/field/item&id=' . $placed['id'], [], $managerToken)['body']['data']['item']['position_note'] ?? '';
check('اپ مدیر فاصله کاربر از نقطه را می‌بیند', str_contains((string) $note, 'کاربر ۱۰ متر از نقطه'), (string) $note);
