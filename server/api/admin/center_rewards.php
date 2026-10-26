<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// گزارش‌های پاداش یک بازه: «گزارش پاداش عوامل اداری» (مستقل، با اکسل) و پاداش عامل شناسایی و
// عوامل اجرایی که توانیر می‌پردازد. هر سطر دستورالعملی را که با آن حساب شد نشان می‌دهد. فقط مدیر.
$user = admin_require([Auth::ROLE_MANAGER]);
$access = CenterSettings::require($user, true);
[$from, $to] = center_range($_GET);
$rows = CenterRows::query($access, $from, $to, ($_GET['area'] ?? '') ?: null);
$staff = CenterRewardReport::staff($rows);
$agents = CenterRewardReport::agents($rows);
$money = static fn ($n) => fa_digits(number_format((float) $n));
$link = static fn (string $f) => 'center_export.php' . query_with($_GET, ['format' => $f]);

admin_header('گزارش‌های پاداش');
echo center_styles();
?>
<div class="card">
  <h2>گزارش‌های پاداش</h2>
  <?= center_range_form($_GET, null) ?>
  <p><a href="center_rules.php">دستورالعمل‌های پاداش</a> · <a href="centers.php<?= e(query_with($_GET)) ?>">آمار جامع</a></p>
</div>

<div class="card">
  <div class="row"><h2>گزارش پاداش عوامل اداری</h2>
    <a href="<?= e($link('staff')) ?>"><button type="button" style="background:#15803d;margin:0">اکسل</button></a></div>
  <div class="kpis">
    <div class="kpi"><b><?= $money($staff['totals']['base']) ?></b>مبنا (ریال)</div>
    <div class="kpi"><b><?= $money($staff['totals']['amount']) ?></b>پاداش کل</div>
    <div class="kpi"><b><?= $money($staff['totals']['ceo']) ?></b>سهم مدیرعامل</div>
    <div class="kpi"><b><?= $money($staff['totals']['staff']) ?></b>سهم کارکنان</div>
  </div>
  <?php if ($staff['lines'] === []): ?><p class="muted">مرکزی با مبنای پاداش و دستورالعمل جاری در این بازه نیست.</p><?php else: ?>
  <div class="cscroll"><table class="ctable">
    <tr><th>ردیف</th><th>اشتراک</th><th>مشترک</th><th>مبنا</th><th>کل</th><th>مدیرعامل</th><th>کارکنان</th>
      <?php foreach ($staff['roles'] as $r): ?><th><?= e($r) ?></th><?php endforeach; ?><th>دستورالعمل</th></tr>
    <?php foreach ($staff['lines'] as $l): ?>
    <tr><td><?= fa_digits((string) $l['row']) ?></td><td><?= e(fa_digits((string) ($l['values']['subscription'] ?? ''))) ?></td>
      <td><?= e((string) ($l['values']['subscriber'] ?? '')) ?></td><td><?= $money($l['base']) ?></td><td><?= $money($l['amount']) ?></td>
      <td><?= $money($l['ceo']) ?></td><td><?= $money($l['staff']) ?></td>
      <?php foreach ($staff['roles'] as $r): ?><td><?= isset($l['roles'][$r]) ? $money($l['roles'][$r]) : '' ?></td><?php endforeach; ?>
      <td class="srcs"><?= e($l['rule']) ?></td></tr>
    <?php endforeach; ?>
  </table></div>
  <h3 style="margin-top:12px">جمع هر نفر</h3>
  <table class="ctable"><tr><th>نام</th><th>نقش</th><th>مراکز</th><th>پاداش (ریال)</th></tr>
    <?php foreach ($staff['people'] as $p): ?><tr><td><?= e($p['name']) ?></td><td><?= e($p['role']) ?></td><td><?= fa_digits((string) $p['centers']) ?></td><td><?= $money($p['amount']) ?></td></tr><?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>

<div class="card">
  <div class="row"><h2>پاداش عامل شناسایی و عوامل اجرایی (پرداخت توانیر)</h2>
    <a href="<?= e($link('agents')) ?>"><button type="button" style="background:#15803d;margin:0">اکسل</button></a></div>
  <div class="kpis">
    <div class="kpi"><b><?= fa_digits(number_format($agents['totals']['miners'])) ?></b>ماینر</div>
    <div class="kpi"><b><?= $money($agents['totals']['finder']) ?></b>پاداش عامل شناسایی</div>
    <div class="kpi"><b><?= $money($agents['totals']['police']) ?></b>پاداش عوامل اجرایی</div>
  </div>
  <div class="cscroll"><table class="ctable">
    <tr><th>ردیف</th><th>اشتراک</th><th>مشترک</th><th>ماینر</th><th>عامل شناسایی</th><th>پاداش شناسایی</th><th>پاداش اجرایی</th><th>حاضران</th></tr>
    <?php foreach ($agents['lines'] as $l): $v = $l['values']; ?>
    <tr><td><?= fa_digits((string) $l['row']) ?></td><td><?= e(fa_digits((string) ($v['subscription'] ?? ''))) ?></td><td><?= e((string) ($v['subscriber'] ?? '')) ?></td>
      <td><?= fa_digits((string) ($v['miners'] ?? '')) ?></td><td><?= e((string) ($v['finder_name'] ?? '')) ?></td>
      <td><?= $money($l['finder']) ?></td><td><?= $money($l['police']) ?></td>
      <td class="srcs"><?= e(implode('، ', array_map(static fn ($p) => $p['name'] . ' (' . $p['org'] . '): ' . number_format($p['share']), $l['people']))) ?></td></tr>
    <?php endforeach; ?>
  </table></div>
</div>
<?php portal_footer();
