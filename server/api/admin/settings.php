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
    $current = FieldSettings::all();
    $presence = (int) ($_POST['presence_limit_m'] ?? $current['presence_limit_m']);
    $single = (int) ($_POST['abnormal_single_a'] ?? $current['abnormal_single_a']);
    $three = (int) ($_POST['abnormal_three_a'] ?? $current['abnormal_three_a']);
    $risePct = (int) ($_POST['history_rise_pct'] ?? $current['history_rise_pct']);
    $riseMin = (int) ($_POST['history_rise_min_a'] ?? $current['history_rise_min_a']);
    // «۷:۳۰» یا «07:30» به دقیقه از نیمه‌شب؛ نیامده، همان مقدار فعلی.
    $minutes = static function (string $key, int $fallback): ?int {
        if (!isset($_POST[$key])) {
            return $fallback;
        }
        return preg_match('/^(\d{1,2}):(\d{2})$/', Jalali::latin(trim((string) $_POST[$key])), $m) === 1
            && (int) $m[1] < 24 && (int) $m[2] < 60 ? (int) $m[1] * 60 + (int) $m[2] : null;
    };
    $officeStart = $minutes('office_start', $current['office_start_min']);
    $officeEnd = $minutes('office_end', $current['office_end_min']);
    if ($pct < 1 || $pct > 50 || $min < 0 || $min > 100) {
        $error = 'درصد باید بین ۱ و ۵۰ و حداقل مطلق بین ۰ و ۱۰۰ آمپر باشد.';
    } elseif ($presence < 20 || $presence > 2000) {
        $error = 'فاصله مجاز حضور باید بین ۲۰ و ۲۰۰۰ متر باشد.';
    } elseif ($single < 1 || $single > 500 || $three < 1 || $three > 500) {
        $error = 'آمپر نامتعارف باید بین ۱ و ۵۰۰ آمپر باشد.';
    } elseif ($risePct < 5 || $risePct > 500 || $riseMin < 0 || $riseMin > 200) {
        $error = 'درصد افزایش نامتعارف باید بین ۵ و ۵۰۰ و کف آن بین ۰ و ۲۰۰ آمپر باشد.';
    } elseif ($officeStart === null || $officeEnd === null || $officeEnd <= $officeStart) {
        $error = 'ساعت اداری را مثل ۰۷:۰۰ تا ۱۴:۳۰ بنویسید؛ پایان بعد از شروع باشد.';
    } else {
        FieldSettings::set('office_start_min', $officeStart);
        FieldSettings::set('office_end_min', $officeEnd);
        FieldSettings::set('abnormal_single_a', $single);
        FieldSettings::set('abnormal_three_a', $three);
        FieldSettings::set('presence_limit_m', $presence);
        FieldSettings::set('history_rise_pct', $risePct);
        FieldSettings::set('history_rise_min_a', $riseMin);
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
  <p><a href="flow_settings.php">مهلت‌های گردش پرونده و مراحل اقدام واحدها</a> — <a href="holidays.php">تعطیلات</a> —
    <a href="areas.php">کاربرهای ناحیه‌ها</a></p>
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

    <h3 style="margin-top:16px">حضور در محل</h3>
    <label>بیشترین فاصله کاربر از تابلو یا تیری که موقعیتش را ثبت می‌کند (متر)
      <input name="presence_limit_m" type="number" min="20" max="2000" value="<?= (int) $s['presence_limit_m'] ?>" dir="ltr">
    </label>
    <p class="muted">اگر موقعیت دقیق ماهواره‌ای کاربر دورتر از این فاصله باشد، اپ اجازه ثبت نمی‌دهد و می‌گوید در محل حاضر شود.</p>

    <h3 style="margin-top:16px">مشترکین با آمپر نامتعارف (آمپرگیری تیربه‌تیر)</h3>
    <div class="grid">
      <label>مشترک تکفاز: بالاتر از (آمپر)
        <input name="abnormal_single_a" type="number" min="1" max="500" value="<?= (int) $s['abnormal_single_a'] ?>" dir="ltr">
      </label>
      <label>مشترک سه‌فاز: هر فاز بالاتر از (آمپر)
        <input name="abnormal_three_a" type="number" min="1" max="500" value="<?= (int) $s['abnormal_three_a'] ?>" dir="ltr">
      </label>
    </div>
    <p class="muted">اپ آمپر بالاتر از این حد را سرخ و «نامتعارف» نشان می‌دهد.</p>

    <h3 style="margin-top:16px">سوابق بازرسی: افزایش نامتعارف آمپر</h3>
    <div class="grid">
      <label>افزایش هر فاز بیش از (درصد) نسبت به بار قبل یا میانگین پنج بار قبل
        <input name="history_rise_pct" type="number" min="5" max="500" value="<?= (int) $s['history_rise_pct'] ?>" dir="ltr">
      </label>
      <label>و دست‌کم (آمپر)
        <input name="history_rise_min_a" type="number" min="0" max="200" value="<?= (int) $s['history_rise_min_a'] ?>" dir="ltr">
      </label>
    </div>
    <p class="muted">خوانش نامتعارف در کادر مقایسه اپ میدانی و صفحه سوابق رنگی می‌شود و به مدیر و مجری رمزارز همان ناحیه هشدار می‌دهد.</p>

    <h3 style="margin-top:16px">ساعت اداری (عملکرد اکیپ)</h3>
    <?php $hm = static fn (int $m): string => sprintf('%02d:%02d', intdiv($m, 60), $m % 60); ?>
    <div class="grid">
      <label>شروع کار روزهای شنبه تا چهارشنبه
        <input name="office_start" value="<?= e($hm((int) $s['office_start_min'])) ?>" placeholder="07:00" dir="ltr">
      </label>
      <label>پایان کار
        <input name="office_end" value="<?= e($hm((int) $s['office_end_min'])) ?>" placeholder="14:30" dir="ltr">
      </label>
    </div>
    <p class="muted">کار بیرون از این ساعت، و همه کار پنجشنبه، جمعه و تعطیلات رسمی، اضافه‌کار حساب می‌شود.
      تعطیلات قمری هر سال را در <a href="holidays.php">تعطیلات</a> بنویسید.</p>

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
    <p class="muted">در صفحه هر مورد در اپ میدانی دیده می‌شود. «جزئیات» یعنی توضیح کارشناس و، برای رمزارز، شمار
      دستگاه و توان کشف‌شده؛ نام کارشناس هرگز نشان داده نمی‌شود. دلیل رد یا بازدید دوباره در هر حال به گزارش‌دهنده می‌رسد.</p>
    <button type="submit">ذخیره</button>
  </form>
</div>
<?php portal_footer();
