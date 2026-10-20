<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$manager = admin_require();

// درخواست ثبت دستگاه: کارشناسی که گوشی تازه دارد از صفحه ورود اپ درخواست
// می‌فرستد. «ساخت کاربر» فرم را با همان مشخصات پر می‌کند و ذخیره، درخواست را
// می‌بندد (Users::save)؛ «رد» فقط درخواست را می‌بندد.
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST' && ($_POST['action'] ?? '') === 'reject_request') {
    csrf_check();
    Db::run('UPDATE device_requests SET status = 2, decided_at = ?, decided_by = ? WHERE id = ? AND status = 0',
        [Db::now(), $manager['id'], (string) ($_POST['id'] ?? '')]);
    header('Location: users.php?rejected=1');
    exit;
}
$requests = Db::all('SELECT * FROM device_requests WHERE status = 0 ORDER BY created_at DESC LIMIT 100');

// صفحه‌بندی از همین حالا: سقفی برای تعداد کاربر نیست و فهرست نباید با رشد کند شود.
$perPage = 100;
$page = max(1, (int) ($_GET['page'] ?? 1));
$role = isset($_GET['role']) && $_GET['role'] !== '' ? (int) $_GET['role'] : null;
$where = $role === null ? '' : 'WHERE role = ?';
$params = $role === null ? [] : [$role];
$total = (int) (Db::one("SELECT COUNT(*) AS n FROM users $where", $params)['n'] ?? 0);
$rows = Db::all(
    "SELECT id, user_code, full_name, role, unit, areas, permissions, active, device_code, updated_at
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
    <div><a href="persons.php"><button type="button">اشخاص و کاربری‌ها</button></a>
      <a href="person.php"><button type="button">شخص تازه</button></a>
      <a href="requests.php"><button type="button" style="background:#b45309">درخواست‌های کاربری</button></a>
      <a href="areas.php"><button type="button" style="background:#7c3aed">کاربرهای ناحیه‌ها (یک‌جا)</button></a></div>
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

<?php if ($requests !== [] || isset($_GET['rejected'])): ?>
<div class="card">
  <h3>درخواست‌های ثبت دستگاه (<?= fa_digits((string) count($requests)) ?>)</h3>
  <?php if (isset($_GET['rejected'])): ?><p class="notice">درخواست رد شد.</p><?php endif; ?>
  <?php if ($requests !== []): ?>
  <table>
    <tr><th>نام</th><th>تلفن</th><th>شهرستان</th><th>کد دستگاه</th><th>زمان</th><th></th></tr>
    <?php foreach ($requests as $req): ?>
      <tr>
        <td><?= e($req['full_name'] ?: '—') ?></td>
        <td dir="ltr" style="text-align:right"><?= e((string) $req['phone']) ?></td>
        <td><?= e((string) $req['county']) ?></td>
        <td dir="ltr" style="text-align:right"><code><?= e((string) $req['device_code']) ?></code></td>
        <td class="nw"><?= e(Jalali::format((int) $req['created_at'])) ?></td>
        <td style="white-space:nowrap">
          <a href="user.php?request=<?= e(urlencode((string) $req['id'])) ?>"><button type="button">ساخت کاربر</button></a>
          <form method="post" style="display:inline" onsubmit="return confirm('این درخواست رد شود؟')">
            <?= csrf_field() ?><input type="hidden" name="action" value="reject_request">
            <input type="hidden" name="id" value="<?= e((string) $req['id']) ?>">
            <button type="submit" style="background:#b91c1c">رد</button>
          </form>
        </td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>
<?php endif; ?>

<div class="card">
  <?php if ($rows === []): ?>
    <p class="muted">کاربری نیست.</p>
  <?php else: ?>
  <table>
    <tr><th>نام</th><th>کد کاربری</th><th>نقش</th><th>مجوز / واحد</th><th>ناحیه</th><th>وضعیت</th></tr>
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
        <td><?php $own = Areas::parse($row['areas'] ?? ''); echo e($own === [] ? '—' : implode('، ', array_map(static fn ($c) => Jalali::digits($c), $own))); ?></td>
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
