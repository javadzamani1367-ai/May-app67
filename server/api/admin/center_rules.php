<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// دستورالعمل‌های پاداش: هر کدام با شماره، شناسه و تاریخ نامه و تاریخ اجرا. دستورالعمل تازه
// از تاریخ اجرایش به بعد حساب می‌شود. فقط مدیر.
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
        $kind = (int) ($_POST['kind'] ?? 0);
        $spec = $kind === RewardRules::STAFF
            ? ['pct' => $_POST['pct'] ?? '', 'ceo_pct' => $_POST['ceo_pct'] ?? '', 'staff_pct' => $_POST['staff_pct'] ?? '',
                'base' => $_POST['base'] ?? 'energy', 'cap' => $_POST['cap'] ?? '', 'roles' => (array) ($_POST['roles'] ?? [])]
            : ['mode' => $_POST['mode'] ?? 'progressive', 'tiers' => (array) ($_POST['tiers'] ?? []), 'cap' => $_POST['cap'] ?? '',
                'orgs' => (array) ($_POST['orgs'] ?? [])];
        $result = RewardRules::save($_POST + ['spec' => $spec], $user, ((string) ($_POST['id'] ?? '')) ?: null);
        $saved = Field::isUuid($result);
        $error = $saved ? null : $result;
    }
}
$editing = isset($_GET['edit']) ? RewardRules::find((string) $_GET['edit']) : null;
$kind = (int) ($_GET['kind'] ?? $editing['kind'] ?? 0);
$spec = $editing['spec'] ?? [];
$date = static fn (?int $m) => $m === null ? '' : Jalali::format($m, false);

admin_header('دستورالعمل‌های پاداش');
echo center_styles();
?>
<div class="card">
  <h2>دستورالعمل‌های پاداش</h2>
  <p class="muted">هر پاداش سقف هر مرکز دارد. پاداش عامل شناسایی و عوامل اجرایی (نیروی انتظامی و سپاه) را توانیر می‌پردازد ولی اینجا حساب و گزارش
    می‌شود؛ تعداد دستگاه هر مرکز و حاضران بازدید از اپ کارشناس و پس از تأیید مدیر می‌آیند. پاداش عوامل اداری درصدی از مبلغ صورتحساب پرونده است.</p>
  <?php if ($saved): ?><p style="color:#15803d">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="warn"><?= e($error) ?></p><?php endif; ?>
  <?php foreach (RewardRules::NAMES as $k => $name): ?>
    <h3 style="margin-top:14px"><?= e($name) ?> <a href="?kind=<?= $k ?>" style="font-size:.8em">+ دستورالعمل تازه</a></h3>
    <?php $list = RewardRules::list($k); if ($list === []): ?><p class="muted">ثبت نشده.</p><?php endif; ?>
    <?php foreach ($list as $r): ?>
      <p><?= e(RewardRules::describe($r)) ?><?= $r['note'] ? ' — ' . e($r['note']) : '' ?> · <a href="?edit=<?= e($r['id']) ?>">ویرایش</a>
        <form method="post" style="display:inline" onsubmit="return confirm('حذف شود؟')"><?= center_csrf_field() ?>
          <input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= e($r['id']) ?>">
          <button type="submit" style="background:#b91c1c;padding:2px 10px;margin:0">حذف</button></form></p>
    <?php endforeach; ?>
  <?php endforeach; ?>
</div>

<div class="card">
  <h2><?= $editing ? 'ویرایش دستورالعمل' : 'دستورالعمل تازه' ?> — <?= e(RewardRules::NAMES[$kind] ?? '') ?></h2>
  <form method="post"><?= center_csrf_field() ?><input type="hidden" name="kind" value="<?= $kind ?>">
    <input type="hidden" name="id" value="<?= e($editing['id'] ?? '') ?>">
    <div class="grid">
      <label>شماره نامه<input name="letter_no" value="<?= e($editing['letter_no'] ?? '') ?>"></label>
      <label>شناسه نامه<input name="letter_id" value="<?= e($editing['letter_id'] ?? '') ?>"></label>
      <label>تاریخ نامه<input name="letter_date" placeholder="۱۴۰۴/۰۱/۰۱" value="<?= e($date($editing['letter_date'] ?? null)) ?>"></label>
      <label>اجرا از تاریخ<input name="effective_from" required placeholder="۱۴۰۴/۰۱/۰۱" value="<?= e($date($editing['effective_from'] ?? null)) ?>"></label>
      <label>سقف هر مرکز (ریال، خالی یعنی بی‌سقف)<input name="cap" dir="ltr" value="<?= e((string) ($spec['cap'] ?? '')) ?>"></label>
      <label>توضیح<input name="note" value="<?= e($editing['note'] ?? '') ?>"></label>
    </div>
    <?php if ($kind !== RewardRules::STAFF): ?>
      <label>روش پلکان<select name="mode"><?php foreach (RewardRules::MODES as $m => $label): ?><option value="<?= $m ?>" <?= ($spec['mode'] ?? 'progressive') === $m ? 'selected' : '' ?>><?= e($label) ?></option><?php endforeach; ?></select></label>
      <table class="ctable" style="margin-top:8px"><tr><th>پله</th><th>از دستگاه</th><th>تا دستگاه (خالی: بی‌انتها)</th><th>مبلغ (ریال)</th></tr>
        <?php for ($i = 0; $i < 6; $i++): $t = $spec['tiers'][$i] ?? []; ?>
        <tr><td><?= fa_digits((string) ($i + 1)) ?></td><td><input name="tiers[<?= $i ?>][from]" dir="ltr" value="<?= e((string) ($t['from'] ?? '')) ?>"></td>
          <td><input name="tiers[<?= $i ?>][to]" dir="ltr" value="<?= e((string) ($t['to'] ?? '')) ?>"></td>
          <td><input name="tiers[<?= $i ?>][amount]" dir="ltr" value="<?= e((string) ($t['amount'] ?? '')) ?>"></td></tr>
        <?php endfor; ?></table>
      <?php if ($kind === RewardRules::ENFORCEMENT): ?>
        <h3 style="margin-top:12px">سازمان‌های اجرایی</h3>
        <?php foreach (RewardRules::ENFORCERS as $o => $label): ?><label class="check"><input type="checkbox" name="orgs[]" value="<?= $o ?>" style="width:auto"
          <?= in_array($o, $spec['orgs'] ?? array_keys(RewardRules::ENFORCERS), true) ? 'checked' : '' ?>> <?= e($label) ?></label><?php endforeach; ?>
        <p class="srcs">مبلغ مرکز اگر هر دو سازمان حاضر بودند میان دو سازمان برابر، و سهم هر سازمان میان حاضران همان سازمان
          برابر تقسیم می‌شود. حاضر «پلیس امنیت» یا با نام نهاد نیروی انتظامی و فراجا، و «سپاه» در نام نهاد شناخته می‌شوند؛ کارکنان
          شرکت توزیع (از جمله مأموران وصول) پاداش اجرایی ندارند.</p>
      <?php endif; ?>
    <?php else: ?>
      <div class="grid">
        <label>درصد کل از مبنا<input name="pct" dir="ltr" value="<?= e((string) ($spec['pct'] ?? '1.5')) ?>"></label>
        <label>سهم مدیرعامل (درصد)<input name="ceo_pct" dir="ltr" value="<?= e((string) ($spec['ceo_pct'] ?? '0.5')) ?>"></label>
        <label>سهم کارکنان (درصد)<input name="staff_pct" dir="ltr" value="<?= e((string) ($spec['staff_pct'] ?? '1')) ?>"></label>
        <label>مبنا<select name="base"><?php foreach (RewardRules::BASES as $b => $label): ?><option value="<?= $b ?>" <?= ($spec['base'] ?? 'energy') === $b ? 'selected' : '' ?>><?= e($label) ?></option><?php endforeach; ?></select></label>
      </div>
      <h3 style="margin-top:12px">نقش‌ها (وزن‌ها جمعاً ۱۰۰ درصد سهم کارکنان)</h3>
      <table class="ctable"><tr><th>نقش</th><th>وزن (درصد)</th><th>دارندگان (هر نام یک سطر)</th></tr>
        <?php $roles = $spec['roles'] ?? array_map(static fn ($l) => ['label' => $l, 'weight' => '', 'people' => []], RewardSpec::STAFF_ROLES);
        for ($i = 0; $i < max(12, count($roles) + 2); $i++): $r = $roles[$i] ?? ['label' => '', 'weight' => '', 'people' => []]; ?>
        <tr><td><input name="roles[<?= $i ?>][label]" value="<?= e($r['label']) ?>"></td>
          <td><input name="roles[<?= $i ?>][weight]" dir="ltr" value="<?= e((string) $r['weight']) ?>"></td>
          <td><textarea name="roles[<?= $i ?>][people]" rows="2"><?= e(implode("\n", $r['people'])) ?></textarea></td></tr>
        <?php endfor; ?></table>
    <?php endif; ?>
    <button type="submit">ذخیره دستورالعمل</button>
  </form>
</div>
<?php portal_footer();
