<?php
declare(strict_types=1);

/**
 * ساخت حساب مدیر و حساب چهار واحد مقصد — یک بار، در همان ابتدا.
 *
 * تا وقتی هیچ کاربری در جدول نیست این صفحه کار می‌کند؛ به‌محض ساخته شدن
 * اولین حساب، خودش را می‌بندد. پس حتی اگر یادتان برود پاکش کنید، کسی از
 * این راه برای خودش حساب مدیر نمی‌سازد.
 */
foreach (['Response', 'Config', 'Db'] as $class) {
    require_once __DIR__ . '/lib/' . $class . '.php';
}

$existing = (int) (Db::one('SELECT COUNT(*) AS c FROM users')['c'] ?? 0);
$done = false;
$error = null;

if ($existing > 0) {
    $error = 'کاربر در سامانه وجود دارد. این صفحه دیگر کار نمی‌کند؛ آن را حذف کنید.';
} elseif (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $code = trim((string) ($_POST['user_code'] ?? ''));
    $name = trim((string) ($_POST['full_name'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');

    if ($code === '' || $name === '' || strlen($password) < 8) {
        $error = 'نام، کد کاربری و رمز حداقل هشت نویسه‌ای لازم است.';
    } else {
        $now = Db::now();
        Db::run(
            'INSERT INTO users (id, user_code, full_name, role, password_hash, active, created_at, updated_at)
             VALUES (?, ?, ?, 1, ?, 1, ?, ?)',
            [Db::uuid(), $code, $name, password_hash($password, PASSWORD_DEFAULT), $now, $now]
        );

        // حساب چهار واحد مقصد با رمزهای تصادفی. مدیر بعداً از داخل برنامه
        // رمزها را عوض می‌کند؛ این‌ها فقط برای اولین ورود است.
        $units = ['واحد فروش', 'واحد حراست', 'واحد حقوقی', 'برق شهرستان'];
        $created = [];
        foreach ($units as $index => $unitName) {
            $unitPassword = bin2hex(random_bytes(4));
            Db::run(
                'INSERT INTO users (id, user_code, full_name, role, unit, password_hash, active,
                                    created_at, updated_at)
                 VALUES (?, ?, ?, 2, ?, ?, 1, ?, ?)',
                [
                    Db::uuid(), 'unit' . ($index + 1), $unitName, $index,
                    password_hash($unitPassword, PASSWORD_DEFAULT), $now, $now,
                ]
            );
            $created[] = ['name' => $unitName, 'code' => 'unit' . ($index + 1), 'password' => $unitPassword];
        }
        $done = true;
    }
}
?>
<!DOCTYPE html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>راه‌اندازی اولیه</title>
<style>
 body{font-family:Tahoma,sans-serif;max-width:640px;margin:30px auto;line-height:2;padding:0 14px}
 label{display:block;margin-top:12px}
 input{width:100%;padding:10px;border:1px solid #ccc;border-radius:8px;font:inherit}
 button{margin-top:16px;padding:11px 20px;border:0;border-radius:8px;background:#0b5d8a;color:#fff;font:inherit}
 .error{background:#fde4e4;color:#8a1616;padding:10px;border-radius:8px}
 table{width:100%;border-collapse:collapse;margin-top:10px}
 td,th{border-bottom:1px solid #ddd;padding:8px;text-align:right}
 code{background:#eef2f6;padding:2px 6px;border-radius:4px}
</style>
</head><body>
<h2>راه‌اندازی اولیه سامانه</h2>
<?php if ($error !== null): ?>
  <p class="error"><?= htmlspecialchars($error, ENT_QUOTES, 'UTF-8') ?></p>
<?php endif; ?>

<?php if ($done): ?>
  <p>حساب مدیر ساخته شد. رمز واحدها را همین حالا یادداشت کنید — دیگر نمایش داده نمی‌شوند:</p>
  <table>
    <tr><th>واحد</th><th>کد کاربری</th><th>رمز عبور</th></tr>
    <?php foreach ($created as $row): ?>
      <tr>
        <td><?= htmlspecialchars($row['name'], ENT_QUOTES, 'UTF-8') ?></td>
        <td><code><?= htmlspecialchars($row['code'], ENT_QUOTES, 'UTF-8') ?></code></td>
        <td><code><?= htmlspecialchars($row['password'], ENT_QUOTES, 'UTF-8') ?></code></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <p><strong>حالا فایل setup.php را از روی هاست حذف کنید.</strong></p>
<?php elseif ($existing === 0): ?>
  <form method="post">
    <label>نام و نام خانوادگی مدیر <input name="full_name" required></label>
    <label>کد کاربری مدیر <input name="user_code" required></label>
    <label>رمز عبور (حداقل هشت نویسه) <input name="password" type="password" required></label>
    <button type="submit">ساخت حساب مدیر</button>
  </form>
<?php endif; ?>
</body></html>
