<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $code = trim((string) ($_POST['user_code'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');
    $locked = $code === '' ? null : LoginGuard::lockedMessage($code);
    $user = $code === '' || $locked !== null
        ? null : Db::one('SELECT * FROM users WHERE user_code = ? AND active = 1', [$code]);

    // همان پیام برای «نیست» و «رمز غلط»، و همان قفل ورود ناموفق اپ‌ها.
    if ($locked !== null) {
        $error = $locked;
    } elseif ($user === null || empty($user['password_hash'])
        || !password_verify($password, (string) $user['password_hash'])) {
        if ($code !== '') {
            LoginGuard::failed($code);
        }
        $error = 'کد کاربری یا رمز عبور درست نیست.';
    } elseif ((int) $user['role'] !== Auth::ROLE_MANAGER) {
        $error = 'این پنل فقط برای مدیر است.';
    } else {
        LoginGuard::succeeded($code);
        session_regenerate_id(true);
        $_SESSION['admin_id'] = $user['id'];
        header('Location: users.php');
        exit;
    }
}

portal_header('ورود مدیر', false, 'پنل مدیر');
?>
<div class="card">
  <h2>ورود به پنل مدیر</h2>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post">
    <label>کد کاربری
      <input name="user_code" autocomplete="username" required dir="ltr">
    </label>
    <label>رمز عبور
      <input name="password" type="password" autocomplete="current-password" required>
    </label>
    <button type="submit">ورود</button>
  </form>
  <p class="muted">همان کد و رمزی که مدیر با آن در اپ مدیر وارد می‌شود.</p>
</div>
<?php portal_footer();
