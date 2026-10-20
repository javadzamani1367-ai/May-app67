<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// اشخاص و کاربری‌ها، مرتب: هر ناحیه یک کارت، در هر کارت دسته‌های نقش، در هر دسته
// نام‌ها. هر سطر یک کاربری است و به صفحه شخصش می‌رود؛ شخصی که در چند ناحیه کار
// می‌کند در هر ناحیه با نقش همان‌جا دیده می‌شود.
$q = trim((string) ($_GET['q'] ?? ''));
$area = Areas::code($_GET['area'] ?? null);
$kind = (string) ($_GET['kind'] ?? '');
$inactive = ($_GET['inactive'] ?? '') === '1';
$list = PersonDirectory::listing($q, $area, $kind === '' ? null : $kind, $inactive);
$pending = (int) (Db::one('SELECT COUNT(*) AS n FROM device_requests WHERE status = 0')['n'] ?? 0);
$people = (int) (Db::one('SELECT COUNT(*) AS n FROM persons WHERE active = 1')['n'] ?? 0);

admin_header('اشخاص و کاربری‌ها');
?>
<div class="card">
  <div class="row">
    <div>
      <h2>اشخاص و کاربری‌ها</h2>
      <p class="muted"><?= fa_digits((string) $people) ?> شخص. هر شخص یک بار با کد ملی ثبت می‌شود و برای هر نقش در هر
        ناحیه کاربری جدا با کد و رمز خودش دارد.</p>
    </div>
    <div>
      <a href="person.php"><button type="button">شخص تازه</button></a>
      <a href="requests.php"><button type="button" style="background:<?= $pending > 0 ? '#b45309' : '#64748b' ?>">درخواست‌های کاربری
        (<?= fa_digits((string) $pending) ?>)</button></a>
      <a href="users.php"><button type="button" style="background:#64748b">همه کاربری‌ها</button></a>
    </div>
  </div>
  <form method="get" class="row" style="justify-content:flex-start;gap:12px">
    <label style="margin:0">جستجو<input name="q" value="<?= e($q) ?>" placeholder="نام، کد ملی یا کد کاربری"></label>
    <label style="margin:0">ناحیه<select name="area"><option value="">همه</option>
      <?php foreach (Areas::options() as $code => $label): ?>
        <option value="<?= e((string) $code) ?>" <?= $area === (string) $code ? 'selected' : '' ?>><?= e($label) ?></option>
      <?php endforeach; ?></select></label>
    <label style="margin:0">نقش<select name="kind"><option value="">همه</option>
      <?php foreach (Persons::GROUPS as $title => $kinds): ?>
        <optgroup label="<?= e($title) ?>">
          <?php foreach ($kinds as $k): ?>
            <option value="<?= e($k) ?>" <?= $kind === $k ? 'selected' : '' ?>><?= e(Persons::kindName($k)) ?></option>
          <?php endforeach; ?>
        </optgroup>
      <?php endforeach; ?></select></label>
    <label class="check" style="margin:0"><input type="checkbox" name="inactive" value="1" <?= $inactive ? 'checked' : '' ?>> غیرفعال‌ها هم</label>
    <button type="submit">نمایش</button>
  </form>
</div>

<?php if ($list['areas'] === [] && $list['province'] === [] && $list['idle'] === []): ?>
  <div class="card"><p class="muted">کسی با این شرط‌ها پیدا نشد.</p></div>
<?php endif; ?>

<?php foreach ($list['areas'] as $block): ?>
<div class="card">
  <h3><?= e($block['name']) ?> <span class="muted">— <?= fa_digits((string) $block['count']) ?> کاربری</span></h3>
  <?php foreach ($block['groups'] as $group): ?>
    <h4 style="margin:12px 0 4px"><?= e($group['title']) ?></h4>
    <table>
      <tr><th>نام</th><th>نقش</th><th>کد کاربری</th><th>کد ملی</th><th>وضعیت</th></tr>
      <?php foreach ($group['rows'] as $row): ?>
        <tr>
          <td><?php if ($row['person_id'] !== null): ?><a href="person.php?id=<?= e($row['person_id']) ?>"><?= e($row['name']) ?></a><?php else: ?><?= e($row['name']) ?><?php endif; ?></td>
          <td><?= e($row['kind_name']) ?><?= str_starts_with((string) $row['kind'], 'crew') || $row['kind'] === 'field'
              ? ' <span class="muted">(' . e(permission_label($row['permissions'])) . ')</span>' : '' ?></td>
          <td dir="ltr" style="text-align:right"><code><?= e($row['user_code']) ?></code></td>
          <td dir="ltr" style="text-align:right"><?= e((string) ($row['national_id'] ?? '—')) ?></td>
          <td><?= $row['active'] ? '<span class="tag tag-answered">فعال</span>' : '<span class="tag tag-overdue">غیرفعال</span>' ?></td>
        </tr>
      <?php endforeach; ?>
    </table>
  <?php endforeach; ?>
</div>
<?php endforeach; ?>

<?php if ($list['province'] !== []): ?>
<div class="card">
  <h3>کاربری‌های استانی</h3>
  <table>
    <tr><th>نام</th><th>نقش</th><th>کد کاربری</th><th>وضعیت</th></tr>
    <?php foreach ($list['province'] as $row): ?>
      <tr>
        <td><?php if ($row['person_id'] !== null): ?><a href="person.php?id=<?= e($row['person_id']) ?>"><?= e($row['name']) ?></a><?php else: ?><?= e($row['name']) ?><?php endif; ?></td>
        <td><?= e($row['kind_name']) ?></td>
        <td dir="ltr" style="text-align:right"><code><?= e($row['user_code']) ?></code></td>
        <td><?= $row['active'] ? '<span class="tag tag-answered">فعال</span>' : '<span class="tag tag-overdue">غیرفعال</span>' ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
</div>
<?php endif; ?>

<?php if ($list['idle'] !== []): ?>
<div class="card">
  <h3>اشخاص بدون کاربری فعال</h3>
  <table>
    <tr><th>نام</th><th>کد ملی</th></tr>
    <?php foreach ($list['idle'] as $row): ?>
      <tr><td><a href="person.php?id=<?= e($row['person_id']) ?>"><?= e($row['full_name']) ?></a></td>
        <td dir="ltr" style="text-align:right"><?= e((string) ($row['national_id'] ?? '—')) ?></td></tr>
    <?php endforeach; ?>
  </table>
</div>
<?php endif; ?>
<?php portal_footer();
