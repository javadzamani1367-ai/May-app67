<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require dirname(__DIR__) . '/admin/_centers_view.php';

// آمار جامع مراکز برای واحدها: سطرهای ناحیه واحد، فقط با ستون‌هایی که مدیر برای این واحد تیک
// زده؛ همان ستون‌ها را اینجا پر می‌کنند. بی تیک مدیر این صفحه بسته است.
$user = portal_require_user();
$access = CenterSettings::access($user);
if ($access === null || (int) $user['role'] !== Auth::ROLE_UNIT) {
    header('Location: index.php');
    exit;
}
$id = (string) ($_GET['id'] ?? '');
$row = $id === '' ? null : CenterRows::one($access, $id);
$error = null;
$saved = false;
if ($row !== null && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    center_csrf_check();
    [$values, $choices] = center_posted($row);
    $error = CenterEdit::save($access, $user, $id, $values, $choices);
    $saved = $error === null;
    $row = CenterRows::one($access, $id);
}
portal_header('آمار جامع مراکز', true, unit_name((int) $user['unit']), portal_nav($user));
echo center_styles();
if ($row !== null): ?>
<div class="card">
  <div class="row"><h2><?= e((string) (($row['values']['subscriber'] ?? null) ?: ($row['values']['subscription'] ?? 'سطر'))) ?></h2><a href="centers.php">بازگشت</a></div>
  <?php if ($saved): ?><p style="color:#15803d">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p style="color:#9a3412"><?= e($error) ?></p><?php endif; ?>
  <?= center_form($row, $access) ?>
</div>
<?php else:
    [$from, $to] = center_range($_GET);
    $rows = CenterRows::query($access, $from, $to, null, (string) ($_GET['q'] ?? ''));
    $cols = array_values(array_intersect(['row', 'county', 'report_date', 'subscription', 'subscriber'], $access['see']));
    $mine = $access['fields']; ?>
<div class="card">
  <h2>آمار جامع مراکز استخراج</h2>
  <p class="muted">ستون‌هایی که مدیر برای واحد شما باز کرده: <?= e(implode('، ', array_map([CenterColumns::class, 'label'], $mine))) ?></p>
  <?= center_range_form($_GET, $access['areas'] ?? []) ?>
</div>
<div class="card"><div class="cscroll"><table class="ctable">
  <tr><?php foreach (array_merge($cols, $mine) as $k): ?><th><?= e(CenterColumns::label($k)) ?></th><?php endforeach; ?><th></th></tr>
  <?php foreach ($rows as $r): ?><tr>
    <?php foreach (array_merge($cols, $mine) as $k): ?><td><?= e(center_show($k, $r['values'][$k] ?? null)) ?></td><?php endforeach; ?>
    <td><a href="centers.php?id=<?= e(urlencode($r['id'])) ?>"><?= $r['locked'] ? 'دیدن' : 'تکمیل' ?></a></td></tr>
  <?php endforeach; ?>
</table></div></div>
<?php endif;
portal_footer();
