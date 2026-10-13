<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_cases_view.php';

admin_require();

// عملکرد واحدها، مثل همان صفحه در اپ مدیر: هر واحد چند ارسال گرفت، چند تا را
// دید و پاسخ داد، چند تا از مهلت گذشت و پاسخ به طور میانگین چقدر طول کشید.
// بازه بر اساس زمان ارسال است؛ بی‌بازه، همه ارسال‌ها.
$fromText = trim((string) ($_GET['from'] ?? ''));
$toText = trim((string) ($_GET['to'] ?? ''));
$from = ($p = Jalali::parse($fromText)) === null ? 0 : Jalali::startOfDay(...$p);
$to = ($p = Jalali::parse($toText)) === null ? Db::now() : Jalali::startOfDay(...$p) + 86_400_000 - 1;
$rows = CaseActions::unitStats($from, $to);
$overdue = CaseActions::overdueDispatches();
$total = ['sent' => 0, 'seen' => 0, 'answered' => 0, 'overdue' => 0];
foreach ($rows as $row) {
    foreach ($total as $k => $v) {
        $total[$k] = $v + (int) $row[$k];
    }
}
$percent = static fn (int $part, int $all): string => $all === 0 ? '—' : fa_digits((string) round($part * 100 / $all)) . '٪';

admin_header('عملکرد واحدها');
echo case_styles();
?>
<div class="card">
  <h2>عملکرد واحدها</h2>
  <form method="get" class="row" style="justify-content:flex-start;gap:12px">
    <label style="margin:0">از تاریخ <input name="from" value="<?= e($fromText) ?>" placeholder="۱۴۰۵/۰۱/۰۱" style="width:130px"></label>
    <label style="margin:0">تا تاریخ <input name="to" value="<?= e($toText) ?>" placeholder="۱۴۰۵/۱۲/۲۹" style="width:130px"></label>
    <button type="submit">نمایش</button>
    <?php if ($fromText !== '' || $toText !== ''): ?><a href="performance.php">همه زمان‌ها</a><?php endif; ?>
  </form>
  <div class="kpis">
    <div class="kpi"><b><?= fa_digits((string) $total['sent']) ?></b><span>ارسال به واحدها</span></div>
    <div class="kpi"><b><?= $percent($total['seen'], $total['sent']) ?></b><span>دیده‌شده</span></div>
    <div class="kpi ok"><b><?= $percent($total['answered'], $total['sent']) ?></b><span>پاسخ‌داده</span></div>
    <div class="kpi danger"><b><?= fa_digits((string) $total['overdue']) ?></b><span>مهلت گذشته بی‌پاسخ</span></div>
  </div>
  <?php if ($rows === []): ?>
    <p class="muted">در این بازه ارسالی به واحدها ثبت نشده.</p>
  <?php else: ?>
  <table>
    <tr><th>واحد</th><th>ارسال</th><th>دیده‌شده</th><th>پاسخ‌داده</th><th>نرخ پاسخ</th><th>پاسخ به‌موقع</th><th>مهلت گذشته</th><th>میانگین زمان پاسخ</th></tr>
    <?php foreach ($rows as $row): ?>
      <tr>
        <td><strong><?= e(UNIT_NAMES[(int) $row['unit']] ?? '—') ?></strong></td>
        <td><?= fa_digits((string) $row['sent']) ?></td>
        <td><?= fa_digits((string) $row['seen']) ?></td>
        <td><?= fa_digits((string) $row['answered']) ?></td>
        <td>
          <div class="bar-track"><div class="bar" style="width:<?= max(1, (int) round((float) $row['answer_rate'])) ?>%"></div></div>
          <?= e((string) CaseLabels::number($row['answer_rate'])) ?>٪
        </td>
        <td><?= fa_digits((string) (int) $row['on_time']) ?></td>
        <td><?= (int) $row['overdue'] > 0 ? '<span class="tag tag-overdue">' . fa_digits((string) $row['overdue']) . '</span>' : '۰' ?></td>
        <td><?= $row['avg_answer_hours'] === null ? '—' : e(CaseLabels::number($row['avg_answer_hours']) . ' ساعت') ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>

<?php if ($overdue !== []): ?>
<div class="card">
  <h3>ارسال‌های با مهلت گذشته</h3>
  <table>
    <tr><th>کد رهگیری</th><th>واحد</th><th>ارسال</th><th>مهلت</th><th>وضعیت</th></tr>
    <?php foreach ($overdue as $row): ?>
      <tr>
        <td dir="ltr" style="text-align:right"><a href="case.php?id=<?= e($row['report_id']) ?>"><?= e(CaseLabels::code($row) ?: '—') ?></a></td>
        <td><?= e(UNIT_NAMES[(int) $row['unit']] ?? '—') ?></td>
        <td class="nw"><?= e(Jalali::format((int) $row['dispatched_at'], false)) ?></td>
        <td><span class="tag tag-overdue"><?= e(Jalali::format((int) $row['deadline_at'], false)) ?></span></td>
        <td><?= e(CaseLabels::of(CaseLabels::DISPATCH_STATUSES, $row['status'])) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
</div>
<?php endif; ?>
<?php portal_footer();
