<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_cases_view.php';

admin_require(CASE_ROLES);

// میز پیگیری: پرونده‌های باز محدوده، هر کدام در مرحله‌ای که مانده، دیرشده‌ها اول.
// مدیر همه ناحیه‌ها را می‌بیند و با فیلتر ناحیه کار هر مجری را جدا دنبال می‌کند.
$scope = admin_scope();
$open = CaseBoard::open($scope, 1000);
$counts = CaseBoard::counts($open);
$stage = (string) ($_GET['stage'] ?? '');
$area = Areas::code($_GET['area'] ?? null);
$rows = array_values(array_filter($open, static fn ($c) => ($stage === '' || $c['stage'] === $stage || ($stage === 'late' && $c['late']))
    && ($area === null || $c['area'] === Areas::label($area))));

admin_header('میز پیگیری');
echo case_styles();
$tile = static fn (string $key, int $n, string $label, string $tone = '') => '<a class="kpi ' . $tone . '" href="board.php?stage='
    . e($key) . ($area === null ? '' : '&area=' . e($area)) . '" style="text-decoration:none"><b>' . fa_digits((string) $n) . '</b><span>' . e($label) . '</span></a>';
?>
<div class="card">
  <div class="row">
    <h2>میز پیگیری</h2>
    <div><a href="intake.php"><button type="button">ثبت گزارش تازه</button></a></div>
  </div>
  <div class="kpis">
    <?= $tile('late', $counts['late'], 'دیرشده یا با هشدار', $counts['late'] > 0 ? 'danger' : '') ?>
    <?php foreach (CaseBoard::STAGES as $key => $label): ?>
      <?= $tile($key, $counts['stages'][$key], $label, $counts['stages'][$key] > 0 && in_array($key, ['awaiting_referral', 'with_executor', 'awaiting_approval'], true) ? 'warn' : '') ?>
    <?php endforeach; ?>
  </div>
  <form method="get" class="row" style="justify-content:flex-start;gap:12px">
    <input type="hidden" name="stage" value="<?= e($stage) ?>">
    <label style="margin:0">ناحیه<select name="area" onchange="this.form.submit()"><option value="">همه</option>
      <?php foreach (Areas::options() as $code => $label): if (!$scope->coversArea((string) $code)) { continue; } ?>
        <option value="<?= e((string) $code) ?>" <?= $area === (string) $code ? 'selected' : '' ?>><?= e($label) ?></option>
      <?php endforeach; ?></select></label>
    <?php if ($stage !== '' || $area !== null): ?><a href="board.php">همه پرونده‌های باز</a><?php endif; ?>
  </form>
</div>

<div class="card">
  <?php if ($rows === []): ?><p class="muted">پرونده بازی در این دسته نیست.</p><?php else: ?>
  <table>
    <tr><th>کد</th><th>نوع</th><th>ناحیه</th><th>مرحله</th><th>مجری</th><th>کارشناس</th><th>مهلت</th><th>ثبت</th></tr>
    <?php foreach ($rows as $c): ?>
      <tr>
        <td dir="ltr" style="text-align:right"><a href="case.php?id=<?= e($c['id']) ?>#flow"><?= e($c['code'] ?: '—') ?></a></td>
        <td><?= e($c['type']) ?></td>
        <td><?= e($c['area']) ?></td>
        <td><?= e($c['stage_name']) ?><?= $c['late'] ? ' <span class="tag tag-overdue">دیر</span>' : '' ?>
          <?= $c['auto_referred'] ? ' <span class="tag tag-seen">ارجاع خودکار</span>' : '' ?></td>
        <td><?= e($c['executor'] ?? '—') ?></td>
        <td><?= e($c['expert'] ?? '—') ?></td>
        <td class="nw"><?= e(Jalali::format($c['due_at'] ?? $c['refer_due_at'], false) ?: '—') ?></td>
        <td class="nw"><?= e(Jalali::format($c['created_at'], false)) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>
<?php portal_footer();
