<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$manager = admin_require();

// «درخواست کاربری» از اپ‌ها: نام، کد ملی، تلفن، توضیح و کد همان نصب. کد ملی‌ای که
// ثبت شده، درخواست را به همان شخص وصل می‌کند و «ثبت» فقط کد نصب را برای او می‌نویسد
// (نصب دوباره)؛ شخص تازه ساخته می‌شود و به صفحه‌اش می‌رویم تا نقش‌هایش تیک بخورد.
$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    $result = DeviceRequests::decide((string) ($_POST['id'] ?? ''), (string) ($_POST['action'] ?? ''),
        ($person = Persons::byNationalId((string) ($_POST['person_national'] ?? ''))) === null ? null : (string) $person['id'],
        $manager, ['full_name' => (string) ($_POST['full_name'] ?? ''), 'national_id' => (string) ($_POST['national_id'] ?? '')]);
    if (isset($result['error'])) {
        $error = $result['error'];
    } elseif (isset($result['person_id'])) {
        header('Location: person.php?id=' . urlencode($result['person_id']) . '&done=device');
        exit;
    } else {
        header('Location: requests.php?rejected=1');
        exit;
    }
}
$requests = DeviceRequests::pending();

admin_header('درخواست‌های کاربری');
?>
<div class="card">
  <div class="row">
    <h2>درخواست‌های کاربری (<?= fa_digits((string) count($requests)) ?>)</h2>
    <a href="persons.php"><button type="button" style="background:#64748b">اشخاص و کاربری‌ها</button></a>
  </div>
  <p class="muted">هر اپ در اولین اجرا کد نصب خودش را نشان می‌دهد و از همان‌جا درخواست می‌فرستد. کد ملی معیار شخص است: اگر
    شخص از قبل ثبت شده، «ثبت نصب» فقط کد همین اپ را برای او می‌نویسد.</p>
  <?php if (isset($_GET['rejected'])): ?><p class="notice">درخواست رد شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
</div>
<?php foreach ($requests as $req): ?>
<div class="card">
  <div class="row">
    <div>
      <h3><?= e($req['full_name'] ?: '—') ?> <span class="muted">— <?= e($req['app_name']) ?></span></h3>
      <p class="muted">کد ملی: <span dir="ltr"><?= e((string) ($req['national_id'] ?? 'نفرستاده')) ?></span>
        — تلفن: <span dir="ltr"><?= e((string) $req['phone']) ?></span>
        — کد نصب: <code dir="ltr"><?= e(implode('-', str_split((string) $req['device_code'], 4))) ?></code>
        — <?= e(Jalali::format((int) $req['created_at'])) ?></p>
      <?php if ($req['note']): ?><p><?= nl2br(e((string) $req['note'])) ?></p><?php endif; ?>
    </div>
  </div>
  <?php if ($req['person_name'] !== null): ?>
    <p class="notice">این کد ملی برای «<?= e((string) $req['person_name']) ?>» (شماره <?= fa_digits((string) $req['person_number']) ?>) ثبت شده است.</p>
    <form method="post" style="display:inline"><?= csrf_field() ?>
      <input type="hidden" name="id" value="<?= e($req['id']) ?>"><input type="hidden" name="action" value="register">
      <button type="submit">ثبت این نصب برای همین شخص</button></form>
  <?php else: ?>
    <form method="post" class="row" style="justify-content:flex-start;gap:8px"><?= csrf_field() ?>
      <input type="hidden" name="id" value="<?= e($req['id']) ?>"><input type="hidden" name="action" value="register">
      <label style="margin:0">نام<input name="full_name" value="<?= e((string) $req['full_name']) ?>"></label>
      <label style="margin:0">کد ملی<input name="national_id" value="<?= e((string) ($req['national_id'] ?? '')) ?>" dir="ltr"></label>
      <button type="submit">ساخت شخص و ثبت نصب</button></form>
    <form method="post" class="row" style="justify-content:flex-start;gap:8px"><?= csrf_field() ?>
      <input type="hidden" name="id" value="<?= e($req['id']) ?>"><input type="hidden" name="action" value="register">
      <label style="margin:0">یا برای شخص ثبت‌شده با کد ملی<input name="person_national" dir="ltr"></label>
      <button type="submit" style="background:#7c3aed">ثبت نصب</button></form>
  <?php endif; ?>
  <form method="post" style="display:inline" onsubmit="return confirm('این درخواست رد شود؟')"><?= csrf_field() ?>
    <input type="hidden" name="id" value="<?= e($req['id']) ?>"><input type="hidden" name="action" value="reject">
    <button type="submit" style="background:#b91c1c">رد</button></form>
</div>
<?php endforeach; ?>
<?php portal_footer();
