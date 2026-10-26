<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// ورود فایل اکسل آمار (سال‌های پیش یا فایل جاری): بارگذاری، پیش‌نمایش برگه‌ها و ستون‌های
// پیداشده و سطرهای جورشده یا تازه و خانه‌های ناسازگار، بعد اجرا. فقط مدیر.
$user = admin_require([Auth::ROLE_MANAGER]);
$access = CenterSettings::require($user, true);
$plan = null;
$token = null;
$done = null;
$error = null;
$year = (int) Jalali::latin((string) ($_POST['year'] ?? FieldCodes::jalaliYear(Db::now())));

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    center_csrf_check();
    $action = (string) ($_POST['action'] ?? '');
    try {
        if ($action === 'upload') {
            $file = $_FILES['file'] ?? null;
            if (!is_array($file) || ($file['error'] ?? 1) !== UPLOAD_ERR_OK || !is_uploaded_file((string) $file['tmp_name'])) {
                throw new RuntimeException('فایل نرسید.');
            }
            $token = CenterImportRun::stash((string) $file['tmp_name'], $user, $year);
        } else {
            $token = (string) ($_POST['token'] ?? '');
        }
        $opened = CenterImportRun::open($token, $user);
        if ($opened === null) {
            throw new RuntimeException('پیش‌نمایش منقضی شده؛ فایل را دوباره بارگذاری کنید.');
        }
        $sheets = isset($_POST['sheets']) ? array_map('strval', (array) $_POST['sheets']) : null;
        $plan = CenterImportRun::plan($opened[0], $year, $action === 'upload' ? null : ($sheets ?? []));
        if ($action === 'apply') {
            $done = CenterImportRun::apply($plan, $user);
            CenterImportRun::discard($token);
            $plan = null;
        }
    } catch (RuntimeException $e) {
        $error = $e->getMessage();
    } catch (Throwable $e) {
        $error = 'این فایل اکسل (xlsx) خوانده نشد.';
    }
}

$statusNames = ['match' => 'جور با سطر موجود', 'new' => 'سطر تازه', 'repeat' => 'تکراری در برگه دیگر', 'locked' => 'سال قفل'];
admin_header('ورود فایل آمار');
echo center_styles();
?>
<div class="card">
  <h2>ورود فایل اکسل آمار جامع</h2>
  <p class="muted">سرستون، ستون‌ها و سطرها خودکار پیدا می‌شوند، حتی اگر ترتیب ستون‌ها فرق کند. هر سطر با شماره اشتراک، شناسه قبض، شماره
    پرونده یا کد سامانه پیامکی به سطر موجود همان سال وصل می‌شود. مقدار فایل جای مقدار سامانه را نمی‌گیرد؛ اگر فرق داشت خانه
    «ناسازگار» می‌شود تا در صفحه سطر انتخاب کنید. برگه «جدول اطلاعات تکمیلی» هم شناخته و وارد می‌شود.</p>
  <?php if ($error !== null): ?><p class="warn"><?= e($error) ?></p><?php endif; ?>
  <?php if ($done !== null): ?><p style="color:#15803d">انجام شد: <?= fa_digits((string) $done['updated']) ?> سطر به‌روز، <?= fa_digits((string) $done['created']) ?>
    سطر تازه، <?= fa_digits((string) $done['supplement']) ?> سطر جدول تکمیلی. <a href="centers.php">فهرست آمار</a></p><?php endif; ?>
  <form method="post" enctype="multipart/form-data"><?= center_csrf_field() ?><input type="hidden" name="action" value="upload">
    <div class="grid"><label>فایل اکسل (xlsx)<input type="file" name="file" accept=".xlsx" required></label>
      <label>سال سطرهایی که تاریخ ندارند<input name="year" value="<?= e(fa_digits((string) $year)) ?>"></label></div>
    <button type="submit">بارگذاری و پیش‌نمایش</button></form>
</div>

<?php if ($plan !== null): $s = $plan['summary']; ?>
<div class="card">
  <h2>پیش‌نمایش</h2>
  <div class="kpis">
    <div class="kpi"><b><?= fa_digits((string) $s['match']) ?></b>جور با سطر موجود</div>
    <div class="kpi"><b><?= fa_digits((string) $s['new']) ?></b>سطر تازه</div>
    <div class="kpi"><b><?= fa_digits((string) $s['conflicts']) ?></b>خانه ناسازگار</div>
    <div class="kpi"><b><?= fa_digits((string) $s['repeat']) ?></b>تکراری در برگه دیگر</div>
    <div class="kpi"><b><?= fa_digits((string) $s['locked']) ?></b>در سال قفل (وارد نمی‌شود)</div>
    <div class="kpi"><b><?= fa_digits((string) $s['supplement']) ?></b>سطر جدول تکمیلی</div>
  </div>
  <form method="post"><?= center_csrf_field() ?><input type="hidden" name="token" value="<?= e($token) ?>">
    <input type="hidden" name="year" value="<?= e((string) $year) ?>">
    <h3 style="margin-top:14px">برگه‌ها</h3>
    <?php foreach ($plan['sheets'] as $sheet): if ($sheet['kind'] === 'none') { continue; } ?>
      <label class="check"><input type="checkbox" name="sheets[]" value="<?= e($sheet['name']) ?>" style="width:auto" <?= $sheet['selected'] ? 'checked' : '' ?>>
        «<?= e($sheet['name']) ?>» — <?= $sheet['kind'] === 'supplement' ? 'جدول اطلاعات تکمیلی' : fa_digits((string) count($sheet['columns'])) . ' ستون' ?>،
        <?= fa_digits((string) $sheet['rows']) ?> سطر</label>
      <?php if ($sheet['missing'] !== [] && $sheet['kind'] === 'main'): ?><p class="srcs">ستون‌های فرم که در این برگه نبود: <?= e(implode('، ', $sheet['missing'])) ?></p><?php endif; ?>
      <?php if ($sheet['unknown'] !== []): ?><p class="srcs">ستون‌های ناشناخته (وارد نمی‌شوند): <?= e(implode('، ', $sheet['unknown'])) ?></p><?php endif; ?>
    <?php endforeach; ?>
    <button type="submit" name="action" value="preview" style="background:#475569">پیش‌نمایش دوباره با همین برگه‌ها</button>
    <button type="submit" name="action" value="apply">ورود</button>
  </form>
  <div class="cscroll" style="margin-top:12px"><table class="ctable">
    <tr><th>برگه</th><th>سطر</th><th>سال</th><th>اشتراک</th><th>مشترک</th><th>ماینر</th><th>نتیجه</th><th>ناسازگار</th></tr>
    <?php foreach (array_slice($plan['rows'], 0, 400) as $r): ?>
    <tr><td><?= e($r['sheet']) ?></td><td><?= fa_digits((string) $r['line']) ?></td><td><?= fa_digits((string) $r['year']) ?></td>
      <td><?= e(fa_digits((string) ($r['values']['subscription'] ?? ''))) ?></td><td><?= e((string) ($r['values']['subscriber'] ?? '')) ?></td>
      <td><?= e(fa_digits((string) ($r['values']['miners'] ?? ''))) ?></td>
      <td><?= e($statusNames[$r['status']] ?? $r['status']) ?><?= $r['repeat_of'] ? ' («' . e($r['repeat_of']) . '»)' : '' ?></td>
      <td><?= e(implode('، ', array_map(static fn ($c) => $c['label'] . ': سامانه ' . $c['system'] . ' / فایل ' . $c['file'], $r['conflicts']))) ?></td></tr>
    <?php endforeach; ?>
  </table></div>
</div>
<?php endif; ?>
<?php portal_footer();
