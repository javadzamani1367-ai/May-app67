<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// فرم پاداش یک مرکز: افراد با تیک، سازمان، اقدام و درصد؛ ذخیره، ثبت نهایی با شماره، باز کردن، و فرم
// تک‌صفحه‌ای اکسل و Word برای مکاتبه. افراد پیش‌فرض از بازدید و پیشنهاد کارشناس می‌آیند. فقط مدیر.
$user = admin_require([Auth::ROLE_MANAGER]);
$access = CenterSettings::require($user, true);
$id = (string) ($_GET['row'] ?? '');
$kind = array_search((string) ($_GET['kind'] ?? ''), RewardRules::KEYS, true);
$row = CenterRows::one($access, $id);
if ($row === null || $kind === false) {
    http_response_code(404);
    exit('این فرم پیدا نشد.');
}
if (in_array($_GET['format'] ?? '', ['xlsx', 'docx'], true)) {
    RewardFormModel::send($row, $kind, (string) $_GET['format']);
    exit;
}
$error = null;
$saved = false;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    center_csrf_check();
    $action = (string) ($_POST['action'] ?? 'save');
    if ($action !== 'unlock' && isset($_POST['p'])) {
        $error = RewardForms::save($row, $kind, (array) $_POST['p'], $user);
        $row = CenterRows::one($access, $id) ?? $row;
    }
    $error ??= match ($action) {
        'final' => RewardForms::finalize($row, $kind, $user),
        'unlock' => RewardForms::unlock($row, $kind, $user),
        default => null,
    };
    $saved = $error === null;
    $row = CenterRows::one($access, $id) ?? $row;
}
$state = $row['rewards'][$kind];
$calc = $state['calc'];
$final = $state['final_at'] !== null;
$editable = $kind !== RewardRules::FINDER && !$final && !$row['locked'];
$shares = array_column($calc['people'] ?? [], 'share', 'key');
$model = CenterRewardReport::forApp(RewardFormModel::build($row, $kind));
$v = $row['values'];
$names = RewardPeople::names();
$posts = $kind === RewardRules::STAFF ? array_unique(array_merge([RewardPeople::CEO],
    array_column($calc['rule']['spec']['roles'] ?? [], 'label'), RewardSpec::STAFF_ROLES)) : [];
$orgs = match ($kind) {
    RewardRules::ENFORCEMENT => array_intersect_key(RewardPeople::ORGS, RewardRules::ENFORCERS),
    RewardRules::COLLECTION => ['company' => 'شرکت توزیع', 'other' => 'سایر'],
    default => [],
};
$file = static fn (string $f) => 'center_form.php?row=' . urlencode($id) . '&kind=' . RewardRules::KEYS[$kind] . '&format=' . $f;

admin_header(RewardRules::NAMES[$kind]);
echo center_styles();
?>
<div class="card">
  <div class="row"><div><h2><?= e(RewardRules::NAMES[$kind]) ?> — <?= e(($v['subscriber'] ?? '') ?: ($v['subscription'] ?? '') ?: 'مرکز') ?></h2>
    <p class="muted"><?= e(fa_digits((string) ($v['tracking_code'] ?? ''))) ?> · <?= $final ? 'ثبت نهایی شماره ' . e(fa_digits((string) $state['number']))
      . ' در ' . e(Jalali::format((int) $state['final_at'], false)) : 'پیش‌نویس' ?></p></div>
    <div><a href="<?= e($file('xlsx')) ?>"><button type="button" style="background:#15803d;margin:0">اکسل</button></a>
      <a href="<?= e($file('docx')) ?>"><button type="button" style="background:#1d4ed8;margin:0">Word</button></a>
      <a href="center_row.php?id=<?= e(urlencode($id)) ?>">بازگشت به سطر</a></div></div>
  <?php if ($saved): ?><p style="color:#15803d">انجام شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="warn"><?= e($error) ?></p><?php endif; ?>
  <?php foreach ($model['paragraphs'] as $text): ?><p class="srcs"><?= e($text) ?></p><?php endforeach; ?>
</div>

<?php if ($kind === RewardRules::FINDER): ?>
<div class="card"><p>نام، ارگان و شبای عامل شناسایی از ستون‌های آمار همین مرکز است: عامل شناسایی <b><?= e(($v['finder_name'] ?? '') ?: '—') ?></b>،
  مبلغ <b><?= e(fa_digits(number_format((float) ($calc['amount'] ?? 0)))) ?></b> ریال. برای تغییر، <a href="center_row.php?id=<?= e(urlencode($id)) ?>">سطر آمار</a> را ویرایش کنید.</p>
<?php else: ?>
<form method="post"><?= center_csrf_field() ?>
<div class="card">
  <h2>افراد و سهم هر نفر</h2>
  <p class="srcs">فقط افراد تیک‌خورده سهم می‌برند و در فرم می‌آیند. <?= match ($kind) {
      RewardRules::COLLECTION => 'مبلغ مرکز میان همه تیک‌خورده‌ها برابر تقسیم می‌شود.',
      RewardRules::ENFORCEMENT => 'مبلغ مرکز میان همه پرسنل تیک‌خورده نیروی انتظامی و سپاه برابر تقسیم می‌شود، نه میان دو سازمان.',
      default => 'سهم مدیرعامل جداست؛ درصد بقیه از سهم کارکنان است و باید جمعاً ۱۰۰ باشد.',
  } ?> نام را از فهرست برگزینید؛ افرادی که کارشناس پیشنهاد کرده یا در بازدید حاضر بوده‌اند خودکار آمده‌اند.</p>
  <datalist id="names"><?php foreach ($names as $n): ?><option value="<?= e($n) ?>"><?php endforeach; ?></datalist>
  <datalist id="posts"><?php foreach ($posts as $n): ?><option value="<?= e($n) ?>"><?php endforeach; ?></datalist>
  <div class="cscroll"><table class="ctable">
    <tr><th>سهم می‌برد</th><th>نام و نام خانوادگی</th><th>سمت / پست</th><?php if ($orgs): ?><th>سازمان</th><?php endif; ?><th>اقدام انجام‌شده</th>
      <?php if ($kind === RewardRules::STAFF): ?><th>درصد</th><?php endif; ?><th>منبع</th><th>سهم (ریال)</th></tr>
    <?php $list = $state['people'];
    if ($editable) {
        for ($k = 0; $k < 3; $k++) {
            $list[] = ['key' => '', 'name' => '', 'post' => '', 'org' => $kind === RewardRules::ENFORCEMENT ? 'faraja' : 'company', 'action' => '',
                'pct' => 0, 'source' => 'manager', 'on' => true, 'blank' => true];
        }
    }
    foreach ($list as $i => $p): $ro = $editable ? '' : ' disabled'; $ceo = ($p['key'] ?? '') === 'ceo'; ?>
    <tr<?= !empty($p['new']) ? ' style="background:#fef3c7"' : '' ?>>
      <td><input type="hidden" name="p[<?= $i ?>][key]" value="<?= e($p['key']) ?>"><input type="hidden" name="p[<?= $i ?>][source]" value="<?= e($p['source']) ?>">
        <input type="hidden" name="p[<?= $i ?>][on]" value="0"><input type="checkbox" name="p[<?= $i ?>][on]" value="1" style="width:auto"<?= $p['on'] ? ' checked' : '' ?><?= $ro ?>></td>
      <td><input name="p[<?= $i ?>][name]" list="names" value="<?= e($p['name']) ?>"<?= $ro ?> placeholder="<?= !empty($p['blank']) ? 'فرد تازه' : 'برگزینید' ?>"></td>
      <td><input name="p[<?= $i ?>][post]" list="posts" value="<?= e($p['post']) ?>"<?= $ro ?><?= $ceo ? ' readonly' : '' ?>></td>
      <?php if ($orgs): ?><td><select name="p[<?= $i ?>][org]"<?= $ro ?>><?php foreach ($orgs as $o => $label): ?><option value="<?= $o ?>" <?= $p['org'] === $o ? 'selected' : '' ?>><?= e($label) ?></option><?php endforeach; ?></select></td><?php endif; ?>
      <td><input name="p[<?= $i ?>][action]" value="<?= e($p['action'] ?? '') ?>"<?= $ro ?>></td>
      <?php if ($kind === RewardRules::STAFF): ?><td><?= $ceo ? 'سهم مدیرعامل' : '<input name="p[' . $i . '][pct]" dir="ltr" style="width:70px" value="' . e((string) (float) ($p['pct'] ?? 0)) . '"' . $ro . '>' ?></td><?php endif; ?>
      <td class="srcs"><?= e(!empty($p['new']) ? 'تازه — ' : '') ?><?= e(RewardPeople::SOURCES[$p['source']] ?? '') ?></td>
      <td><?= $p['on'] && empty($p['blank']) ? e(fa_digits(number_format((float) ($shares[$p['key']] ?? 0)))) : '—' ?></td></tr>
    <?php endforeach; ?>
  </table></div>
  <?php if ($kind === RewardRules::STAFF && $calc !== null): ?><p class="srcs">جمع درصد کارکنان: <b><?= e(fa_digits((string) $calc['pct_sum'])) ?></b> از ۱۰۰</p><?php endif; ?>
  <?php if ($editable): ?>
    <button type="submit" name="action" value="save">ذخیره</button>
    <button type="submit" name="action" value="final" style="background:#15803d" onclick="return confirm('فرم ثبت نهایی و شماره‌گذاری شود؟ پس از آن تغییر نمی‌کند.')">ذخیره و ثبت نهایی</button>
  <?php endif; ?>
</div>
</form>
<?php endif; ?>
<?php if ($final): ?>
<form method="post" class="card"><?= center_csrf_field() ?><input type="hidden" name="action" value="unlock">
  <p class="srcs">فرم ثبت نهایی شده و محاسبه‌اش ثابت است. باز کردن فرم شماره‌اش را نگه می‌دارد.</p>
  <button type="submit" style="background:#b45309" onclick="return confirm('فرم برای ویرایش باز شود؟')">باز کردن فرم</button></form>
<?php elseif ($kind === RewardRules::FINDER): ?>
<form method="post" class="card"><?= center_csrf_field() ?><input type="hidden" name="action" value="final">
  <button type="submit" style="background:#15803d" onclick="return confirm('فرم ثبت نهایی و شماره‌گذاری شود؟')">ثبت نهایی</button></form>
<?php endif; ?>

<div class="card"><h2>پیش‌نمایش فرم</h2>
  <?php foreach ($model['tables'] as $t): ?>
    <h3 style="margin-top:10px"><?= e((string) $t['caption']) ?></h3>
    <div class="cscroll"><table class="ctable">
      <?php if ($t['header']): ?><tr><?php foreach ($t['header'] as $h): ?><th><?= e($h) ?></th><?php endforeach; ?></tr><?php endif; ?>
      <?php foreach ($t['rows'] as $line): ?><tr><?php foreach ($line as $c => $cell): ?><<?= $t['pairs'] && $c % 2 === 0 ? 'th' : 'td' ?>><?= e($cell) ?></<?= $t['pairs'] && $c % 2 === 0 ? 'th' : 'td' ?>><?php endforeach; ?></tr><?php endforeach; ?>
      <?php if ($t['total']): ?><tr><?php foreach ($t['total'] as $cell): ?><th><?= e($cell) ?></th><?php endforeach; ?></tr><?php endif; ?>
    </table></div>
  <?php endforeach; ?>
  <?php foreach ($model['notes'] ?? [] as $n): ?><p class="srcs"><?= e($n) ?></p><?php endforeach; ?>
</div>
<?php portal_footer();
