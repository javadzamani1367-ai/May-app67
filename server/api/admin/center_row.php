<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// یک سطر آمار جامع: ویرایش بخش به بخش، انتخاب میان سامانه و فایل در خانه‌های ناسازگار،
// پاداش‌های حساب‌شده با دستورالعمل جاری، و تاریخچه هر خانه.
$user = admin_require(CASE_ROLES);
$access = CenterSettings::access($user);
$id = (string) ($_GET['id'] ?? '');
$row = $access === null ? null : CenterRows::one($access, $id);
if ($row === null) {
    http_response_code(404);
    exit('این سطر پیدا نشد.');
}
$error = null;
$saved = false;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    center_csrf_check();
    $action = (string) ($_POST['action'] ?? '');
    if ($action === 'save') {
        [$values, $choices] = center_posted($row);
        $error = CenterEdit::save($access, $user, $id, $values, $choices);
        $saved = $error === null;
    } elseif ($action === 'copy') {
        $copy = CenterEdit::add($access, $user, null, null, $id);
        if (!str_starts_with($copy, '!')) {
            header('Location: center_row.php?id=' . urlencode($copy));
            exit;
        }
        $error = substr($copy, 1);
    } elseif ($action === 'delete' && $access['manage']) {
        $error = CenterEdit::delete($access, $user, $id);
        if ($error === null) {
            header('Location: centers.php');
            exit;
        }
    }
    $row = CenterRows::one($access, $id) ?? $row;
}
$v = $row['values'];
admin_header('سطر آمار جامع');
echo center_styles();
?>
<div class="card">
  <div class="row"><div><h2><?= e(($v['subscriber'] ?? null) ?: ($v['subscription'] ?? null) ?: 'سطر آمار') ?></h2>
    <p class="muted">سال <?= fa_digits((string) $row['year']) ?> · <?= e($row['area_code'] ? Areas::label($row['area_code']) : 'بی ناحیه') ?>
    <?php if ($row['report_id'] !== null): ?> · <a href="case.php?id=<?= e(urlencode($row['report_id'])) ?>">پرونده <?= e(fa_digits((string) ($v['tracking_code'] ?? ''))) ?></a><?php endif; ?>
    <?= $row['approved'] ? '' : ' · <span class="badge">پرونده دیگر تأییدشده نیست</span>' ?></p></div>
    <div><a href="centers.php">بازگشت به فهرست</a></div></div>
  <?php if ($saved): ?><p style="color:#15803d">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="warn"><?= e($error) ?></p><?php endif; ?>
  <?php foreach ($row['warnings'] as $w): ?><p class="warn">⚠ <?= e($w['text']) ?></p><?php endforeach; ?>
  <?= center_form($row, $access) ?>
  <?php if (!$row['locked']): ?>
  <form method="post" style="display:inline"><?= center_csrf_field() ?><input type="hidden" name="action" value="copy">
    <button type="submit" style="background:#475569">سطر دیگر برای همین مرکز</button></form>
  <?php if ($access['manage']): ?>
  <form method="post" style="display:inline" onsubmit="return confirm('این سطر حذف شود؟')"><?= center_csrf_field() ?>
    <input type="hidden" name="action" value="delete"><button type="submit" style="background:#b91c1c">حذف سطر</button></form>
  <?php endif; endif; ?>
</div>

<?php if ($access['manage']): ?>
<div class="card">
  <h2>پاداش‌ها</h2>
  <p>عامل شناسایی: <b><?= e(center_show('finder_reward', $v['finder_reward'] ?? null) ?: '—') ?></b> ریال —
    <span class="srcs"><?= e(RewardRules::describe($row['finder']['rule'] ?? null)) ?></span></p>
  <p>عوامل اجرایی: <b><?= e(center_show('police_reward', $v['police_reward'] ?? null) ?: '—') ?></b> ریال —
    <span class="srcs"><?= e(RewardRules::describe($row['police']['rule'] ?? null)) ?></span></p>
  <?php if (($row['police']['people'] ?? []) !== []): ?>
  <table class="ctable"><tr><th>حاضر</th><th>نهاد</th><th>سهم (ریال)</th></tr>
    <?php foreach ($row['police']['people'] as $p): ?><tr><td><?= e($p['name']) ?></td><td><?= e($p['org']) ?></td><td><?= e(fa_digits(number_format($p['share']))) ?></td></tr><?php endforeach; ?>
  </table>
  <?php endif; ?>
  <?php if ($row['staff'] !== null): $s = $row['staff']; ?>
  <p>عوامل اداری: مبنا <?= e(fa_digits(number_format($s['base']))) ?> ریال؛ کل <b><?= e(fa_digits(number_format($s['amount']))) ?></b>،
    مدیرعامل <?= e(fa_digits(number_format($s['ceo']))) ?>، کارکنان <?= e(fa_digits(number_format($s['staff']))) ?> —
    <span class="srcs"><?= e(RewardRules::describe($s['rule'])) ?></span></p>
  <?php if ($s['roles'] !== []): ?>
  <table class="ctable"><tr><th>نقش</th><th>وزن</th><th>سهم نقش</th><th>دارندگان</th><th>هر نفر</th></tr>
    <?php foreach ($s['roles'] as $r): ?><tr><td><?= e($r['label']) ?></td><td><?= e(fa_digits((string) $r['weight'])) ?>٪</td>
      <td><?= e(fa_digits(number_format($r['amount']))) ?></td><td><?= e(implode('، ', $r['people'])) ?></td><td><?= e(fa_digits(number_format($r['each']))) ?></td></tr><?php endforeach; ?>
  </table>
  <?php endif; endif; ?>
</div>
<?php endif; ?>

<div class="card"><h2>تاریخچه تغییرات</h2><?= center_changes($row['changes']) ?></div>
<?php portal_footer();
