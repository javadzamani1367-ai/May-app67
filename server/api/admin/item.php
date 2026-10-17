<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_item_view.php';
require __DIR__ . '/_item_merge.php';

$manager = admin_require(ADMIN_ROLES);
$scope = admin_scope();
$id = (string) ($_GET['id'] ?? '');
$load = static fn () => Field::isUuid($id) ? Db::one(
    'SELECT i.*, u.full_name AS reporter, u.user_code AS reporter_code, a.full_name AS expert, h.full_name AS handler
     FROM field_items i JOIN users u ON u.id = i.user_id LEFT JOIN users a ON a.id = i.assigned_to
     LEFT JOIN users h ON h.id = i.handler_id WHERE i.id = ?',
    [$id]
) : null;
$item = $load();
if ($item === null || !$scope->canSeeField($item)) {
    http_response_code(404);
    admin_header('مورد پیدا نشد');
    echo '<div class="card"><p class="error">این مورد پیدا نشد.</p><p><a href="items.php">بازگشت به فهرست</a></p></div>';
    portal_footer();
    exit;
}

$error = null;
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    csrf_check();
    $action = (string) ($_POST['action'] ?? 'status');
    $note = (string) ($_POST['note'] ?? '');
    if ($action === 'hand') {
        $error = FieldHandOff::hand($item, (string) ($_POST['executor_id'] ?? ''), $note, $scope);
    } elseif ($action === 'followup') {
        $error = FieldHandOff::followUp($item, $note, $scope);
    } elseif ($action === 'merge' || $action === 'unmerge') {
        // ادغام تکراری‌ها کار مدیر است؛ مجری فقط موردهای خودش را می‌گرداند.
        $error = !$scope->isManager() ? 'ادغام کار مدیر است.' : ($action === 'merge'
            ? FieldDuplicates::merge($item, (string) ($_POST['target'] ?? ''), $manager, $note)
            : FieldDuplicates::unmerge($item, $manager));
    } else {
        $dueText = trim((string) ($_POST['due'] ?? ''));
        $parsed = $dueText === '' ? null : Jalali::parse($dueText);
        $assignee = (string) ($_POST['assigned_to'] ?? '');
        // مهلت تا پایان همان روز است.
        $error = $dueText !== '' && $parsed === null ? 'تاریخ مهلت را مثل ۱۴۰۵/۰۷/۲۰ بنویسید.'
            : FieldAdmin::transition($item, (int) ($_POST['status'] ?? -1), $manager, $note,
                Field::isUuid($assignee) ? $assignee : null, $parsed === null ? null : Jalali::startOfDay(...$parsed) + 86_400_000 - 1);
    }
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
$experts = array_map(static fn ($x) => ['id' => $x['id'], 'full_name' => $x['name']],
    $scope->experts($scope->wholeProvince() ? null : ($item['area_code'] ?? null)));
$executors = $scope->isExecutor() ? [] : $scope->executors();
$target = $item['merged_into'] === null ? null : Db::one('SELECT id, tracking_code FROM field_items WHERE id = ?', [$item['merged_into']]);
// مورد ادغام‌شده وضعیت جدا ندارد؛ اول باید از ادغام جدا شود.
$next = $target === null ? (FieldAdmin::NEXT[(int) $item['status']] ?? []) : [];
$candidates = $scope->isManager() ? FieldDuplicates::candidatesFor($item) : [];
$merged = FieldDuplicates::mergedInto($id);
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
    <div>
      <a href="sheet.php?id=<?= e($id) ?>" target="_blank"><button type="button">برگه ارجاع (چاپ / PDF)</button></a>
      <a href="item_docx.php?id=<?= e($id) ?>"><button type="button">خروجی Word</button></a>
      <a href="items.php">بازگشت به فهرست</a>
    </div>
  </div>
  <?php if (isset($_GET['saved'])): ?><p class="notice">ثبت شد.</p><?php endif; ?>
  <table>
    <?= detail_row('ثبت‌کننده', $item['reporter'] . ' (' . ltr((string) $item['reporter_code']) . ')') ?>
    <?= detail_row('ناحیه', Areas::label($item['area_code'] ?? null)) ?>
    <?= detail_row('سپرده به مجری', $item['handler']) ?>
    <?= detail_row('اولویت', $item['priority'] === null ? null : (FieldAdmin::PRIORITY_NAMES[(int) $item['priority']] ?? null)) ?>
    <?= detail_row('زمان ثبت در گوشی', Jalali::format((int) $item['created_at'])) ?>
    <?= detail_row('زمان رسیدن به سرور', Jalali::format((int) $item['received_at'])) ?>
    <?= detail_row('ارجاع به', $item['expert'] === null ? null
        : $item['expert'] . ($item['due_at'] === null ? '' : '، مهلت ' . Jalali::format((int) $item['due_at'], false))) ?>
    <?= detail_row('نتیجه ارجاع', referral_result($item)) ?>
    <?= detail_row('آدرس', $item['address']) ?>
    <?= detail_row('پلاک', $item['plate']) ?>
    <?= detail_row('شرح', $item['description']) ?>
  </table>
  <?php if ($lat !== null && $lon !== null): ?>
    <p dir="ltr" style="text-align:right"><?= sprintf('%.6f, %.6f', $lat, $lon) ?>
      <?= $item['accuracy'] === null ? '' : '± ' . (int) round((float) $item['accuracy']) . ' m' ?>
      — <a target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=<?= $lat ?>&amp;mlon=<?= $lon ?>#map=18/<?= $lat ?>/<?= $lon ?>">OpenStreetMap</a>
      — <a target="_blank" rel="noopener" href="https://www.google.com/maps?q=<?= $lat ?>,<?= $lon ?>">Google Maps</a></p>
    <?php $audit = PositionAudit::text($item['position_at'] ?? null, $lat, $lon, $item['observer_lat'] ?? null,
        $item['observer_lon'] ?? null, $item['observer_accuracy'] ?? null); ?>
    <?php if ($audit !== null): ?><p><strong>ثبت موقعیت:</strong> <?= e($audit) ?></p><?php endif; ?>
  <?php endif; ?>
</div>

<?php $details = render_payload((int) $item['kind'], $payload); if ($details !== ''): ?>
<div class="card"><h3>جزئیات</h3><?= $details ?></div>
<?php endif; ?>
<?php if ((int) $item['kind'] === Field::KIND_LINE): ?>
<div class="card"><h3>جریان سه فاز در طول فیدر</h3>
  <?= FieldLine::svg($payload) ?>
  <p class="muted"><span style="color:#dc2626">■ R</span> <span style="color:#d97706">■ S</span>
    <span style="color:#2563eb">■ T</span> — زمینه سرخ: بار قابل توجه، کهربایی: افزایش غیرعادی</p>
  <?= render_poles($payload) ?>
</div>
<?php $subscribers = render_subscribers($payload); if ($subscribers !== ''): ?>
<div class="card"><h3>مشترکان نامتعارف</h3><?= $subscribers ?></div>
<?php endif; ?>
<?php endif; ?>

<?= render_merge($item, $target, $candidates, $merged) ?>
<?php if ($error !== null && $next === []): ?><div class="card"><p class="error"><?= e($error) ?></p></div><?php endif; ?>

<div class="card"><h3>فایل‌ها</h3><?= render_files($files, $payload) ?></div>

<?php if ($next !== []): ?>
<div class="card">
  <h3>اقدام</h3>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post">
    <?= csrf_field() ?><input type="hidden" name="action" value="status">
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

<?php if ($target === null): ?>
<div class="section-grid">
  <?php if ($executors !== [] && !in_array((int) $item['status'], [Field::STATUS_CLOSED, Field::STATUS_REJECTED], true)): ?>
  <div class="card"><h3>سپردن به مجری رمزارز</h3>
    <p class="muted">مجری مورد را به کارشناس ناحیه‌اش ارجاع می‌دهد؛ هر قدمش در تاریخچه همین مورد می‌آید.</p>
    <form method="post"><?= csrf_field() ?><input type="hidden" name="action" value="hand">
      <label>مجری<select name="executor_id" required><option value="">—</option>
        <?php foreach ($executors as $x): ?><option value="<?= e($x['id']) ?>" <?= $x['id'] === $item['handler_id'] ? 'selected' : '' ?>>
          <?= e($x['name'] . ' — ' . implode('، ', array_map([Areas::class, 'label'], $x['areas']))) ?></option><?php endforeach; ?>
      </select></label>
      <label>توضیح<input name="note"></label>
      <button type="submit" style="background:#7c3aed">سپردن</button></form></div>
  <?php endif; ?>
  <div class="card"><h3>پیگیری</h3>
    <form method="post"><?= csrf_field() ?><input type="hidden" name="action" value="followup">
      <label>یادداشت پیگیری<textarea name="note" rows="2" required></textarea></label>
      <button type="submit" style="background:#64748b">ثبت پیگیری</button></form></div>
</div>
<?php endif; ?>

<div class="card"><h3>تاریخچه</h3><?= render_events($events) ?></div>
<?php portal_footer();
