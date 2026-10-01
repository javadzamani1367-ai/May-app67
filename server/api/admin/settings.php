<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();
$saved = false;
$error = null;

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    $pct = (int) ($_POST['amp_tolerance_pct'] ?? -1);
    $min = (int) ($_POST['amp_tolerance_min_a'] ?? -1);
    if ($pct < 1 || $pct > 50 || $min < 0 || $min > 100) {
        $error = 'درصد باید بین ۱ و ۵۰ و حداقل مطلق بین ۰ و ۱۰۰ آمپر باشد.';
    } else {
        FieldSettings::set('amp_tolerance_pct', $pct);
        FieldSettings::set('amp_tolerance_min_a', $min);
        FieldSettings::set('purge_after_sync', isset($_POST['purge_after_sync']) ? 1 : 0);
        FieldSettings::set('show_reporter_to_team', isset($_POST['show_reporter_to_team']) ? 1 : 0);
        FieldSettings::set('reporter_result_detail', max(0, min(2, (int) ($_POST['reporter_result_detail'] ?? 1))));
        $saved = true;
    }
}
$s = FieldSettings::all();

admin_header('تنظیمات میدانی');
?>
<div class="card">
  <h2>تنظیمات اپ بازرسی میدانی</h2>
  <p class="muted">گوشی‌ها این تنظیمات را در همگام‌سازی بعدی می‌گیرند؛ نسخه تازه اپ لازم نیست.</p>
  <?php if ($saved): ?><p class="notice">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post">
    <?= csrf_field() ?>
    <h3>کنترل مجموع آمپر فیدرها</h3>
    <p class="muted">اختلاف مجموع فیدرهای هر فاز با کلید کل تا این حد پذیرفته است؛ هر کدام بزرگ‌تر بود.</p>
    <div class="grid">
      <label>درصد مجاز اختلاف
        <input name="amp_tolerance_pct" type="number" min="1" max="50" value="<?= (int) $s['amp_tolerance_pct'] ?>" dir="ltr">
      </label>
      <label>حداقل اختلاف مجاز (آمپر)
        <input name="amp_tolerance_min_a" type="number" min="0" max="100" value="<?= (int) $s['amp_tolerance_min_a'] ?>" dir="ltr">
      </label>
    </div>

    <h3 style="margin-top:16px">داده روی گوشی</h3>
    <label class="check"><input type="checkbox" name="purge_after_sync" <?= $s['purge_after_sync'] === 1 ? 'checked' : '' ?>>
      بعد از رسیدن کامل به سرور، تصاویر و فایل‌ها از گوشی پاک شوند</label>

    <h3 style="margin-top:16px">ارجاع و نتیجه</h3>
    <label class="check"><input type="checkbox" name="show_reporter_to_team" <?= $s['show_reporter_to_team'] === 1 ? 'checked' : '' ?>>
      اکیپ نام گزارش‌دهنده را ببیند (در غیر این صورت فقط «گزارش همکار»)</label>
    <label>آنچه گزارش‌دهنده از نتیجه می‌بیند
      <select name="reporter_result_detail">
        <option value="0" <?= $s['reporter_result_detail'] === 0 ? 'selected' : '' ?>>هیچ — فقط وضعیت</option>
        <option value="1" <?= $s['reporter_result_detail'] === 1 ? 'selected' : '' ?>>نتیجه (تأیید شد یا نشد)</option>
        <option value="2" <?= $s['reporter_result_detail'] === 2 ? 'selected' : '' ?>>نتیجه با جزئیات</option>
      </select>
    </label>
    <button type="submit">ذخیره</button>
  </form>
</div>
<?php portal_footer();
