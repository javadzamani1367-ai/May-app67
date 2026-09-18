<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$user = portal_require_user();
$unit = (int) $user['unit'];
$now = Db::now();

$rows = Db::all(
    'SELECT d.*, r.tracking_code, r.county, r.address, r.owner_name,
            (SELECT COUNT(*) FROM dispatch_responses x WHERE x.dispatch_id = d.id) AS response_count
     FROM dispatches d JOIN reports r ON r.id = d.report_id
     WHERE d.unit = ? ORDER BY d.dispatched_at DESC LIMIT 200',
    [$unit]
);

portal_header('صندوق ورودی — ' . unit_name($unit));
?>
<div class="card">
  <div class="row">
    <div>
      <h2><?= e(unit_name($unit)) ?></h2>
      <p class="muted"><?= e($user['full_name']) ?></p>
    </div>
    <div class="muted">
      مجموع: <?= fa_digits((string) count($rows)) ?> مورد
    </div>
  </div>
</div>

<?php if ($rows === []): ?>
  <div class="card"><p>هنوز مدرکی برای واحد شما ارسال نشده است.</p></div>
<?php else: ?>
<div class="card">
  <table>
    <thead>
      <tr>
        <th>کد رهگیری</th><th>شهرستان</th><th>تاریخ ارسال</th><th>مهلت</th><th>وضعیت</th><th></th>
      </tr>
    </thead>
    <tbody>
    <?php foreach ($rows as $row):
        $overdue = $row['deadline_at'] !== null
            && (int) $row['status'] !== 2
            && (int) $row['deadline_at'] < $now;
        [$tagClass, $tagText] = $overdue
            ? ['tag-overdue', 'مهلت گذشته']
            : match ((int) $row['status']) {
                2 => ['tag-answered', 'پاسخ داده شد'],
                1 => ['tag-seen', 'دیده شد'],
                default => ['tag-sent', 'ارسال شد'],
            };
    ?>
      <tr>
        <td><?= fa_digits(e($row['tracking_code'] ?? '—')) ?></td>
        <td><?= e($row['county'] ?? '—') ?></td>
        <td><?= e(jalali((int) $row['dispatched_at'])) ?></td>
        <td><?= $row['deadline_at'] === null ? '—' : e(jalali((int) $row['deadline_at'])) ?></td>
        <td><span class="tag <?= $tagClass ?>"><?= $tagText ?></span></td>
        <td><a href="view.php?id=<?= e((string) $row['id']) ?>">مشاهده</a></td>
      </tr>
    <?php endforeach; ?>
    </tbody>
  </table>
</div>
<?php endif; ?>
<?php portal_footer();
