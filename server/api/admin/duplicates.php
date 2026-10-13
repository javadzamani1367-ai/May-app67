<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_item_merge.php';

admin_require();

// همه جفت‌های احتمالاً تکراری میان موردهای باز؛ تصمیم ادغام در صفحه خود مورد گرفته می‌شود.
$pairs = FieldDuplicates::pairs();

admin_header('موردهای احتمالاً تکراری');
?>
<div class="card">
  <h2>موردهای احتمالاً تکراری</h2>
  <p class="muted">گزارش‌های یک نوع با فاصله کمتر از <?= fa_digits((string) (int) FieldDuplicates::REPORT_METRES) ?> متر و
    <?= fa_digits((string) FieldDuplicates::REPORT_DAYS) ?> روز؛ بازرسی‌های یک نوع با پلاک یکسان در
    <?= fa_digits((string) FieldDuplicates::INSPECTION_DAYS) ?> روز. برای ادغام، مورد تازه‌تر را باز کنید.</p>
  <?php if ($pairs === []): ?>
    <p class="notice">موردی که تکراری به نظر برسد پیدا نشد.</p>
  <?php else: ?>
  <table>
    <tr><th>نوع</th><th>مورد قدیمی‌تر</th><th>مورد تازه‌تر</th><th>فاصله</th><th>پلاک یا آدرس</th></tr>
    <?php foreach ($pairs as $p): ?>
      <tr>
        <td><?= e(FieldAdmin::KIND_NAMES[(int) $p['kind']] ?? '—') ?></td>
        <td dir="ltr" style="text-align:right"><a href="item.php?id=<?= e($p['a_id']) ?>"><?= e($p['a_code']) ?></a></td>
        <td dir="ltr" style="text-align:right"><a href="item.php?id=<?= e($p['b_id']) ?>"><?= e($p['b_code']) ?></a></td>
        <td><?= metres_text($p['metres']) ?></td>
        <td><?= e(mb_substr((string) ($p['plate'] ?: $p['address']), 0, 60)) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>
<?php portal_footer();
