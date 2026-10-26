<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_cases_view.php';

admin_require(CASE_ROLES);

// آرشیو پرونده‌های بازدید: همان «بایگانی» نرم‌افزار ویندوز، روی هر مرورگری.
$filter = CaseArchive::filters($_GET, admin_scope());
$values = $filter['values'];
$perPage = 50;
$total = CaseArchive::count($filter);
$pages = max(1, (int) ceil($total / $perPage));
$page = min($pages, max(1, (int) ($_GET['page'] ?? 1)));
$rows = CaseArchive::rows($filter, $perPage, ($page - 1) * $perPage);
$stats = CaseArchive::stats($filter);

admin_header('آرشیو پرونده‌ها');
?>
<div class="card">
  <div class="row">
    <div>
      <h2>آرشیو پرونده‌های بازدید</h2>
      <p class="muted">همه پرونده‌هایی که اپ‌های کارشناس و مدیر به سرور فرستاده‌اند.</p>
    </div>
    <div>
      <a href="case_stats.php<?= e(query_with($values)) ?>"><button type="button">آمار و نمودار</button></a>
      <a href="cases_export.php<?= e(query_with($values)) ?>"><button type="button">خروجی اکسل</button></a>
      <a href="cases_export.php<?= e(query_with($values, ['format' => 'docx'])) ?>"><button type="button" style="background:#1d4ed8">خروجی Word</button></a>
    </div>
  </div>
  <?= case_kpis($stats) ?>
  <?= case_filter_form($values, 'cases.php') ?>
</div>

<div class="card">
  <?php if ($rows === []): ?>
    <p class="muted">پرونده‌ای با این فیلترها نیست.</p>
  <?php else: ?>
  <table>
    <tr><th>کد رهگیری</th><th>نوع</th><th>تاریخ گزارش</th><th>شهرستان</th><th>مالک یا آدرس</th>
        <th>دستگاه</th><th>توان (کیلووات)</th><th>وضعیت</th><th>کارشناس</th></tr>
    <?php foreach ($rows as $row): ?>
      <tr>
        <td dir="ltr" class="nw" style="text-align:right"><a href="case.php?id=<?= e($row['id']) ?>"><?= e(CaseLabels::code($row) ?: '—') ?></a></td>
        <td><?= case_type_tag((int) $row['report_type']) ?></td>
        <td class="nw"><?= e(Jalali::format((int) $row['report_date'], false)) ?></td>
        <td class="nw"><?= e(CaseLabels::countyWithArea($row['county'], $row['area_code'])) ?></td>
        <td><?= e(mb_substr((string) ($row['owner_name'] ?: $row['address']), 0, 60)) ?></td>
        <td><?= fa_digits((string) (int) $row['device_count']) ?></td>
        <td><?= e(is_numeric($row['total_watt']) ? CaseLabels::number((float) $row['total_watt'] / 1000) : '—') ?></td>
        <td><?= case_status_tag($row) ?></td>
        <td class="nw"><?= e(trim((string) $row['expert_name']) ?: Jalali::digits((string) $row['expert_code'])) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
  <?= case_pager($values, $page, $pages, $total) ?>
</div>
<?php portal_footer();
