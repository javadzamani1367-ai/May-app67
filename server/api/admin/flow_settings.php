<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// مهلت‌های گردش پرونده (به روز کاری) و مراحل اقدام هر واحد مقصد.
$saved = isset($_GET['saved']);
$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    if (($_POST['action'] ?? '') === 'days') {
        $days = [];
        foreach (['refer_days', 'expert_days', 'executor_days'] as $key) {
            $days[$key] = (int) Jalali::latin((string) ($_POST[$key] ?? ''));
        }
        if (min($days) < 1 || max($days) > 30) {
            $error = 'هر مهلت را بین ۱ تا ۳۰ روز کاری بنویسید.';
        } else {
            foreach ($days as $key => $value) {
                FieldSettings::set($key, $value);
            }
        }
    } else {
        $unit = (int) ($_POST['unit'] ?? -1);
        $error = UnitStages::save($unit, (string) ($_POST['stages'] ?? ''));
    }
    if ($error === null) {
        header('Location: flow_settings.php?saved=1');
        exit;
    }
}
$s = FieldSettings::all();

admin_header('گردش پرونده و واحدها');
?>
<div class="card">
  <h2>مهلت‌های گردش پرونده</h2>
  <?php if ($saved): ?><p class="notice">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <p class="muted">به روز کاری: شنبه تا چهارشنبه، بی جمعه و تعطیلاتی که در صفحه «تعطیلات» آمده. پنجشنبه شمرده نمی‌شود.</p>
  <form method="post">
    <?= csrf_field() ?><input type="hidden" name="action" value="days">
    <div class="grid">
      <label>مهلت ارجاع مدیر — گزارشی که مدیر ثبت کرد و به کسی نسپرد، پس از این مدت خودکار به مجری ناحیه می‌رسد
        <input name="refer_days" type="number" min="1" max="30" value="<?= (int) $s['refer_days'] ?>" dir="ltr"></label>
      <label>مهلت بازدید کارشناس — پس از آن به مجری ناحیه هشدار می‌رود
        <input name="expert_days" type="number" min="1" max="30" value="<?= (int) $s['expert_days'] ?>" dir="ltr"></label>
      <label>مهلت مجری — اگر پس از هشدار (یا پس از سپرده شدن پرونده) اقدامی نکند، به مدیر هشدار می‌رود
        <input name="executor_days" type="number" min="1" max="30" value="<?= (int) $s['executor_days'] ?>" dir="ltr"></label>
    </div>
    <button type="submit">ذخیره مهلت‌ها</button>
  </form>
  <p class="muted">اگر هاست شما «Cron Jobs» دارد، این دستور را هر ۱۵ دقیقه اجرا کنید تا ارجاع خودکار و هشدارها دقیق سر وقت انجام شوند:
    <code dir="ltr">php <?= e(dirname(__DIR__)) ?>/cron.php</code> — بی آن هم با هر باز شدن پنل و اپ‌ها انجام می‌شوند.</p>
</div>

<div class="card">
  <h2>مراحل اقدام واحدهای مقصد</h2>
  <p class="muted">واحد در هر پاسخ می‌گوید کار به کدام مرحله رسیده. هر سطر یک مرحله؛ آخرین مرحله یعنی پرونده در آن واحد بسته شد.
    امتیاز واحدها از پیشرفت همین مراحل هم حساب می‌شود.</p>
  <div class="section-grid">
    <?php foreach (UnitStages::all() as $unit => $stages): ?>
      <form method="post">
        <?= csrf_field() ?><input type="hidden" name="action" value="stages"><input type="hidden" name="unit" value="<?= $unit ?>">
        <label><?= e(UnitStages::NAMES[$unit]) ?><textarea name="stages" rows="7"><?= e(implode("\n", $stages)) ?></textarea></label>
        <button type="submit">ذخیره</button>
      </form>
    <?php endforeach; ?>
  </div>
</div>
<?php portal_footer();
