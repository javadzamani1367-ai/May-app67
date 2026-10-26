<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// تنظیمات آمار جامع: دسترسی مجری رمزارز، ستون‌هایی که هر واحد می‌بیند و پر می‌کند، ستون‌های
// اکسل و Word، و قفل سال‌ها. فقط مدیر.
$user = admin_require([Auth::ROLE_MANAGER]);
CenterSettings::require($user, true);
$saved = false;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    center_csrf_check();
    if (isset($_POST['year'])) {
        $year = (int) Jalali::latin((string) $_POST['year']);
        $lock = ($_POST['lock'] ?? '') === '1';
        CenterLocks::set($year, $lock, $user);
        CenterEdit::log('year:' . $year, '*', null, (string) $year, $lock ? 'lock' : 'unlock', $user);
    } else {
        $units = [];
        foreach (array_keys(UnitStages::NAMES) as $unit) {
            $units[$unit] = array_map('strval', (array) ($_POST['units'][$unit] ?? []));
        }
        CenterSettings::save(($_POST['executor'] ?? '') === '1', $units, (array) ($_POST['excel'] ?? []), (array) ($_POST['word'] ?? []));
    }
    $saved = true;
}
$unitFields = CenterSettings::unitFields();
$excel = array_flip(CenterSettings::columns('xlsx'));
$word = array_flip(CenterSettings::columns('docx'));
$editable = array_flip(CenterColumns::editableKeys());

admin_header('تنظیمات آمار جامع');
echo center_styles();
?>
<div class="card">
  <h2>ستون‌ها و دسترسی‌ها</h2>
  <?php if ($saved): ?><p style="color:#15803d">ذخیره شد.</p><?php endif; ?>
  <form method="post"><?= center_csrf_field() ?>
    <label class="check"><input type="checkbox" name="executor" value="1" style="width:auto" <?= CenterSettings::executorAllowed() ? 'checked' : '' ?>>
      مجری رمزارز هر ناحیه آمار جامع ناحیه‌های خودش را ببیند و ویرایش کند (بی دستورالعمل، تنظیمات، قفل سال و ورود فایل)</label>
    <p class="muted">پیش‌فرض، مبالغ و ستون‌های دستی را فقط مدیر می‌نویسد. هر ستونی که برای یک واحد تیک بزنید، کاربران همان واحد در پرتال
      واحدها سطرهای ناحیه‌شان را با همان ستون‌ها می‌بینند و پر می‌کنند.</p>
    <div class="cscroll"><table class="ctable">
      <tr><th>ستون</th><th>اکسل</th><th>Word</th><?php foreach (UnitStages::NAMES as $name): ?><th><?= e($name) ?></th><?php endforeach; ?></tr>
      <?php foreach (CenterColumns::all() as $key => $c): ?>
      <tr><td><?= e(CenterColumns::label($key)) ?><?= $c['cols'] === null ? ' <span class="srcs">(اضافه)</span>' : '' ?></td>
        <td><input type="checkbox" name="excel[]" value="<?= e($key) ?>" style="width:auto" <?= isset($excel[$key]) ? 'checked' : '' ?>></td>
        <td><input type="checkbox" name="word[]" value="<?= e($key) ?>" style="width:auto" <?= isset($word[$key]) ? 'checked' : '' ?>></td>
        <?php foreach (array_keys(UnitStages::NAMES) as $unit): ?>
        <td><?php if (isset($editable[$key])): ?><input type="checkbox" name="units[<?= $unit ?>][]" value="<?= e($key) ?>" style="width:auto"
          <?= in_array($key, $unitFields[$unit], true) ? 'checked' : '' ?>><?php endif; ?></td>
        <?php endforeach; ?></tr>
      <?php endforeach; ?>
    </table></div>
    <button type="submit">ذخیره</button>
  </form>
</div>

<div class="card">
  <h2>قفل سال</h2>
  <p class="muted">سطرهای سال قفل‌شده ویرایش، حذف یا با فایل تازه عوض نمی‌شوند؛ برای وقتی که آمار سال برای توانیر رفته است.</p>
  <table class="ctable"><tr><th>سال</th><th>سطر</th><th>وضعیت</th><th></th></tr>
    <?php foreach (CenterLocks::overview() as $y): ?>
    <tr><td><?= fa_digits((string) $y['year']) ?></td><td><?= fa_digits((string) $y['rows']) ?></td><td><?= $y['locked'] ? 'قفل' : 'باز' ?></td>
      <td><form method="post"><?= center_csrf_field() ?><input type="hidden" name="year" value="<?= $y['year'] ?>">
        <input type="hidden" name="lock" value="<?= $y['locked'] ? '0' : '1' ?>">
        <button type="submit" style="margin:0;padding:4px 14px;<?= $y['locked'] ? 'background:#475569' : 'background:#b45309' ?>"><?= $y['locked'] ? 'باز کردن' : 'قفل کردن' ?></button></form></td></tr>
    <?php endforeach; ?>
  </table>
</div>
<?php portal_footer();
