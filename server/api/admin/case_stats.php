<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_cases_view.php';

admin_require();

// آمار پرونده‌های بازدید (CLAUDE.md بخش ۱۱)، با همان فیلترهای آرشیو: شمار و
// توان کشف‌شده به تفکیک وضعیت، نوع، شهرستان، کارشناس و ماه، و یافته‌های فنی.
$filter = CaseArchive::filters($_GET);
$values = $filter['values'];
$s = CaseArchive::stats($filter);
$kw = static fn ($watt) => CaseLabels::number((float) $watt / 1000) . ' کیلووات';
$link = static fn (array $change, string $text) => '<a href="cases.php' . e(query_with($values, $change)) . '">' . e($text) . '</a>';

$byType = array_map(static fn ($row) => [case_type_tag((int) $row['k']), (int) $row['n'], $kw($row['watt'])], $s['type']);
$byStatus = array_map(static fn ($row) => [$link(['status' => (string) $row['k']], CaseLabels::STATUSES[(int) $row['k']] ?? '—'),
    (int) $row['n'], ''], $s['status']);
$byCounty = array_map(static fn ($row) => [$link(['county' => (string) $row['k']], (string) $row['k'] ?: '—'),
    (int) $row['n'], $kw($row['watt'])], $s['county']);
$byExpert = array_map(static fn ($row) => [$link(['expert' => (string) $row['k']],
    trim((string) $row['name']) ?: Jalali::digits((string) $row['k']) ?: '—'), (int) $row['n'], $kw($row['watt'])], $s['expert']);
$byMonth = [];
foreach ($s['months'] as $month => $n) {
    $byMonth[] = [e(Jalali::digits($month)), $n, ''];
}
$findings = [
    [e('دستکاری کنتور'), $s['tampered'], ''],
    [e('برداشت قبل از کنتور'), $s['before_meter'], ''],
    [e('بدون انشعاب (غیرمجاز)'), $s['no_supply'], ''],
];

admin_header('آمار پرونده‌ها');
?>
<div class="card">
  <div class="row">
    <div><h2>آمار پرونده‌های بازدید</h2>
      <p class="muted">هر عدد به همان پرونده‌ها در آرشیو پیوند دارد.</p></div>
    <div class="actions-bar">
      <a href="cases.php<?= e(query_with($values)) ?>"><button type="button">فهرست پرونده‌ها</button></a>
      <a href="cases_export.php<?= e(query_with($values)) ?>"><button type="button">خروجی اکسل</button></a>
    </div>
  </div>
  <?= case_kpis($s) ?>
  <?= case_filter_form($values, 'case_stats.php') ?>
</div>
<div class="section-grid">
  <div class="card"><h3>نوع گزارش</h3><?= case_bars($byType, '#0f766e') ?></div>
  <div class="card"><h3>وضعیت</h3><?= case_bars($byStatus, '#1d4ed8') ?></div>
  <div class="card"><h3>شهرستان</h3><?= case_bars($byCounty, '#0e7490') ?></div>
  <div class="card"><h3>کارشناس</h3><?= case_bars($byExpert, '#334155') ?></div>
  <div class="card"><h3>پرونده در هر ماه (تاریخ گزارش، ۱۲ ماه آخر)</h3><?= case_bars($byMonth, '#7c3aed') ?></div>
  <div class="card"><h3>یافته‌های فنی</h3><?= case_bars($findings, '#b91c1c') ?>
    <p class="muted"><?= $link(['tampered' => '1'], 'پرونده‌های دارای دستکاری کنتور') ?></p></div>
</div>
<?php portal_footer();
