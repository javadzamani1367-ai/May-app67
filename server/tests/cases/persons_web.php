<?php
declare(strict_types=1);

/**
 * پنل وب برای اشخاص و برق غیرمجاز: فهرست مرتب اشخاص، جدول ناحیه × نقش با رمز یک‌بارمصرف،
 * صف درخواست‌های کاربری، و پنل ستاد غیرمجاز؛ مدیر در پرونده غیرمجاز فقط ناظر است و فرم
 * رسمی غیرمجاز فهرست دستگاه ندارد. از persons.php و illegal_flow.php.
 *
 * @var HttpProbe $probe
 * @var PDO $pdo
 * @var string $ali
 * @var string $ghCase
 * @var callable $csrfOf
 */

echo "\n— پنل وب: اشخاص —\n";
$pdo->exec('DELETE FROM login_attempts');
$mgr = tempnam(sys_get_temp_dir(), 'mgr-persons');
$probe->page('admin/login.php', $mgr, ['user_code' => 'mgr', 'password' => 'manager-pass-1']);
$list = $probe->page('admin/persons.php?area=410', $mgr)['body'];
check('فهرست اشخاص: ناحیه با دسته نقش', str_contains($list, 'دره‌شهر') && str_contains($list, 'مجریان') && str_contains($list, 'علی نمونه'));
$page = $probe->page("admin/person.php?id=$ali", $mgr)['body'];
check('صفحه شخص: جدول ناحیه × نقش و کد نصب هر اپ', str_contains($page, 'ذخیره نقش‌ها') && str_contains($page, 'اپ بازرسی میدانی'));
check('صفحه شخص: کار در هر شهرستان', str_contains($page, 'محل خدمت اصلی') && str_contains($page, 'کار در این بازه'));
$ticked = $probe->page("admin/person.php?id=$ali", $mgr, ['csrf' => $csrfOf($page), 'action' => 'accounts',
    'tick' => ['410:illegal', '410:expert', '410:legal', '405:crew_lead'], 'perm' => ['405:crew_lead' => ['1', '2']]]);
$after = $probe->page((string) $ticked['location'] === '' ? "admin/person.php?id=$ali" : 'admin/' . $ticked['location'], $mgr)['body'];
check('تیک تازه: کد و رمز یک‌بارمصرف همان‌جا', str_contains($after, 'فقط همین یک بار') && str_contains($after, '405-lead-'));
check('و بار دوم دیده نمی‌شود', !str_contains($probe->page("admin/person.php?id=$ali", $mgr)['body'], 'فقط همین یک بار'));
$pdo->exec("INSERT INTO device_requests (id, device_code, full_name, phone, status, created_at, national_id, app, note)
            VALUES ('rq-web-1', 'EEEE0000', 'درخواست وب', '0918', 0, 1, NULL, 'field', 'سیم‌بان')");
$requests = $probe->page('admin/requests.php', $mgr)['body'];
check('صف درخواست‌ها با اپ و کد نصب', str_contains($requests, 'درخواست وب') && str_contains($requests, 'اپ بازرسی میدانی'));
$noId = $probe->page('admin/requests.php', $mgr, ['csrf' => $csrfOf($requests), 'id' => 'rq-web-1', 'action' => 'register']);
check('شخص تازه بی کد ملی ساخته نمی‌شود', str_contains($noId['body'], 'کد ملی'));

echo "\n— پنل وب: برق غیرمجاز —\n";
$hq = tempnam(sys_get_temp_dir(), 'hq-desk');
$in = $probe->page('admin/login.php', $hq, ['user_code' => 'ill', 'password' => 'area-pass-123']);
equals('ستاد وارد پنل می‌شود و به میز پیگیری می‌رود', 'board.php', $in['location']);
check('میز ستاد پرونده غیرمجاز را دارد', str_contains($probe->page('admin/board.php', $hq)['body'], 'GH-'));
equals('اشخاص برای ستاد نیست', 'board.php', $probe->page('admin/persons.php', $hq)['location']);
$asManager = $probe->page("admin/case.php?id=$ghCase", $mgr)['body'];
check('مدیر در پرونده غیرمجاز ناظر است', str_contains($asManager, 'مدیر ناظر است') && !str_contains($asManager, 'name="action" value="to_expert"'));
$print = $probe->page("admin/case_print.php?id=$ghCase", $hq)['body'];
check('فرم رسمی غیرمجاز: عنوان خودش، بی فهرست دستگاه', str_contains($print, 'بازرسی برق غیرمجاز') && !str_contains($print, 'فهرست دستگاه‌ها'));
check('عملکرد واحدها برای مدیر، زبانه برق غیرمجاز', str_contains($probe->page('admin/performance.php?kind=1', $mgr)['body'], 'عملکرد واحدها — برق غیرمجاز'));
check('تنظیم مهلت‌های غیرمجاز و کارشناس پیش‌فرض', str_contains($probe->page('admin/flow_settings.php', $mgr)['body'], 'کارشناس پیش‌فرض برق غیرمجاز'));
