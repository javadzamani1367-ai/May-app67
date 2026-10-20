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
        $current = FieldSettings::all();
        foreach (['refer_days', 'expert_days', 'executor_days', 'illegal_county_days', 'illegal_hq_days'] as $key) {
            $days[$key] = isset($_POST[$key]) ? (int) Jalali::latin((string) $_POST[$key]) : (int) $current[$key];
        }
        if (min($days) < 1 || max($days) > 30) {
            $error = 'هر مهلت را بین ۱ تا ۳۰ روز کاری بنویسید.';
        } else {
            foreach ($days as $key => $value) {
                FieldSettings::set($key, $value);
            }
        }
    } elseif (($_POST['action'] ?? '') === 'illegal_experts') {
        foreach (Areas::options() as $code => $label) {
            $chosen = (string) ($_POST['expert'][(string) $code] ?? '');
            $valid = $chosen === '' || in_array($chosen, array_column(Scope::usersIn(Auth::ROLE_EXPERT, [(string) $code]), 'id'), true);
            if ($valid) {
                IllegalFlow::setDefaultExpert((string) $code, $chosen === '' ? null : $chosen);
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
      <label>برق غیرمجاز: مهلت مجری شهرستان — برای ارجاع، تأیید و ارسال؛ پس از آن به ستاد هشدار می‌رود و ستاد جای او اقدام می‌کند
        <input name="illegal_county_days" type="number" min="1" max="30" value="<?= (int) $s['illegal_county_days'] ?>" dir="ltr"></label>
      <label>برق غیرمجاز: مهلت ستاد — اگر ستاد هم ارجاع ندهد، پس از این مدت خودکار به کارشناس همان شهرستان می‌رود
        <input name="illegal_hq_days" type="number" min="1" max="30" value="<?= (int) $s['illegal_hq_days'] ?>" dir="ltr"></label>
    </div>
    <button type="submit">ذخیره مهلت‌ها</button>
  </form>
  <p class="muted">اگر هاست شما «Cron Jobs» دارد، این دستور را هر ۱۵ دقیقه اجرا کنید تا ارجاع خودکار و هشدارها دقیق سر وقت انجام شوند:
    <code dir="ltr">php <?= e(dirname(__DIR__)) ?>/cron.php</code> — بی آن هم با هر باز شدن پنل و اپ‌ها انجام می‌شوند.</p>
</div>

<div class="card">
  <h2>کارشناس پیش‌فرض برق غیرمجاز هر شهرستان</h2>
  <p class="muted">ارجاع خودکار پرونده غیرمجاز (وقتی نه مجری شهرستان و نه ستاد ارجاع داد) به این کارشناس می‌رود؛ اگر تعیین نشده،
    به کارشناسی از همان شهرستان که کار باز کمتری دارد.</p>
  <form method="post">
    <?= csrf_field() ?><input type="hidden" name="action" value="illegal_experts">
    <div class="grid">
      <?php foreach (Areas::options() as $code => $label): $experts = Scope::usersIn(Auth::ROLE_EXPERT, [(string) $code]);
          $chosen = Db::one('SELECT v FROM field_settings WHERE k = ?', ['illegal_expert:' . $code])['v'] ?? ''; ?>
        <label><?= e($label) ?><select name="expert[<?= e((string) $code) ?>]"><option value="">کمترین کار باز</option>
          <?php foreach ($experts as $x): ?><option value="<?= e($x['id']) ?>" <?= $chosen === $x['id'] ? 'selected' : '' ?>><?= e($x['name']) ?></option><?php endforeach; ?>
        </select></label>
      <?php endforeach; ?>
    </div>
    <button type="submit">ذخیره</button>
  </form>
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
