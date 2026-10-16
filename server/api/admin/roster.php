<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// اعضای اکیپ و فیدرهای فشار متوسط: اپ میدانی از همین فهرست انتخاب می‌کند تا
// هر نفر و هر فیدر یک نام داشته باشد و گزارش عملکرد درست بشمارد. حذف نمی‌شوند،
// غیرفعال می‌شوند: موردهای قدیمی هنوز به آن‌ها اشاره دارند.
$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    $id = (string) ($_POST['id'] ?? '');
    $id = $id === '' ? null : $id;
    $active = isset($_POST['active']) || $id === null;
    $error = ($_POST['what'] ?? '') === 'feeder'
        ? FieldRoster::saveFeeder($id, (string) ($_POST['name'] ?? ''), (string) ($_POST['county'] ?? ''), $active)
        : FieldRoster::saveMember($id, (string) ($_POST['name'] ?? ''), (int) ($_POST['role'] ?? -1),
            (string) ($_POST['county'] ?? ''), $active);
    if ($error === null) {
        header('Location: roster.php?saved=1#' . (($_POST['what'] ?? '') === 'feeder' ? 'feeders' : 'crew'));
        exit;
    }
}
$members = FieldRoster::members();
$feeders = FieldRoster::feeders();
$edit = static fn (array $row): bool => ($_GET['edit'] ?? '') === $row['id'];

admin_header('اکیپ و فیدرها');
?>
<div class="card" id="crew">
  <h2>اعضای اکیپ</h2>
  <p class="muted">مسئول اکیپ، سیم‌بان و راننده. اپ میدانی پیش از شروع آمپرگیری تابلو یا تیربه‌تیر، مسئول و سیم‌بان را از
    این فهرست می‌خواهد (راننده اختیاری). گوشی‌ها فهرست را در همگام‌سازی بعدی می‌گیرند.</p>
  <?php if (isset($_GET['saved'])): ?><p class="notice">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post" class="row" style="justify-content:flex-start;gap:10px;flex-wrap:wrap">
    <?= csrf_field() ?><input type="hidden" name="what" value="member">
    <label style="margin:0">نام و نام خانوادگی <input name="name" required></label>
    <label style="margin:0">نقش <select name="role">
      <?php foreach (FieldRoster::ROLE_NAMES as $v => $n): ?><option value="<?= $v ?>"><?= e($n) ?></option><?php endforeach; ?>
    </select></label>
    <label style="margin:0">شهرستان <input name="county" style="width:140px"></label>
    <button type="submit">افزودن</button>
  </form>
  <?php if ($members !== []): ?>
  <table>
    <tr><th>نام</th><th>نقش</th><th>شهرستان</th><th>وضعیت</th><th></th></tr>
    <?php foreach ($members as $m): ?>
      <?php if ($edit($m)): ?>
      <tr><td colspan="5">
        <form method="post" class="row" style="justify-content:flex-start;gap:10px;flex-wrap:wrap">
          <?= csrf_field() ?><input type="hidden" name="what" value="member"><input type="hidden" name="id" value="<?= e($m['id']) ?>">
          <input name="name" value="<?= e($m['full_name']) ?>" required>
          <select name="role"><?php foreach (FieldRoster::ROLE_NAMES as $v => $n): ?>
            <option value="<?= $v ?>" <?= (int) $m['role'] === $v ? 'selected' : '' ?>><?= e($n) ?></option><?php endforeach; ?></select>
          <input name="county" value="<?= e((string) $m['county']) ?>" style="width:140px">
          <label class="check" style="margin:0"><input type="checkbox" name="active" <?= (int) $m['active'] === 1 ? 'checked' : '' ?>> فعال</label>
          <button type="submit">ذخیره</button> <a href="roster.php#crew">انصراف</a>
        </form>
      </td></tr>
      <?php else: ?>
      <tr>
        <td><?= e($m['full_name']) ?></td>
        <td><?= e(FieldRoster::ROLE_NAMES[(int) $m['role']] ?? '—') ?></td>
        <td><?= e((string) $m['county']) ?></td>
        <td><?= (int) $m['active'] === 1 ? '<span class="tag tag-answered">فعال</span>' : '<span class="tag tag-overdue">غیرفعال</span>' ?></td>
        <td><a href="roster.php?edit=<?= e(urlencode($m['id'])) ?>#crew">ویرایش</a></td>
      </tr>
      <?php endif; ?>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>

<div class="card" id="feeders">
  <h2>فیدرهای فشار متوسط</h2>
  <p class="muted">نام فیدر ۲۰ کیلوولت در ترموویژن، آمپرگیری تابلو و تیربه‌تیر از این فهرست انتخاب می‌شود (اختیاری؛ اپ گزینه
    «سایر» هم دارد).</p>
  <form method="post" class="row" style="justify-content:flex-start;gap:10px;flex-wrap:wrap">
    <?= csrf_field() ?><input type="hidden" name="what" value="feeder">
    <label style="margin:0">نام فیدر <input name="name" required></label>
    <label style="margin:0">شهرستان <input name="county" style="width:140px"></label>
    <button type="submit">افزودن</button>
  </form>
  <?php if ($feeders !== []): ?>
  <table>
    <tr><th>فیدر</th><th>شهرستان</th><th>وضعیت</th><th></th></tr>
    <?php foreach ($feeders as $f): ?>
      <?php if ($edit($f)): ?>
      <tr><td colspan="4">
        <form method="post" class="row" style="justify-content:flex-start;gap:10px;flex-wrap:wrap">
          <?= csrf_field() ?><input type="hidden" name="what" value="feeder"><input type="hidden" name="id" value="<?= e($f['id']) ?>">
          <input name="name" value="<?= e($f['name']) ?>" required>
          <input name="county" value="<?= e((string) $f['county']) ?>" style="width:140px">
          <label class="check" style="margin:0"><input type="checkbox" name="active" <?= (int) $f['active'] === 1 ? 'checked' : '' ?>> فعال</label>
          <button type="submit">ذخیره</button> <a href="roster.php#feeders">انصراف</a>
        </form>
      </td></tr>
      <?php else: ?>
      <tr>
        <td><?= e($f['name']) ?></td>
        <td><?= e((string) $f['county']) ?></td>
        <td><?= (int) $f['active'] === 1 ? '<span class="tag tag-answered">فعال</span>' : '<span class="tag tag-overdue">غیرفعال</span>' ?></td>
        <td><a href="roster.php?edit=<?= e(urlencode($f['id'])) ?>#feeders">ویرایش</a></td>
      </tr>
      <?php endif; ?>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>
<?php portal_footer();
