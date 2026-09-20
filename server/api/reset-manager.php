<?php
declare(strict_types=1);

/**
 * بازیابی حساب مدیر.
 *
 * setup.php فقط یک بار کار می‌کند: به‌محض ساخته شدن اولین کاربر خودش را
 * می‌بندد. اگر رمز مدیر گم شود، هیچ راهی از داخل برنامه باقی نمی‌ماند — مدیر
 * تنها کسی است که می‌تواند رمز کاربران را عوض کند و خودش هم بیرون مانده است.
 *
 * این صفحه همان یک در را باز می‌کند و نه بیشتر: رمز مدیر را عوض می‌کند، یا
 * اگر هیچ مدیری در جدول نیست یکی می‌سازد.
 *
 * دروازه‌اش app_key داخل config.php است. آن کلید هرگز از سرور بیرون نمی‌رود و
 * فقط کسی که به فایل‌های هاست دسترسی دارد می‌تواند بخواندش؛ پس این صفحه به
 * کسی چیزی نمی‌دهد که از قبل نداشته باشد. با این حال بعد از استفاده پاکش کنید.
 */
foreach (['Config', 'Db'] as $class) {
    require_once __DIR__ . '/lib/' . $class . '.php';
}

const PLACEHOLDER_KEY = 'CHANGE-ME-TO-A-LONG-RANDOM-STRING';

$appKey = (string) Config::get('app_key', '');
$error = null;
$done = null;
$users = null;

if ($appKey === '' || $appKey === PLACEHOLDER_KEY) {
    // بدون کلید واقعی، دروازه‌ای در کار نیست و این صفحه می‌شد یک در باز.
    $error = 'ابتدا app_key را در config.php به یک رشته تصادفی و طولانی تغییر دهید.';
} elseif (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $given = (string) ($_POST['app_key'] ?? '');
    if (!hash_equals($appKey, $given)) {
        usleep(300000);
        $error = 'کلید امنیتی درست نیست. مقدار app_key را از فایل config.php بردارید.';
    } else {
        $code = trim((string) ($_POST['user_code'] ?? ''));
        $name = trim((string) ($_POST['full_name'] ?? ''));
        $password = (string) ($_POST['password'] ?? '');

        if (isset($_POST['list'])) {
            $users = Db::all('SELECT user_code, full_name, role, active FROM users ORDER BY role, user_code');
        } elseif ($code === '' || strlen($password) < 8) {
            $error = 'کد کاربری و رمز حداقل هشت نویسه‌ای لازم است.';
        } else {
            $now = Db::now();
            $existing = Db::one('SELECT id, role FROM users WHERE user_code = ?', [$code]);
            $hash = password_hash($password, PASSWORD_DEFAULT);

            if ($existing === null) {
                Db::run(
                    'INSERT INTO users (id, user_code, full_name, role, password_hash, active, created_at, updated_at)
                     VALUES (?, ?, ?, 1, ?, 1, ?, ?)',
                    [Db::uuid(), $code, $name !== '' ? $name : $code, $hash, $now, $now]
                );
                $done = 'حساب مدیر با کد کاربری «' . $code . '» ساخته شد.';
            } else {
                // نقش هم به مدیر برمی‌گردد: حسابی که نقشش واحد است، هر رمزی
                // هم داشته باشد، در اپ مدیر به جایی نمی‌رسد.
                Db::run(
                    'UPDATE users SET password_hash = ?, role = 1, active = 1, updated_at = ?'
                    . ($name !== '' ? ', full_name = ?' : '') . ' WHERE id = ?',
                    $name !== ''
                        ? [$hash, $now, $name, $existing['id']]
                        : [$hash, $now, $existing['id']]
                );
                // نشست‌های قدیمی همان حساب باطل می‌شوند، وگرنه عوض کردن رمز
                // کسی را بیرون نمی‌کرد.
                Db::run('DELETE FROM tokens WHERE user_id = ?', [$existing['id']]);
                $done = 'رمز حساب «' . $code . '» عوض شد و نقش آن مدیر است.';
            }
        }
    }
}

$roleNames = [0 => 'کارشناس', 1 => 'مدیر', 2 => 'واحد'];
?>
<!DOCTYPE html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>بازیابی حساب مدیر</title>
<style>
 body{font-family:Tahoma,sans-serif;max-width:640px;margin:30px auto;line-height:2;padding:0 14px}
 label{display:block;margin-top:12px}
 input{width:100%;padding:10px;border:1px solid #ccc;border-radius:8px;font:inherit}
 button{margin-top:16px;padding:11px 20px;border:0;border-radius:8px;background:#0b5d8a;color:#fff;font:inherit}
 button.plain{background:#eef2f6;color:#0b5d8a}
 .error{background:#fde4e4;color:#8a1616;padding:10px;border-radius:8px}
 .done{background:#e3f5e6;color:#14612a;padding:10px;border-radius:8px}
 table{width:100%;border-collapse:collapse;margin-top:10px}
 td,th{border-bottom:1px solid #ddd;padding:8px;text-align:right}
 code{background:#eef2f6;padding:2px 6px;border-radius:4px}
</style>
</head><body>
<h2>بازیابی حساب مدیر</h2>

<?php if ($error !== null): ?>
  <p class="error"><?= htmlspecialchars($error, ENT_QUOTES, 'UTF-8') ?></p>
<?php endif; ?>
<?php if ($done !== null): ?>
  <p class="done"><?= htmlspecialchars($done, ENT_QUOTES, 'UTF-8') ?></p>
  <p>حالا در اپ مدیر با همین کد کاربری و رمز وارد شوید.
     <strong>سپس این فایل را از روی هاست پاک کنید.</strong></p>
<?php endif; ?>

<?php if ($users !== null): ?>
  <p>کاربران موجود (رمزها نمایش داده نمی‌شوند و قابل بازیابی نیستند):</p>
  <table>
    <tr><th>کد کاربری</th><th>نام</th><th>نقش</th><th>فعال</th></tr>
    <?php foreach ($users as $row): ?>
      <tr>
        <td><code><?= htmlspecialchars((string) $row['user_code'], ENT_QUOTES, 'UTF-8') ?></code></td>
        <td><?= htmlspecialchars((string) $row['full_name'], ENT_QUOTES, 'UTF-8') ?></td>
        <td><?= $roleNames[(int) $row['role']] ?? (int) $row['role'] ?></td>
        <td><?= ((int) $row['active'] === 1) ? 'بله' : 'خیر' ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
<?php endif; ?>

<?php if ($appKey !== '' && $appKey !== PLACEHOLDER_KEY): ?>
  <form method="post">
    <label>کلید امنیتی (مقدار <code>app_key</code> در فایل <code>config.php</code>)
      <input name="app_key" required></label>
    <button class="plain" type="submit" name="list" value="1">فقط فهرست کاربران را نشان بده</button>
    <hr style="margin:20px 0;border:0;border-top:1px solid #ddd">
    <label>کد کاربری مدیر <input name="user_code" placeholder="مثلاً manager"></label>
    <label>نام و نام خانوادگی (اختیاری) <input name="full_name"></label>
    <label>رمز عبور تازه (حداقل هشت نویسه) <input name="password" type="password"></label>
    <button type="submit">ساخت یا تغییر رمز مدیر</button>
  </form>
<?php endif; ?>
</body></html>
