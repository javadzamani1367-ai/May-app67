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
  <h2>پاداش‌ها و فرم هر پاداش</h2>
  <p class="srcs">هر پاداش فرم تک‌صفحه‌ای خودش را دارد: افراد، سمت، اقدام و سهم هر نفر با استناد به دستورالعمل؛ پس از ثبت نهایی شماره می‌گیرد و قفل می‌شود.</p>
  <table class="ctable"><tr><th>پاداش</th><th>مبلغ مرکز (ریال)</th><th>نفرات</th><th>دستورالعمل</th><th>فرم</th></tr>
  <?php foreach (RewardRules::KEYS as $kind => $key): $st = $row['rewards'][$kind]; ?>
    <tr><td><a href="center_form.php?row=<?= e(urlencode($id)) ?>&kind=<?= $key ?>"><?= e(RewardRules::NAMES[$kind]) ?></a></td>
      <td><?= e(fa_digits(number_format((float) ($st['calc']['amount'] ?? 0)))) ?></td>
      <td><?= $kind === RewardRules::FINDER ? '—' : e(fa_digits((string) count($st['calc']['people'] ?? []))) ?></td>
      <td class="srcs"><?= e(RewardRules::describe($st['calc']['rule'] ?? null)) ?></td>
      <td><?= $st['final_at'] !== null ? '<span class="badge">ثبت نهایی ' . e(fa_digits((string) $st['number'])) . '</span>' : 'پیش‌نویس' ?></td></tr>
  <?php endforeach; ?>
  </table>
</div>
<?php endif; ?>

<div class="card"><h2>تاریخچه تغییرات</h2><?= center_changes($row['changes']) ?></div>
<?php portal_footer();
