<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// سه گزارش پاداش یک بازه: عامل شناسایی، عوامل اجرایی (نیروی انتظامی و سپاه) و «گزارش پاداش
// عوامل اداری»؛ هر کدام با اکسل و Word رسمی برای مکاتبه. فقط مدیر.
$user = admin_require([Auth::ROLE_MANAGER]);
$access = CenterSettings::require($user, true);
[$from, $to] = center_range($_GET);
$rows = CenterRows::query($access, $from, $to, ($_GET['area'] ?? '') ?: null);
$link = static fn (string $kind, string $format) => 'center_export.php' . query_with($_GET, ['reward' => $kind, 'format' => $format]);

admin_header('گزارش‌های پاداش');
echo center_styles();
?>
<div class="card">
  <h2>گزارش‌های پاداش</h2>
  <?= center_range_form($_GET, null) ?>
  <p><a href="center_rules.php">دستورالعمل‌های پاداش</a> · <a href="centers.php<?= e(query_with($_GET)) ?>">آمار جامع</a></p>
</div>
<?php foreach (CenterRewardReport::KINDS as $kind => $name):
    $model = CenterRewardReport::forApp(CenterRewardReport::model($kind, $rows, $from, $to)); ?>
<div class="card">
  <div class="row"><h2><?= e($name) ?></h2>
    <div><a href="<?= e($link($kind, 'xlsx')) ?>"><button type="button" style="background:#15803d;margin:0">اکسل</button></a>
      <a href="<?= e($link($kind, 'docx')) ?>"><button type="button" style="background:#1d4ed8;margin:0">Word</button></a></div></div>
  <?php foreach ($model['paragraphs'] as $text): ?><p class="srcs"><?= e($text) ?></p><?php endforeach; ?>
  <?php foreach ($model['tables'] as $t): ?>
    <?php if ($t['caption']): ?><h3 style="margin-top:12px"><?= e($t['caption']) ?></h3><?php endif; ?>
    <?php if ($t['rows'] === []): ?><p class="muted">موردی در این بازه نیست.</p><?php continue; endif; ?>
    <div class="cscroll"><table class="ctable">
      <tr><?php foreach ($t['header'] as $h): ?><th><?= e($h) ?></th><?php endforeach; ?></tr>
      <?php foreach ($t['rows'] as $line): ?><tr><?php foreach ($line as $cell): ?><td><?= e($cell) ?></td><?php endforeach; ?></tr><?php endforeach; ?>
      <?php if ($t['total']): ?><tr><?php foreach ($t['total'] as $cell): ?><th><?= e($cell) ?></th><?php endforeach; ?></tr><?php endif; ?>
    </table></div>
  <?php endforeach; ?>
</div>
<?php endforeach;
portal_footer();
