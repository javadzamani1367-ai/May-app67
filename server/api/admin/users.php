<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// صفحه‌بندی از همین حالا: سقفی برای تعداد کاربر نیست و فهرست نباید با رشد کند شود.
$perPage = 100;
$page = max(1, (int) ($_GET['page'] ?? 1));
$role = isset($_GET['role']) && $_GET['role'] !== '' ? (int) $_GET['role'] : null;
$where = $role === null ? '' : 'WHERE role = ?';
$params = $role === null ? [] : [$role];
$total = (int) (Db::one("SELECT COUNT(*) AS n FROM users $where", $params)['n'] ?? 0);
$rows = Db::all(
    "SELECT id, user_code, full_name, role, unit, permissions, active, device_code, updated_at
     FROM users $where ORDER BY role, full_name LIMIT $perPage OFFSET " . (($page - 1) * $perPage),
    $params
);

admin_header('کاربران');
?>
<div class="card">
  <div class="row">
    <div>
      <h2>کاربران</h2>
      <p class="muted"><?= fa_digits((string) $total) ?> کاربر</p>
    </div>
    <div><a href="user.php"><button type="button">کاربر جدید</button></a></div>
  </div>
  <form method="get" class="row" style="justify-content:flex-start">
    <label style="margin:0">نقش
      <select name="role" onchange="this.form.submit()">
        <option value="">همه</option>
        <?php foreach (ROLE_NAMES as $value => $name): ?>
          <option value="<?= $value ?>" <?= $role === $value ? 'selected' : '' ?>><?= e($name) ?></option>
        <?php endforeach; ?>
      </select>
    </label>
  </form>
</div>

<div class="card">
  <?php if ($rows === []): ?>
    <p class="muted">کاربری نیست.</p>
  <?php else: ?>
  <table>
    <tr><th>نام</th><th>کد کاربری</th><th>نقش</th><th>مجوز / واحد</th><th>وضعیت</th></tr>
    <?php foreach ($rows as $row): ?>
      <?php $r = (int) $row['role']; ?>
      <tr>
        <td><a href="user.php?code=<?= urlencode((string) $row['user_code']) ?>"><?= e($row['full_name']) ?></a></td>
        <td dir="ltr" style="text-align:right"><?= e($row['user_code']) ?></td>
        <td><?= e(ROLE_NAMES[$r] ?? '—') ?></td>
        <td><?php
            if ($r === Auth::ROLE_FIELD) {
                echo e(permission_label((int) $row['permissions']));
            } elseif ($r === Auth::ROLE_UNIT) {
                echo e(UNIT_NAMES[(int) $row['unit']] ?? '—');
            } else {
                echo '—';
            }
        ?></td>
        <td><?= (int) $row['active'] === 1
            ? '<span class="tag tag-answered">فعال</span>'
            : '<span class="tag tag-overdue">غیرفعال</span>' ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
  <?php if ($total > $perPage): ?>
    <p class="muted">
      <?php for ($p = 1; $p <= (int) ceil($total / $perPage); $p++): ?>
        <a href="?page=<?= $p ?><?= $role === null ? '' : '&role=' . $role ?>"><?= fa_digits((string) $p) ?></a>
      <?php endfor; ?>
    </p>
  <?php endif; ?>
</div>
<?php portal_footer();
