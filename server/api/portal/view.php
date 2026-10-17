<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$user = portal_require_user();
$unit = (int) $user['unit'];
$id = (string) ($_GET['id'] ?? '');

$dispatch = Db::one(
    'SELECT d.*, r.tracking_code, r.temp_code, r.county, r.area_code AS report_area, r.district, r.address, r.owner_name,
            r.subscription_number, r.subscriber_name, r.total_watt, r.description, r.actions_taken
     FROM dispatches d JOIN reports r ON r.id = d.report_id WHERE d.id = ?',
    [$id]
);
if ($dispatch === null || !Scope::unitSees($user, $dispatch)) {
    http_response_code(404);
    portal_header('یافت نشد');
    echo '<div class="card"><p class="error">این ارسال برای واحد شما نیست.</p></div>';
    portal_footer();
    exit;
}

// باز کردن یعنی دیده شد. تاریخ اولین بازکردن نگه داشته می‌شود، نه آخرین.
Db::run(
    'UPDATE dispatches SET status = GREATEST(status, 1), seen_at = COALESCE(seen_at, ?) WHERE id = ?',
    [Db::now(), $id]
);

$stages = UnitStages::of($unit);
$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $stage = is_numeric($_POST['stage'] ?? null) ? (int) $_POST['stage'] : null;
    $result = UnitResponse::record($dispatch, $user, (string) ($_POST['body'] ?? ''), $stage, UnitResponse::uploaded());
    if (is_string($result)) {
        $error = $result;
    } else {
        header('Location: view.php?id=' . urlencode($id) . '&saved=1');
        exit;
    }
}

$responses = UnitResponse::list($dispatch);
$current = $dispatch['stage'] === null ? null : (int) $dispatch['stage'];
$items = json_decode((string) $dispatch['included_items'], true);
$files = Db::all(
    'SELECT id, file_path, caption AS title, "media" AS kind FROM media WHERE report_id = ?
     UNION ALL
     SELECT id, file_path, title, "attachment" AS kind FROM attachments WHERE report_id = ?',
    [$dispatch['report_id'], $dispatch['report_id']]
);
$selected = is_array($items) ? $items : [];

portal_header('پرونده ' . CaseLabels::code($dispatch));
?>
<div class="card">
  <h2>پرونده <?= fa_digits(e(CaseLabels::code($dispatch) ?: '—')) ?></h2>
  <table>
    <tr><th>شهرستان</th><td><?= e(CaseLabels::countyWithArea($dispatch['county'], $dispatch['report_area'])) ?></td></tr>
    <tr><th>آدرس</th><td><?= e($dispatch['address'] ?? '—') ?></td></tr>
    <tr><th>مشترک</th><td><?= e($dispatch['subscriber_name'] ?? '—') ?></td></tr>
    <tr><th>مالک یا متصرف</th><td><?= e($dispatch['owner_name'] ?? '—') ?></td></tr>
    <tr><th>شماره اشتراک</th><td><?= fa_digits(e($dispatch['subscription_number'] ?? '—')) ?></td></tr>
    <tr><th>تاریخ ارسال</th><td><?= e(jalali((int) $dispatch['dispatched_at'])) ?></td></tr>
    <tr><th>مهلت اعلام نتیجه</th>
        <td><?= $dispatch['deadline_at'] === null ? '—' : e(jalali((int) $dispatch['deadline_at'])) ?></td></tr>
  </table>
  <?php if (!empty($dispatch['note'])): ?>
    <p><strong>توضیح ارسال:</strong> <?= nl2br(e($dispatch['note'])) ?></p>
  <?php endif; ?>
</div>

<div class="card">
  <h3>مدارک این ارسال</h3>
  <?php
  $sent = array_values(array_filter($files, static fn(array $f): bool => in_array($f['id'], $selected, true)));
  if ($sent === []): ?>
    <p class="muted">فایلی همراه این ارسال ثبت نشده است.</p>
  <?php else: ?>
    <ul>
      <?php foreach ($sent as $file): ?>
        <li><a href="file.php?id=<?= e((string) $file['id']) ?>&amp;kind=<?= e((string) $file['kind']) ?>">
          <?= e($file['title'] ?: 'فایل بدون عنوان') ?></a></li>
      <?php endforeach; ?>
    </ul>
  <?php endif; ?>
</div>

<div class="card">
  <h3>پاسخ و اقدامات</h3>
  <ol class="stages">
    <?php foreach ($stages as $i => $name): ?>
      <li class="<?= $current !== null && $i <= $current ? 'done' : '' ?>"><?= e($name) ?></li>
    <?php endforeach; ?>
  </ol>
  <?php if (isset($_GET['saved'])): ?><p class="tag tag-answered">پاسخ شما ثبت شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <?php foreach ($responses as $response): ?>
    <div style="border-top:1px solid #d8dee9; padding-top:10px; margin-top:10px">
      <p class="muted"><?= e($response['responder_name'] ?? '') ?> — <?= e(jalali((int) $response['created_at'])) ?>
        <?php if ($response['stage_name'] !== null): ?> — <span class="tag tag-seen"><?= e($response['stage_name']) ?></span><?php endif; ?></p>
      <p><?= nl2br(e($response['body'] ?? '')) ?></p>
      <?php foreach ($response['files'] as $rf): ?>
        <a href="file.php?id=<?= e((string) $rf['id']) ?>&amp;kind=response"><?= e($rf['file_name']) ?></a><br>
      <?php endforeach; ?>
    </div>
  <?php endforeach; ?>

  <form method="post" enctype="multipart/form-data">
    <label>کار به کدام مرحله رسیده؟
      <select name="stage" required>
        <?php foreach ($stages as $i => $name): ?>
          <option value="<?= $i ?>" <?= $i === min(count($stages) - 1, ($current ?? -1) + 1) ? 'selected' : '' ?>><?= e($name) ?></option>
        <?php endforeach; ?>
      </select>
    </label>
    <label>شرح اقدام انجام‌شده
      <textarea name="body" rows="5" required></textarea>
    </label>
    <label>مستندات اقدام (تصویر، PDF، ورد — چند فایل قابل انتخاب است)
      <input type="file" name="files[]" multiple>
    </label>
    <button type="submit">ثبت اقدام</button>
  </form>
</div>
<?php portal_footer();
