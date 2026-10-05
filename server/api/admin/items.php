<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

$filter = FieldAdmin::filters($_GET);
$values = $filter['values'];
$perPage = 50;
$page = max(1, (int) ($_GET['page'] ?? 1));
$total = (int) (Db::one("SELECT COUNT(*) AS n FROM field_items i WHERE {$filter['where']}", $filter['params'])['n'] ?? 0);
$rows = Db::all(FieldAdmin::listSql($filter['where']) . " LIMIT $perPage OFFSET " . (($page - 1) * $perPage), $filter['params']);
$fieldUsers = Db::all('SELECT id, full_name FROM users WHERE role = ? ORDER BY full_name', [Auth::ROLE_FIELD]);
$experts = Db::all('SELECT id, full_name FROM users WHERE role = ? ORDER BY full_name', [Auth::ROLE_EXPERT]);

/** یک فهرست کشویی برای فیلتر، با «همه» در بالا. */
function filter_select(string $name, string $label, array $options, array $values): string
{
    $html = '<label>' . e($label) . '<select name="' . $name . '"><option value="">همه</option>';
    foreach ($options as $value => $text) {
        $selected = (string) ($values[$name] ?? '') === (string) $value ? ' selected' : '';
        $html .= '<option value="' . e((string) $value) . '"' . $selected . '>' . e($text) . '</option>';
    }
    return $html . '</select></label>';
}

admin_header('موردهای میدانی');
?>
<div class="card">
  <div class="row">
    <div>
      <h2>موردهای میدانی</h2>
      <p class="muted"><?= fa_digits((string) $total) ?> مورد</p>
    </div>
    <div><a href="items_export.php<?= e(query_with($values)) ?>"><button type="button">خروجی اکسل</button></a></div>
  </div>
  <form method="get">
    <div class="grid">
      <?= filter_select('kind', 'نوع', FieldAdmin::KIND_NAMES, $values) ?>
      <?= filter_select('status', 'وضعیت', FieldAdmin::STATUS_NAMES, $values) ?>
      <?= filter_select('user', 'ثبت‌کننده', array_column($fieldUsers, 'full_name', 'id'), $values) ?>
      <?= filter_select('assigned', 'ارجاع به', array_column($experts, 'full_name', 'id'), $values) ?>
      <label>از تاریخ<input name="from" placeholder="۱۴۰۵/۰۷/۰۱" value="<?= e($values['from'] ?? '') ?>"></label>
      <label>تا تاریخ<input name="to" placeholder="۱۴۰۵/۰۷/۳۰" value="<?= e($values['to'] ?? '') ?>"></label>
      <label>کد، پلاک یا آدرس<input name="q" value="<?= e($values['q'] ?? '') ?>"></label>
    </div>
    <label class="check"><input type="checkbox" name="overdue" value="1" <?= isset($values['overdue']) ? 'checked' : '' ?>>
      فقط ارجاع‌های با مهلت گذشته</label>
    <button type="submit">نمایش</button>
    <?php if ($values !== []): ?> <a href="items.php">پاک کردن فیلترها</a><?php endif; ?>
  </form>
</div>

<div class="card">
  <?php if ($rows === []): ?>
    <p class="muted">موردی با این فیلترها نیست.</p>
  <?php else: ?>
  <table>
    <tr><th>کد</th><th>نوع</th><th>ثبت‌کننده</th><th>تاریخ</th><th>وضعیت</th><th>آدرس یا پلاک</th></tr>
    <?php foreach ($rows as $row): ?>
      <tr>
        <td dir="ltr" style="text-align:right"><a href="item.php?id=<?= e($row['id']) ?>"><?= e($row['tracking_code'] ?? '—') ?></a></td>
        <td><?= e(FieldAdmin::KIND_NAMES[(int) $row['kind']] ?? '—') ?>
            <?= $row['priority'] !== null && (int) $row['priority'] === 0 ? '<span class="tag tag-overdue">فوری</span>' : '' ?></td>
        <td><?= e($row['reporter']) ?></td>
        <td><?= e(Jalali::format((int) $row['created_at'])) ?></td>
        <td><?= field_status_tag($row) ?></td>
        <td><?= e(mb_substr((string) ($row['address'] ?: $row['plate']), 0, 70)) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
  <?php if ($total > $perPage): ?>
    <p class="muted">صفحه
      <?php for ($p = 1; $p <= (int) ceil($total / $perPage); $p++): ?>
        <?php if ($p === $page): ?><strong><?= fa_digits((string) $p) ?></strong>
        <?php else: ?><a href="<?= e(query_with($values, ['page' => (string) $p])) ?>"><?= fa_digits((string) $p) ?></a><?php endif; ?>
      <?php endfor; ?>
    </p>
  <?php endif; ?>
</div>
<?php portal_footer();
