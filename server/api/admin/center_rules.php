<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// دستورالعمل‌های پاداش: هر نامه (شماره، شناسه و تاریخ نامه و تاریخ اجرا) هر چهار پاداش را با هم تعیین
// می‌کند و از تاریخ اجرایش تا دستورالعمل بعدی حساب می‌شود. فقط مدیر.
$user = admin_require([Auth::ROLE_MANAGER]);
CenterSettings::require($user, true);
$error = null;
$saved = false;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    center_csrf_check();
    if (($_POST['action'] ?? '') === 'delete') {
        RewardRules::delete((string) ($_POST['id'] ?? ''));
        $saved = true;
    } else {
        $result = RewardRules::save($_POST + ['sections' => (array) ($_POST['s'] ?? [])], $user, ((string) ($_POST['id'] ?? '')) ?: null);
        $saved = Field::isUuid($result);
        $error = $saved ? null : $result;
    }
}
$editing = isset($_GET['edit']) ? RewardRules::find((string) $_GET['edit']) : null;
// دستورالعمل تازه بخش‌ها را از تازه‌ترین دستورالعمل پیشین برمی‌دارد تا فقط عددهای تغییرکرده نوشته شوند.
$base = $editing ?? (RewardRules::list()[0] ?? null);
$sec = static fn (int $kind) => $base['sections'][$kind] ?? [];
$date = static fn (?int $m) => $m === null ? '' : Jalali::format($m, false);
$val = static fn (array $a, string $k, string $d = '') => e((string) ($a[$k] ?? $d));

admin_header('دستورالعمل‌های پاداش');
echo center_styles();
?>
<div class="card">
  <h2>دستورالعمل‌های پاداش</h2>
  <p class="muted">هر دستورالعمل یک نامه است که هر چهار پاداش را تعیین می‌کند و برای مرکزهایی که تاریخ گزارششان از تاریخ اجرای آن تا
    اجرای دستورالعمل بعدی است حساب می‌شود. پاداش عامل شناسایی و سهم نیروی انتظامی را شرکت توانیر می‌پردازد؛ پاداش جمع‌آوری، سهم
    سپاه و پاداش عوامل اداری را شرکت توزیع. افراد و سهم هر نفر در فرم پاداش هر مرکز (صفحه سطر آمار) تعیین و ثبت نهایی می‌شود.</p>
  <?php if ($saved): ?><p style="color:#15803d">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="warn"><?= e($error) ?></p><?php endif; ?>
  <?php $list = RewardRules::list(); if ($list === []): ?><p class="muted">ثبت نشده.</p><?php endif; ?>
  <?php foreach ($list as $r): ?>
    <p><b><?= e(RewardRules::describe($r)) ?></b> — <?= e(implode('، ', array_map(static fn ($k) => RewardRules::NAMES[$k], array_keys($r['sections'])))) ?>
      <?= $r['note'] ? ' — ' . e($r['note']) : '' ?> · <a href="?edit=<?= e($r['id']) ?>">ویرایش</a>
      <form method="post" style="display:inline" onsubmit="return confirm('حذف شود؟')"><?= center_csrf_field() ?>
        <input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= e($r['id']) ?>">
        <button type="submit" style="background:#b91c1c;padding:2px 10px;margin:0">حذف</button></form></p>
  <?php endforeach; ?>
  <p><a href="center_rules.php">+ دستورالعمل تازه</a></p>
</div>

<form method="post"><?= center_csrf_field() ?><input type="hidden" name="id" value="<?= e($editing['id'] ?? '') ?>">
<div class="card">
  <h2><?= $editing ? 'ویرایش دستورالعمل' : 'دستورالعمل تازه' ?></h2>
  <div class="grid">
    <label>شماره نامه<input name="letter_no" value="<?= e($editing['letter_no'] ?? '') ?>"></label>
    <label>شناسه نامه<input name="letter_id" value="<?= e($editing['letter_id'] ?? '') ?>"></label>
    <label>تاریخ نامه<input name="letter_date" placeholder="۱۴۰۴/۰۱/۰۱" value="<?= e($date($editing['letter_date'] ?? null)) ?>"></label>
    <label>اجرا از تاریخ<input name="effective_from" required placeholder="۱۴۰۴/۰۱/۰۱" value="<?= e($date($editing['effective_from'] ?? null)) ?>"></label>
    <label>توضیح<input name="note" value="<?= e($editing['note'] ?? '') ?>"></label>
  </div>
  <p class="srcs">بخشی که خالی بماند از دستورالعمل پیشین می‌آید.<?= $editing === null && $base !== null ? ' عددهای این فرم از تازه‌ترین دستورالعمل پر شده‌اند.' : '' ?></p>
</div>

<?php $f = $sec(RewardRules::FINDER); ?>
<div class="card">
  <h2>۱. <?= e(RewardRules::NAMES[RewardRules::FINDER]) ?> <span class="muted">(پلکانی؛ پرداخت شرکت توانیر)</span></h2>
  <div class="grid">
    <label>روش پلکان<select name="s[finder][mode]"><?php foreach (RewardRules::MODES as $m => $label): ?><option value="<?= $m ?>" <?= ($f['mode'] ?? 'progressive') === $m ? 'selected' : '' ?>><?= e($label) ?></option><?php endforeach; ?></select></label>
    <label>سقف هر مرکز (ریال)<input name="s[finder][cap]" dir="ltr" value="<?= $val($f, 'cap') ?>"></label>
  </div>
  <table class="ctable" style="margin-top:8px"><tr><th>پله</th><th>از دستگاه</th><th>تا دستگاه (خالی: بی‌انتها)</th><th>مبلغ هر دستگاه (ریال)</th></tr>
    <?php for ($i = 0; $i < 6; $i++): $t = $f['tiers'][$i] ?? []; ?>
    <tr><td><?= fa_digits((string) ($i + 1)) ?></td><td><input name="s[finder][tiers][<?= $i ?>][from]" dir="ltr" value="<?= $val($t, 'from') ?>"></td>
      <td><input name="s[finder][tiers][<?= $i ?>][to]" dir="ltr" value="<?= $val($t, 'to') ?>"></td>
      <td><input name="s[finder][tiers][<?= $i ?>][amount]" dir="ltr" value="<?= $val($t, 'amount') ?>"></td></tr>
    <?php endfor; ?></table>
</div>

<?php foreach ([RewardRules::COLLECTION => ['۲', 'پرداخت شرکت توزیع؛ میان پرسنل جمع‌آوری برابر'],
    RewardRules::ENFORCEMENT => ['۳', 'نیروی انتظامی: توانیر، سپاه: شرکت توزیع؛ میان همه پرسنل حاضر برابر']] as $kind => [$n, $hint]):
    $s = $sec($kind); $key = RewardRules::KEYS[$kind]; ?>
<div class="card">
  <h2><?= $n ?>. <?= e(RewardRules::NAMES[$kind]) ?> <span class="muted">(<?= e($hint) ?>)</span></h2>
  <?php if (!isset($s['per_device']) && isset($s['tiers'])): ?><p class="warn">این بخش در دستورالعمل پیشین پلکانی بود؛ مبلغ هر دستگاه را بنویسید.</p><?php endif; ?>
  <div class="grid">
    <label>مبلغ هر دستگاه (ریال)<input name="s[<?= $key ?>][per_device]" dir="ltr" value="<?= $val($s, 'per_device') ?>"></label>
    <label>شمار دستگاه<select name="s[<?= $key ?>][count]"><?php foreach (RewardRules::COUNTS as $c => $label): ?><option value="<?= $c ?>" <?= ($s['count'] ?? ($kind === RewardRules::COLLECTION ? 'collected' : 'found')) === $c ? 'selected' : '' ?>><?= e($label) ?></option><?php endforeach; ?></select></label>
    <label>سقف هر مرکز (ریال)<input name="s[<?= $key ?>][cap]" dir="ltr" value="<?= $val($s, 'cap') ?>"></label>
  </div>
  <?php if ($kind === RewardRules::ENFORCEMENT): ?>
    <?php foreach (RewardRules::ENFORCERS as $o => $label): ?><label class="check"><input type="checkbox" name="s[enforcement][orgs][]" value="<?= $o ?>" style="width:auto"
      <?= in_array($o, $s['orgs'] ?? array_keys(RewardRules::ENFORCERS), true) ? 'checked' : '' ?>> <?= e($label) ?></label><?php endforeach; ?>
    <p class="srcs">یک نفر سپاه و چهار نفر انتظامی یعنی هر نفر یک پنجم مبلغ مرکز. کارکنان شرکت توزیع (از جمله مأموران وصول) از پاداش جمع‌آوری سهم می‌برند.</p>
  <?php else: ?>
    <p class="srcs">«جمع‌آوری‌شده» یعنی جمع ستون‌های تعداد جمع‌آوری‌شده دارای و فاقد انشعاب؛ اگر هنوز نوشته نشده باشد تعداد کشف‌شده حساب می‌شود.</p>
  <?php endif; ?>
</div>
<?php endforeach; ?>

<?php $st = $sec(RewardRules::STAFF); ?>
<div class="card">
  <h2>۴. <?= e(RewardRules::NAMES[RewardRules::STAFF]) ?> <span class="muted">(درصدی؛ پرداخت شرکت توزیع)</span></h2>
  <div class="grid">
    <label>درصد کل از مبنا<input name="s[staff][pct]" dir="ltr" value="<?= $val($st, 'pct') ?>"></label>
    <label>سهم مدیرعامل (درصد)<input name="s[staff][ceo_pct]" dir="ltr" value="<?= $val($st, 'ceo_pct') ?>"></label>
    <label>سهم کارکنان (درصد)<input name="s[staff][staff_pct]" dir="ltr" value="<?= $val($st, 'staff_pct') ?>"></label>
    <label>مبنا<select name="s[staff][base]"><?php foreach (RewardRules::BASES as $b => $label): ?><option value="<?= $b ?>" <?= ($st['base'] ?? 'energy') === $b ? 'selected' : '' ?>><?= e($label) ?></option><?php endforeach; ?></select></label>
    <label>سقف هر مرکز (ریال، اختیاری)<input name="s[staff][cap]" dir="ltr" value="<?= $val($st, 'cap') ?>"></label>
  </div>
  <h3 style="margin-top:12px">درصد پیش‌فرض هر پست از سهم کارکنان (جمعاً ۱۰۰)</h3>
  <p class="srcs">دارندگان هر پست را کارشناس در اپ پیشنهاد می‌کند و مدیر در فرم هر مرکز تأیید می‌کند؛ مدیر درصد هر نفر را پیش از ثبت نهایی همان فرم تغییر می‌دهد.</p>
  <table class="ctable"><tr><th>پست سازمانی</th><th>درصد</th></tr>
    <?php $roles = $st['roles'] ?? array_map(static fn ($l) => ['label' => $l, 'weight' => ''], RewardSpec::STAFF_ROLES);
    for ($i = 0; $i < max(12, count($roles) + 2); $i++): $r = $roles[$i] ?? ['label' => '', 'weight' => '']; ?>
    <tr><td><input name="s[staff][roles][<?= $i ?>][label]" value="<?= e($r['label']) ?>"></td>
      <td><input name="s[staff][roles][<?= $i ?>][weight]" dir="ltr" value="<?= e((string) $r['weight']) ?>"></td></tr>
    <?php endfor; ?></table>
</div>
<div class="card"><button type="submit">ذخیره دستورالعمل</button></div>
</form>
<?php portal_footer();
