<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $code = trim((string) ($_POST['user_code'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');
    $user = $code === '' ? null : Db::one('SELECT * FROM users WHERE user_code = ? AND active = 1', [$code]);

    // پیام یکسان برای هر دو حالت، تا نشود از تفاوت پیام فهمید کدام کد
    // کاربری روی سامانه وجود دارد.
    if ($user === null || empty($user['password_hash'])
        || !password_verify($password, (string) $user['password_hash'])) {
        $error = 'کد کاربری یا رمز عبور درست نیست.';
    } elseif ((int) $user['role'] !== Auth::ROLE_UNIT) {
        $error = 'این پرتال برای واحدهای مقصد است.';
    } else {
        session_regenerate_id(true);
        $_SESSION['user_id'] = $user['id'];
        header('Location: index.php');
        exit;
    }
}

portal_header('ورود واحد');
?>
<div class="card">
  <h2>ورود به پرتال واحدها</h2>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post">
    <label>کد کاربری
      <input name="user_code" autocomplete="username" required>
    </label>
    <label>رمز عبور
      <input name="password" type="password" autocomplete="current-password" required>
    </label>
    <button type="submit">ورود</button>
  </form>
  <p class="muted">کد کاربری و رمز عبور را مدیر سامانه به شما می‌دهد.</p>
</div>
<?php portal_footer();
