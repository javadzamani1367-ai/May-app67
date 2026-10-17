<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$manager = admin_require();

// ساخت یک‌جای کاربرهای ناحیه‌ها: برای هر ناحیه انتخاب‌شده یک مجری رمزارز و
// واحدهای مقصد. کدی که از قبل هست دست نمی‌خورد. رمزها فقط همین یک بار، روی
// همین صفحه، نشان داده می‌شوند؛ جایی ذخیره نمی‌شوند که بشود دوباره دیدشان.
$kinds = [UserBatch::EXECUTOR => 'مجری رمزارز'] + array_combine(UnitStages::SLUGS, UnitStages::NAMES);
$made = null;
$selectedAreas = array_keys(Areas::NAMES);
$selectedKinds = array_keys($kinds);
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    $selectedAreas = Areas::parse((array) ($_POST['areas'] ?? []));
    $selectedKinds = array_values(array_intersect(array_keys($kinds), (array) ($_POST['kinds'] ?? [])));
    if (($_POST['action'] ?? '') === 'create') {
        $made = UserBatch::create(UserBatch::plan($selectedAreas, $selectedKinds), (string) $manager['id']);
    }
}
$plan = UserBatch::plan($selectedAreas, $selectedKinds);

admin_header('کاربرهای ناحیه‌ها');
?>
<?php if ($made !== null): ?>
<div class="card" id="made">
  <h2>کاربرهای ساخته‌شده (<?= fa_digits((string) count($made)) ?>)</h2>
  <?php if ($made === []): ?><p class="muted">همه این کاربرها از قبل ساخته شده بودند.</p><?php else: ?>
  <p class="error">رمزها فقط همین حالا دیده می‌شوند. همین صفحه را چاپ کنید (یا به PDF ذخیره کنید) و به هر کاربر فقط برگه خودش را بدهید.
    رمز هر کدام را بعداً در صفحه همان کاربر می‌شود عوض کرد.</p>
  <table>
    <tr><th>نام</th><th>کد کاربری</th><th>رمز</th></tr>
    <?php foreach ($made as $row): ?>
      <tr><td><?= e($row['full_name']) ?></td><td dir="ltr" style="text-align:right"><?= e($row['user_code']) ?></td>
        <td dir="ltr" style="text-align:right;font-family:monospace;font-size:1.1em"><?= e($row['password']) ?></td></tr>
    <?php endforeach; ?>
  </table>
  <p><button type="button" onclick="window.print()">چاپ</button></p>
  <?php endif; ?>
</div>
<?php endif; ?>

<div class="card">
  <h2>ساخت کاربرهای ناحیه‌ها</h2>
  <p class="muted">برای هر ناحیه: مجری رمزارز (اختیارات مدیر فقط در همان ناحیه، بی ساخت کاربر) و چهار واحد مقصد که فقط پرونده‌های
    همان ناحیه را در پرتال می‌بینند. کد کاربری قالب ثابت دارد، مثل <span dir="ltr">401-legal</span>؛ رمز تصادفی است.</p>
  <form method="post">
    <?= csrf_field() ?>
    <h3>ناحیه‌ها</h3>
    <div style="columns:3">
      <?php foreach (Areas::options() as $code => $label): ?>
        <label class="check" style="font-weight:normal"><input type="checkbox" name="areas[]" value="<?= e((string) $code) ?>"
          <?= in_array((string) $code, $selectedAreas, true) ? 'checked' : '' ?>> <?= e($label) ?></label>
      <?php endforeach; ?>
    </div>
    <h3>کاربرها</h3>
    <?php foreach ($kinds as $key => $label): ?>
      <label class="check" style="font-weight:normal"><input type="checkbox" name="kinds[]" value="<?= e($key) ?>"
        <?= in_array($key, $selectedKinds, true) ? 'checked' : '' ?>> <?= e($label) ?></label>
    <?php endforeach; ?>
    <div class="actions-bar">
      <button type="submit" name="action" value="preview" style="background:#64748b">پیش‌نمایش</button>
      <button type="submit" name="action" value="create">ساختن کاربرهای تازه</button>
    </div>
  </form>
</div>

<div class="card">
  <h3>پیش‌نمایش (<?= fa_digits((string) count($plan)) ?> کاربر، <?= fa_digits((string) count(array_filter($plan, static fn ($p) => !$p['exists']))) ?> تازه)</h3>
  <table>
    <tr><th>کد کاربری</th><th>نام</th><th>وضعیت</th></tr>
    <?php foreach ($plan as $row): ?>
      <tr><td dir="ltr" style="text-align:right"><?= e($row['user_code']) ?></td><td><?= e($row['full_name']) ?></td>
        <td><?= $row['exists'] ? '<span class="tag tag-sent">از قبل هست</span>' : '<span class="tag tag-answered">ساخته می‌شود</span>' ?></td></tr>
    <?php endforeach; ?>
  </table>
</div>
<?php portal_footer();
