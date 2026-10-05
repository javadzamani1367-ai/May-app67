<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// داشبورد: هر عدد پیوندی است به همان موردها در فهرست، تا عدد به کار برسد.
$now = Db::now();
$byStatus = array_fill_keys(array_keys(FieldAdmin::STATUS_NAMES), 0);
foreach (Db::all('SELECT status, COUNT(*) AS n FROM field_items WHERE merged_into IS NULL GROUP BY status') as $row) {
    $byStatus[(int) $row['status']] = (int) $row['n'];
}
$byKind = array_fill_keys(array_keys(FieldAdmin::KIND_NAMES), 0);
foreach (Db::all('SELECT kind, COUNT(*) AS n FROM field_items WHERE merged_into IS NULL GROUP BY kind') as $row) {
    $byKind[(int) $row['kind']] = (int) $row['n'];
}
$total = array_sum($byStatus);
$week = (int) (Db::one('SELECT COUNT(*) AS n FROM field_items WHERE merged_into IS NULL AND created_at >= ?',
    [$now - 7 * 86_400_000])['n'] ?? 0);
$overdue = Db::all(
    'SELECT i.id, i.kind, i.tracking_code, i.status, i.due_at, i.address, u.full_name AS expert
     FROM field_items i LEFT JOIN users u ON u.id = i.assigned_to
     WHERE i.merged_into IS NULL AND ' . FieldAdmin::OVERDUE_SQL . ' ORDER BY i.due_at LIMIT 20',
    [$now]
);
$overdueCount = (int) (Db::one('SELECT COUNT(*) AS n FROM field_items i WHERE i.merged_into IS NULL AND '
    . FieldAdmin::OVERDUE_SQL, [$now])['n'] ?? 0);
$waiting = $byStatus[Field::STATUS_REGISTERED];
$users = FieldAdmin::userStats();
$experts = FieldAdmin::expertStats();

admin_header('داشبورد');
?>
<div class="card">
  <h2>موردهای میدانی</h2>
  <div class="grid">
    <p><a href="items.php"><strong><?= fa_digits((string) $total) ?></strong></a> مورد در کل،
       <?= fa_digits((string) $week) ?> مورد در هفت روز گذشته</p>
    <p><a href="items.php?status=<?= Field::STATUS_REGISTERED ?>"><strong><?= fa_digits((string) $waiting) ?></strong></a>
       مورد هنوز بررسی نشده</p>
    <p><a href="items.php?overdue=1"><strong><?= fa_digits((string) $overdueCount) ?></strong></a>
       ارجاع با مهلت گذشته</p>
  </div>
  <table>
    <tr><th>وضعیت</th><th>تعداد</th><th>نوع</th><th>تعداد</th></tr>
    <?php $kinds = array_keys($byKind); foreach (array_keys(FieldAdmin::STATUS_NAMES) as $i => $status): ?>
      <tr>
        <td><a href="items.php?status=<?= $status ?>"><?= e(FieldAdmin::STATUS_NAMES[$status]) ?></a></td>
        <td><?= fa_digits((string) $byStatus[$status]) ?></td>
        <?php if (isset($kinds[$i])): ?>
          <td><a href="items.php?kind=<?= $kinds[$i] ?>"><?= e(FieldAdmin::KIND_NAMES[$kinds[$i]]) ?></a></td>
          <td><?= fa_digits((string) $byKind[$kinds[$i]]) ?></td>
        <?php else: ?><td></td><td></td><?php endif; ?>
      </tr>
    <?php endforeach; ?>
  </table>
</div>

<?php if ($overdue !== []): ?>
<div class="card">
  <h3>ارجاع‌های با مهلت گذشته</h3>
  <table>
    <tr><th>کد</th><th>نوع</th><th>کارشناس</th><th>مهلت</th><th>آدرس</th></tr>
    <?php foreach ($overdue as $row): ?>
      <tr>
        <td dir="ltr" style="text-align:right"><a href="item.php?id=<?= e($row['id']) ?>"><?= e($row['tracking_code']) ?></a></td>
        <td><?= e(FieldAdmin::KIND_NAMES[(int) $row['kind']] ?? '—') ?></td>
        <td><?= e($row['expert'] ?? '—') ?></td>
        <td><span class="tag tag-overdue"><?= e(Jalali::format((int) $row['due_at'], false)) ?></span></td>
        <td><?= e(mb_substr((string) $row['address'], 0, 60)) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
</div>
<?php endif; ?>

<div class="card">
  <h3>کاربران میدانی</h3>
  <?php if ($users === []): ?>
    <p class="muted">هنوز کاربر میدانی ساخته نشده.</p>
  <?php else: ?>
  <table>
    <tr><th>نام</th><th>همه موردها</th><th>۳۰ روز اخیر</th><th>ردشده</th><th>آخرین ارسال</th></tr>
    <?php foreach ($users as $row): ?>
      <tr>
        <td><a href="items.php?user=<?= e($row['id']) ?>"><?= e($row['full_name']) ?></a>
            <?= (int) $row['active'] === 1 ? '' : '<span class="tag tag-overdue">غیرفعال</span>' ?></td>
        <td><?= fa_digits((string) $row['total']) ?></td>
        <td><?= fa_digits((string) $row['recent']) ?></td>
        <td><?= fa_digits((string) $row['rejected']) ?></td>
        <td><?= e(Jalali::format($row['last_at'] === null ? null : (int) $row['last_at'])) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>

<?php if ($experts !== []): ?>
<div class="card">
  <h3>ارجاع به کارشناسان</h3>
  <table>
    <tr><th>کارشناس</th><th>باز</th><th>مهلت گذشته</th><th>نتیجه یا بسته</th></tr>
    <?php foreach ($experts as $row): ?>
      <tr>
        <td><a href="items.php?assigned=<?= e($row['id']) ?>"><?= e($row['full_name']) ?></a></td>
        <td><?= fa_digits((string) $row['open_count']) ?></td>
        <td><?= (int) $row['overdue'] > 0
            ? '<span class="tag tag-overdue">' . fa_digits((string) $row['overdue']) . '</span>' : '۰' ?></td>
        <td><?= fa_digits((string) $row['done']) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
</div>
<?php endif; ?>
<?php portal_footer();
