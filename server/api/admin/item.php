<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_item_view.php';

$manager = admin_require();
$id = (string) ($_GET['id'] ?? '');
$load = static fn () => Field::isUuid($id) ? Db::one(
    'SELECT i.*, u.full_name AS reporter, u.user_code AS reporter_code, a.full_name AS expert
     FROM field_items i JOIN users u ON u.id = i.user_id LEFT JOIN users a ON a.id = i.assigned_to WHERE i.id = ?',
    [$id]
) : null;
$item = $load();
if ($item === null) {
    http_response_code(404);
    admin_header('مورد پیدا نشد');
    echo '<div class="card"><p class="error">این مورد پیدا نشد.</p><p><a href="items.php">بازگشت به فهرست</a></p></div>';
    portal_footer();
    exit;
}

$error = null;
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();
    $to = (int) ($_POST['status'] ?? -1);
    $due = null;
    $dueText = trim((string) ($_POST['due'] ?? ''));
    if ($dueText !== '') {
        $parsed = Jalali::parse($dueText);
        // مهلت تا پایان همان روز است.
        $due = $parsed === null ? -1 : Jalali::startOfDay(...$parsed) + 86_400_000 - 1;
    }
    $assignee = (string) ($_POST['assigned_to'] ?? '');
    $error = $due === -1 ? 'تاریخ مهلت را مثل ۱۴۰۵/۰۷/۲۰ بنویسید.'
        : FieldAdmin::transition($item, $to, $manager, (string) ($_POST['note'] ?? ''),
            Field::isUuid($assignee) ? $assignee : null, $due);
    if ($error === null) {
        header('Location: item.php?id=' . urlencode($id) . '&saved=1');
        exit;
    }
}

$payload = json_decode((string) $item['payload'], true);
$payload = is_array($payload) ? $payload : [];
$files = Db::all('SELECT * FROM field_files WHERE item_id = ? ORDER BY role, captured_at', [$id]);
$events = Db::all(
    'SELECT e.*, u.full_name AS who FROM field_events e LEFT JOIN users u ON u.id = e.user_id
     WHERE e.item_id = ? ORDER BY e.at, e.id',
    [$id]
);
$experts = Db::all('SELECT id, full_name FROM users WHERE role = ? AND active = 1 ORDER BY full_name', [Auth::ROLE_EXPERT]);
$next = FieldAdmin::NEXT[(int) $item['status']] ?? [];
$lat = $item['latitude'] === null ? null : (float) $item['latitude'];
$lon = $item['longitude'] === null ? null : (float) $item['longitude'];

admin_header((string) ($item['tracking_code'] ?? 'مورد'));
?>
<div class="card">
  <div class="row">
    <div>
      <h2 dir="ltr" style="text-align:right"><?= e($item['tracking_code'] ?? '—') ?></h2>
      <p><?= e(FieldAdmin::KIND_NAMES[(int) $item['kind']] ?? '—') ?> — <?= field_status_tag($item) ?></p>
    </div>
    <div><a href="items.php">بازگشت به فهرست</a></div>
  </div>
  <?php if (isset($_GET['saved'])): ?><p class="notice">ثبت شد.</p><?php endif; ?>
  <table>
    <?= detail_row('ثبت‌کننده', $item['reporter'] . ' (' . $item['reporter_code'] . ')') ?>
    <?= detail_row('اولویت', $item['priority'] === null ? null : (FieldAdmin::PRIORITY_NAMES[(int) $item['priority']] ?? null)) ?>
    <?= detail_row('زمان ثبت در گوشی', Jalali::format((int) $item['created_at'])) ?>
    <?= detail_row('زمان رسیدن به سرور', Jalali::format((int) $item['received_at'])) ?>
    <?= detail_row('ارجاع به', $item['expert'] === null ? null
        : $item['expert'] . ($item['due_at'] === null ? '' : '، مهلت ' . Jalali::format((int) $item['due_at'], false))) ?>
    <?= detail_row('آدرس', $item['address']) ?>
    <?= detail_row('پلاک', $item['plate']) ?>
    <?= detail_row('شرح', $item['description']) ?>
  </table>
  <?php if ($lat !== null && $lon !== null): ?>
    <p dir="ltr" style="text-align:right"><?= sprintf('%.6f, %.6f', $lat, $lon) ?>
      <?= $item['accuracy'] === null ? '' : '± ' . (int) round((float) $item['accuracy']) . ' m' ?>
      — <a target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=<?= $lat ?>&amp;mlon=<?= $lon ?>#map=18/<?= $lat ?>/<?= $lon ?>">OpenStreetMap</a>
      — <a target="_blank" rel="noopener" href="https://www.google.com/maps?q=<?= $lat ?>,<?= $lon ?>">Google Maps</a></p>
  <?php endif; ?>
</div>

<?php $details = render_payload((int) $item['kind'], $payload); if ($details !== ''): ?>
<div class="card"><h3>جزئیات</h3><?= $details ?></div>
<?php endif; ?>

<div class="card"><h3>فایل‌ها</h3><?= render_files($files, $payload) ?></div>

<?php if ($next !== []): ?>
<div class="card">
  <h3>اقدام</h3>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post">
    <?= csrf_field() ?>
    <div class="grid">
      <label>وضعیت تازه
        <select name="status">
          <?php foreach ($next as $status): ?>
            <option value="<?= $status ?>"><?= e($status === (int) $item['status'] ? 'ارجاع به کارشناس دیگر' : FieldAdmin::STATUS_NAMES[$status]) ?></option>
          <?php endforeach; ?>
        </select>
      </label>
      <label>ارجاع به کارشناس (برای «ارجاع‌شده»)
        <select name="assigned_to">
          <option value="">—</option>
          <?php foreach ($experts as $expert): ?>
            <option value="<?= e($expert['id']) ?>" <?= $expert['id'] === $item['assigned_to'] ? 'selected' : '' ?>><?= e($expert['full_name']) ?></option>
          <?php endforeach; ?>
        </select>
      </label>
      <label>مهلت نتیجه (اختیاری)<input name="due" placeholder="۱۴۰۵/۰۷/۲۰"></label>
    </div>
    <label>توضیح (برای رد و بازدید دوباره اجباری)<textarea name="note" rows="3"></textarea></label>
    <button type="submit">ثبت</button>
  </form>
</div>
<?php endif; ?>

<div class="card"><h3>تاریخچه</h3><?= render_events($events) ?></div>
<?php portal_footer();
