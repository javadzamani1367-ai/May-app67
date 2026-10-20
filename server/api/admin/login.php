<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $code = Auth::latin(trim((string) ($_POST['user_code'] ?? '')));
    $password = (string) ($_POST['password'] ?? '');
    $locked = $code === '' ? null : LoginGuard::lockedMessage($code);
    $user = $code === '' || $locked !== null ? null : Auth::userByCode($code);

    // همان پیام برای «نیست» و «رمز غلط»، و همان قفل ورود ناموفق اپ‌ها.
    if ($locked !== null) {
        $error = $locked;
    } elseif ($user === null || !Auth::passwordMatches($user, $password)) {
        if ($code !== '') {
            LoginGuard::failed($code);
        }
        $error = 'کد کاربری یا رمز عبور درست نیست.';
    } elseif (!in_array((int) $user['role'], ADMIN_ROLES, true)) {
        $error = 'این پنل برای مدیر و مجریان است.';
    } else {
        LoginGuard::succeeded($code);
        session_regenerate_id(true);
        $_SESSION['admin_id'] = $user['id'];
        header('Location: ' . admin_home($user));
        exit;
    }
}

portal_header('ورود', false, 'پنل مدیر و مجریان');
?>
<div class="card">
  <h2>ورود به پنل</h2>
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
  <p class="muted">همان کد و رمزی که مدیر یا مجری با آن در اپ وارد می‌شود. هر مجری فقط ناحیه‌های خودش را می‌بیند.</p>
</div>
<?php portal_footer();
