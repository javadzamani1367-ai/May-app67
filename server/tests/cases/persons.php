<?php
declare(strict_types=1);

/**
 * شخص و کاربری‌هایش: کد ملی معیار یکتا، تیک‌های ناحیه × نقش با کد و رمز جدا برای هر
 * کدام، کد نصب هر اپ، درخواست کاربری از اپ، اکیپ در فهرست، و اصلاح باگ اپ مدیر قدیمی
 * که هر کاربری ویرایش‌شده را کارشناس بی‌ناحیه می‌کرد.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $managerToken
 */

echo "\n— شخص و کاربری‌ها —\n";
$pdo->exec('DELETE FROM login_attempts');
$nid = static function (string $nine): string {
    $sum = 0;
    for ($i = 0; $i < 9; $i++) {
        $sum += (int) $nine[$i] * (10 - $i);
    }
    $rest = $sum % 11;
    return $nine . ($rest < 2 ? $rest : 11 - $rest);
};
$saveP = static fn (array $data) => $probe->json('POST', 'persons/save', $data, $managerToken);
equals('کد ملی نادرست پذیرفته نیست', 400, $saveP(['full_name' => 'علی نمونه', 'national_id' => '1234567890'])['status']);
equals('شخص تازه بی کد ملی پذیرفته نیست', 400, $saveP(['full_name' => 'علی نمونه'])['status']);
$aliNid = $nid('450123456');
$made = $saveP(['full_name' => 'علی نمونه', 'national_id' => $aliNid, 'phone' => '۰۹۱۲۳۴۵۶۷۸۹', 'home_area' => '410']);
$ali = (string) ($made['body']['data']['id'] ?? '');
check('شخص با کد ملی ساخته شد', $ali !== '', $made['raw']);
$dup = $saveP(['full_name' => 'علی دیگر', 'national_id' => $aliNid]);
check('کد ملی تکراری شخص قبلی را نشان می‌دهد', $dup['status'] === 400 && str_contains($dup['raw'], 'علی نمونه'));

$ticks = $probe->json('POST', 'persons/accounts', ['person_id' => $ali,
    'ticks' => ['410:illegal', '410:expert', '401:crew_lineman', '410:legal'],
    'permissions' => ['401:crew_lineman' => 1]], $managerToken)['body']['data'] ?? [];
$made = array_column($ticks['made'] ?? [], 'password', 'user_code');
equals('چهار کاربری، هر کدام با کد و رمز خودش', 4, count($made));
$codes = array_map('strval', array_keys($made));
check('کد هر کاربری کد ناحیه و سه رقم', count(preg_grep('/^(410|401)\d{3}$/', $codes)) === 4, implode(',', $codes));
$codeOf = static fn (int $role, string $area): string => (string) $pdo->query(
    "SELECT user_code FROM users WHERE person_id = '$ali' AND role = $role AND areas = '$area'")->fetchColumn();
$lineman = $codeOf(3, '401');
$illCode = $codeOf(6, '410');
check('سه کاربری ناحیه ۴۱۰ کدهای پشت سر هم', count(array_unique(preg_grep('/^410\d{3}$/', $codes))) === 3);
check('رمز هر کاربری جداست', count(array_unique(array_values($made))) === 4);
$crew = $pdo->query("SELECT c.full_name, c.role, c.county, c.active FROM crew_members c JOIN users u ON u.id = c.user_id
                     WHERE u.user_code = '$lineman'")->fetch(PDO::FETCH_ASSOC);
equals('سیم‌بان خودکار در فهرست اکیپ همان شهرستان', ['علی نمونه', 1, 'ایلام', 1],
    [$crew['full_name'] ?? null, (int) ($crew['role'] ?? -1), $crew['county'] ?? null, (int) ($crew['active'] ?? 0)]);
equals('مجوز اکیپ همان تیک', 1, (int) $pdo->query("SELECT permissions FROM users WHERE user_code = '$lineman'")->fetchColumn());

echo "\n— ورود هر کاربری از اپ خودش و نصب ثبت‌شده —\n";
$login = static fn (string $code, string $password, string $app, string $device) => $probe->json('POST', 'auth/login',
    ['user_code' => $code, 'password' => $password, 'app' => $app, 'device_code' => $device]);
$expCode = $codeOf(0, '410');
equals('بی ثبت نصب، ورود رد می‌شود و راه را می‌گوید', 'device_unregistered',
    $login($expCode, $made[$expCode], 'expert', 'AAAA-1111-2222-3333')['body']['error']['code'] ?? null);
equals('کاربری مجری غیرمجاز در اپ کارشناس، پیام اپ اشتباه', 'wrong_app',
    $login($illCode, $made[$illCode], 'expert', 'AAAA-1111-2222-3333')['body']['error']['code'] ?? null);
$request = $probe->json('POST', 'auth/request-device', ['device_code' => 'AAAA-1111-2222-3333', 'full_name' => 'علی نمونه',
    'national_id' => strtr($aliNid, ['0' => '۰', '1' => '۱', '2' => '۲', '3' => '۳', '4' => '۴', '5' => '۵', '6' => '۶', '7' => '۷', '8' => '۸', '9' => '۹']), 'phone' => '09123456789', 'app' => 'expert', 'note' => 'کارشناس دره‌شهر']);
check('درخواست کاربری با کد ملی فارسی پذیرفته شد', ($request['body']['ok'] ?? false) === true, $request['raw']);
equals('درخواست با کد ملی نادرست رد می‌شود', 400, $probe->json('POST', 'auth/request-device',
    ['device_code' => 'BBBB-0000', 'full_name' => 'x', 'national_id' => '1111111111', 'app' => 'field'])['status']);
$pending = $probe->json('GET', 'persons/requests', [], $managerToken)['body']['data']['requests'] ?? [];
$req = array_values(array_filter($pending, static fn ($r) => $r['device_code'] === 'AAAA111122223333'))[0] ?? [];
equals('درخواست خودکار به همان شخص وصل شد', 'علی نمونه', $req['person_name'] ?? null);
$decided = $probe->json('POST', 'persons/requests/decide', ['id' => $req['id'] ?? '', 'action' => 'register'], $managerToken);
equals('ثبت نصب برای همان شخص', $ali, $decided['body']['data']['person_id'] ?? null);
check('حالا کارشناس وارد می‌شود', ($login($expCode, $made[$expCode], 'expert', 'AAAA-1111-2222-3333')['body']['ok'] ?? false) === true);
equals('نصب اپ کارشناس، اپ غیرمجاز را باز نمی‌کند', 'device_unregistered',
    $login($illCode, $made[$illCode], 'illegal', 'AAAA-1111-2222-3333')['body']['error']['code'] ?? null);
bindDevice($probe, $pdo, $managerToken, $illCode, 'illegal', 'CCCC-0000-1111-2222');
check('اپ غیرمجاز با کد نصب خودش باز می‌شود',
    ($login($illCode, $made[$illCode], 'illegal', 'CCCC-0000-1111-2222')['body']['ok'] ?? false) === true);
bindDevice($probe, $pdo, $managerToken, $expCode, 'expert', 'DDDD-0000-1111-2222');
equals('نصب تازه کارشناس جای قبلی را می‌گیرد', 'device_mismatch',
    $login($expCode, $made[$expCode], 'expert', 'AAAA-1111-2222-3333')['body']['error']['code'] ?? null);

echo "\n— فهرست مرتب و صفحه شخص —\n";
$list = $probe->json('GET', 'persons&area=410', [], $managerToken)['body']['data'] ?? [];
$groups = array_column($list['areas'][0]['groups'] ?? [], 'title');
equals('ناحیه ۴۱۰ با دسته‌های نقش', ['مجریان', 'کارشناسان', 'واحدهای مقصد'], array_values(array_intersect($groups, ['مجریان', 'کارشناسان', 'واحدهای مقصد'])));
$search = $probe->json('GET', 'persons&q=' . urlencode($aliNid), [], $managerToken)['body']['data']['areas'] ?? [];
equals('جستجوی کد ملی هر دو ناحیه شخص را می‌آورد', ['401', '410'], array_column($search, 'code'));
$one = $probe->json('GET', "persons/one&id=$ali", [], $managerToken)['body']['data'] ?? [];
equals('صفحه شخص: محل خدمت اصلی اول، بعد شهرستان دیگر', ['410', '401'], array_column($one['work'] ?? [], 'area'));
equals('هر نقش در ناحیه خودش جدا', 3, count($one['work'][0]['roles'] ?? []));
equals('کد نصب هر اپ جدا', ['expert' => 'DDDD000011112222', 'illegal' => 'CCCC000011112222'], $one['devices'] ?? null);

$closed = $probe->json('POST', 'persons/accounts', ['person_id' => $ali, 'ticks' => ['410:illegal', '410:expert', '410:legal']],
    $managerToken)['body']['data'] ?? [];
equals('تیک برداشته، کاربری بسته شد', [$lineman], $closed['closed'] ?? null);
equals('و از فهرست اکیپ بیرون رفت', 0, (int) $pdo->query("SELECT c.active FROM crew_members c JOIN users u ON u.id = c.user_id
    WHERE u.user_code = '$lineman'")->fetchColumn());
$reset = $probe->json('POST', 'persons/password', ['user_id' => (string) $pdo->query("SELECT id FROM users WHERE user_code = '$expCode'")->fetchColumn()],
    $managerToken)['body']['data']['password'] ?? '';
check('رمز تازه یک‌بارمصرف', strlen($reset) === 10 && ($login($expCode, $reset, 'expert', 'DDDD-0000-1111-2222')['body']['ok'] ?? false) === true);

echo "\n— اپ مدیر قدیمی و ارقام فارسی —\n";
// اپ مدیر پیش از ۱.۸ نقش را صفر و بی «areas» می‌فرستاد؛ کاربر میدانی کارشناس بی‌ناحیه می‌شد.
$probe->json('POST', 'users/save', ['user_code' => '410-legal', 'full_name' => 'حقوقی دره‌شهر', 'role' => 0, 'county' => '',
    'phone' => '', 'device_code' => '', 'password' => '۱۲۳۴۵۶۷۸', 'active' => 1, 'note' => ''], $managerToken);
$legal = $pdo->query("SELECT role, unit, areas FROM users WHERE user_code = '410-legal'")->fetch(PDO::FETCH_ASSOC);
equals('ویرایش اپ قدیمی نقش، واحد و ناحیه را نگه داشت', [2, 2, '410'], [(int) $legal['role'], (int) $legal['unit'], $legal['areas']]);
$pdo->exec('DELETE FROM login_attempts');
$latin = $probe->json('POST', 'auth/login', ['user_code' => '۴۱۰-legal', 'password' => '12345678']);
check('رمز با ارقام فارسی، ورود با ارقام لاتین و کد با ارقام فارسی', ($latin['body']['ok'] ?? false) === true, $latin['raw']);
