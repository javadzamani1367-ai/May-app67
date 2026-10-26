<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// آمار جامع مراکز استخراج و اقدامات: فهرست بازه‌ای که مدیر انتخاب می‌کند، با نمودار، خروجی
// اکسل به شکل فرم توانیر و Word ستون‌های تیک‌خورده، و پیوند ورود فایل، دستورالعمل‌ها و تنظیمات.
// مدیر همه؛ مجری رمزارز فقط اگر مدیر اجازه داده و فقط ناحیه‌های خودش.
$user = admin_require(CASE_ROLES);
$access = CenterSettings::access($user);
if ($access === null) {
    admin_header('آمار جامع مراکز');
    echo '<div class="card"><p>دسترسی به آمار جامع را مدیر برای شما باز نکرده است.</p></div>';
    portal_footer();
    exit;
}

$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST' && ($_POST['action'] ?? '') === 'add') {
    center_csrf_check();
    $id = CenterEdit::add($access, $user, (string) ($_POST['date'] ?? ''), (string) ($_POST['area'] ?? '') ?: null, null);
    if (!str_starts_with($id, '!')) {
        header('Location: center_row.php?id=' . urlencode($id));
        exit;
    }
    $error = substr($id, 1);
}

[$from, $to] = center_range($_GET);
$rows = CenterRows::query($access, $from, $to, ($_GET['area'] ?? '') ?: null, (string) ($_GET['q'] ?? ''));
$charts = CenterCharts::summary($rows);
$t = $charts['totals'];
$export = static fn (string $format) => 'center_export.php' . query_with($_GET, ['format' => $format]);
$list = ['row', 'county', 'report_date', 'subscription', 'subscriber', 'miners', 'capacity_kw', 'finder_reward', 'energy_amount',
    'damage_amount', 'collected_amount', 'status'];
$list = array_values(array_intersect($list, $access['see']));

admin_header('آمار جامع مراکز');
echo center_styles();
?>
<div class="card">
  <h2>آمار جامع مراکز استخراج و اقدامات</h2>
  <p class="muted">هر پرونده رمزارز که تأیید شود خودکار یک سطر می‌گیرد؛ سطرهای سال‌های پیش از فایل اکسل وارد می‌شوند. خانه‌ای که سامانه و
    فایل در آن یکی نیستند «ناسازگار» است تا یکی را انتخاب کنید.</p>
  <?= center_range_form($_GET, $access['areas'],
      '<a href="' . e($export('xlsx')) . '"><button type="button" style="background:#15803d">اکسل (فرم توانیر)</button></a> '
      . '<a href="' . e($export('docx')) . '"><button type="button" style="background:#1d4ed8">Word</button></a>') ?>
  <?php if ($access['manage']): ?>
  <p><a href="center_import.php">ورود فایل اکسل</a> · <a href="center_rewards.php<?= e(query_with($_GET)) ?>">گزارش‌های پاداش</a> ·
    <a href="center_rules.php">دستورالعمل‌های پاداش</a> · <a href="center_settings.php">ستون‌ها، دسترسی‌ها و قفل سال</a></p>
  <?php endif; ?>
</div>

<div class="card">
  <div class="kpis">
    <div class="kpi"><b><?= fa_digits((string) $t['centers']) ?></b>مرکز</div>
    <div class="kpi"><b><?= fa_digits(number_format($t['miners'])) ?></b>ماینر</div>
    <div class="kpi"><b><?= fa_digits((string) $t['kw']) ?></b>کیلووات برآوردی</div>
    <div class="kpi"><b><?= fa_digits((string) $t['commission']) ?></b>کمیسیون</div>
    <div class="kpi"><b><?= fa_digits((string) $t['conflicts']) ?></b>خانه ناسازگار</div>
    <div class="kpi"><b><?= fa_digits((string) $t['warnings']) ?></b>سطر با هشدار</div>
  </div>
  <div class="grid" style="margin-top:12px">
    <div><h3>مراکز هر ماه</h3><?= CenterCharts::svg(array_map(static fn ($m) => [$m['label'], $m['centers']], $charts['months'])) ?></div>
    <div><h3>ماینر هر ناحیه</h3><?= CenterCharts::svg(array_map(static fn ($a) => [$a['label'], $a['miners']], $charts['areas']), '#1d4ed8') ?></div>
    <div><h3>وضعیت اقدام</h3><?= CenterCharts::svg(array_map(static fn ($s) => [$s['label'], $s['count']], $charts['status']), '#b45309') ?></div>
    <div><h3>مبالغ (ریال)</h3><?= CenterCharts::svg(array_map(static fn ($m) => [$m['label'], $m['amount']], $charts['money']), '#15803d', true) ?></div>
  </div>
</div>

<div class="card">
  <div class="row"><h2><?= fa_digits((string) count($rows)) ?> سطر</h2>
  <?php if ($access['role'] !== Auth::ROLE_UNIT): ?>
  <form method="post" class="row" style="align-items:end"><?= center_csrf_field() ?><input type="hidden" name="action" value="add">
    <label>سطر دستی — تاریخ گزارش<input name="date" placeholder="۱۴۰۴/۰۱/۰۱"></label>
    <label>ناحیه<select name="area"><?php foreach ($access['areas'] ?? array_keys(Areas::NAMES) as $code): ?><option value="<?= e((string) $code) ?>"><?= e(Areas::label((string) $code)) ?></option><?php endforeach; ?></select></label>
    <button type="submit">افزودن</button></form>
  <?php endif; ?></div>
  <?php if ($error !== null): ?><p class="warn"><?= e($error) ?></p><?php endif; ?>
  <div class="cscroll"><table class="ctable">
    <tr><?php foreach ($list as $key): ?><th><?= e(CenterColumns::label($key)) ?></th><?php endforeach; ?><th></th></tr>
    <?php foreach ($rows as $row): ?>
    <tr><?php foreach ($list as $key): ?><td><?= e(center_show($key, $row['values'][$key] ?? null)) ?></td><?php endforeach; ?>
      <td><a href="center_row.php?id=<?= e(urlencode($row['id'])) ?>">باز کردن</a>
        <?= $row['conflicts'] !== [] ? '<span class="badge amber">' . fa_digits((string) count($row['conflicts'])) . ' ناسازگار</span>' : '' ?>
        <?= $row['warnings'] !== [] ? '<span class="badge">هشدار</span>' : '' ?>
        <?= $row['locked'] ? '<span class="badge amber">قفل</span>' : '' ?>
        <?= $row['source'] === CenterSchema::SOURCE_IMPORT && $row['report_id'] === null ? '<span class="srcs">از فایل</span>' : '' ?></td></tr>
    <?php endforeach; ?>
    <?php $totals = CenterRows::totals($rows); ?>
    <tr><?php foreach ($list as $i => $key): ?><th><?= isset($totals[$key]) ? e(center_show($key === 'capacity_kw' ? 'capacity_kw' : 'miners', $totals[$key])) : ($i === 0 ? 'جمع' : '') ?></th><?php endforeach; ?><th></th></tr>
  </table></div>
</div>
<?php portal_footer();
